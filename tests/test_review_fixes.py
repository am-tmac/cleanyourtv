"""Device-free checks for the fixes from the external code review of the tools/ scripts.

Each case runs the real script against a stand-in: a stub adb_shell (so no TV and no adb_shell install
is needed), or a fake tools/tv.py whose shell runs commands in a local temporary directory.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import stat
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]

# Enough of adb_shell for `import tv`. Connecting fails loudly: a usage error must stop before it.
STUB_DEVICE = (
    "class AdbDeviceTcp:\n"
    "    def __init__(self, *a, **k):\n"
    "        pass\n"
    "    def connect(self, *a, **k):\n"
    "        raise RuntimeError('stub: tried to connect to the TV')\n"
)
STUB_SIGNER = "class PythonRSASigner:\n    def __init__(self, *a, **k):\n        pass\n"
STUB_KEYGEN = (
    "def keygen(filepath):\n"
    "    with open(filepath, 'wb') as f:\n"
    "        f.write(b'stub private\\n')\n"
    "    with open(filepath + '.pub', 'wb') as f:\n"
    "        f.write(b'stub pub\\n')\n"
)


def make_stub(base):
    stub = base / "stub" / "adb_shell"
    (stub / "auth").mkdir(parents=True)
    (stub / "__init__.py").write_text("")
    (stub / "adb_device.py").write_text(STUB_DEVICE)
    (stub / "auth" / "__init__.py").write_text("")
    (stub / "auth" / "keygen.py").write_text(STUB_KEYGEN)
    (stub / "auth" / "sign_pythonrsa.py").write_text(STUB_SIGNER)
    return base / "stub"


class TempDirTest(unittest.TestCase):
    def setUp(self):
        self.base = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.base, ignore_errors=True)


class TvPyArgumentsTest(TempDirTest):
    """L2 (TV_PORT) and L6 (--push/--pull argument count) in tools/tv.py."""

    def setUp(self):
        super().setUp()
        self.tools = self.base / "tools"
        self.tools.mkdir()
        shutil.copy2(ROOT / "tools" / "tv.py", self.tools / "tv.py")
        self.stub = make_stub(self.base)
        self.home = self.base / "home"
        self.home.mkdir()

    def run_tv(self, *args, **env):
        full = dict(os.environ, TV_HOST="127.0.0.1", HOME=str(self.home), PYTHONPATH=str(self.stub))
        full.pop("TV_PORT", None)
        full.update(env)
        return subprocess.run([sys.executable, str(self.tools / "tv.py"), *args], env=full,
                              capture_output=True, text=True, timeout=60)

    def test_bad_tv_port_is_a_message_not_a_traceback(self):
        for port in ("abc", "0", "65536", "70000", "-1", ""):
            with self.subTest(port=port):
                result = self.run_tv("--probe", TV_PORT=port)
                self.assertNotEqual(result.returncode, 0)
                self.assertNotIn("Traceback", result.stderr)
                self.assertIn("TV_PORT", result.stderr)
                self.assertNotIn("tried to connect", result.stderr)

    def test_good_tv_port_is_accepted(self):
        # Gets as far as connecting (the stub refuses), so the port itself passed validation.
        result = self.run_tv("--probe", TV_PORT="5555")
        self.assertIn("tried to connect", result.stderr)

    def test_transfer_usage_precedes_host_configuration(self):
        for option in ("--push", "--pull"):
            result = self.run_tv(option, TV_HOST="")
            self.assertEqual(result.returncode, 2, result.stderr)
            self.assertIn("usage", result.stderr)
            self.assertNotIn("tried to connect", result.stderr)

    def test_push_and_pull_with_wrong_argument_count_print_usage(self):
        for args in (["--push"], ["--push", "a"], ["--push", "a", "b", "c"],
                     ["--pull"], ["--pull", "a"], ["--pull", "a", "b", "c"]):
            with self.subTest(args=args):
                result = self.run_tv(*args)
                self.assertEqual(result.returncode, 2, result.stderr)
                self.assertIn("usage", result.stderr.lower())
                self.assertNotIn("tried to connect", result.stderr,
                                 "a malformed --push/--pull must not run as a shell command")


class AdbKeyLocationTest(TempDirTest):
    """O2: a new ADB key is generated outside the repository, in ~/.cleanyourtv/adbkey/."""

    def setUp(self):
        super().setUp()
        self.tools = self.base / "repo" / "tools"
        self.tools.mkdir(parents=True)
        shutil.copy2(ROOT / "tools" / "tv.py", self.tools / "tv.py")
        self.stub = make_stub(self.base)
        self.home = self.base / "home"
        self.home.mkdir()

    def signer(self):
        env = dict(os.environ, TV_HOST="127.0.0.1", HOME=str(self.home),
                   PYTHONPATH=os.pathsep.join([str(self.stub), str(self.tools)]))
        env.pop("TV_PORT", None)
        return subprocess.run([sys.executable, "-c", "import tv; tv.get_signer(); print('KEY=' + tv.KEY)"],
                              cwd=self.base, env=env, capture_output=True, text=True, timeout=60)

    def test_new_key_lives_in_home_not_in_the_repo(self):
        result = self.signer()
        self.assertEqual(result.returncode, 0, result.stderr)
        key = self.home / ".cleanyourtv" / "adbkey" / "adbkey"
        self.assertTrue(key.exists(), result.stdout + result.stderr)
        self.assertFalse((self.tools / "adbkey").exists(), "no key material inside the checkout")
        self.assertEqual(stat.S_IMODE(key.stat().st_mode) & 0o077, 0)
        self.assertIn(str(key), result.stderr, "say once where the new key lives")
        # The second run reuses it and says nothing.
        again = self.signer()
        self.assertNotIn("generated", again.stderr)

    def test_existing_repo_key_is_still_used(self):
        legacy = self.tools / "adbkey"
        legacy.mkdir()
        (legacy / "adbkey").write_text("old private\n")
        (legacy / "adbkey.pub").write_text("old pub\n")
        result = self.signer()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(f"KEY={legacy / 'adbkey'}", result.stdout)
        self.assertFalse((self.home / ".cleanyourtv" / "adbkey" / "adbkey").exists())

    def test_stock_adb_key_is_still_reused(self):
        android = self.home / ".android"
        android.mkdir()
        (android / "adbkey").write_text("stock\n")
        (android / "adbkey.pub").write_text("stock pub\n")
        result = self.signer()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(f"KEY={android / 'adbkey'}", result.stdout)


class AdbClosedPortTest(TempDirTest):
    """L1: a bad TV_PORT from the environment is reported, not an IndexError."""

    def run_check(self, *args, **env):
        full = dict(os.environ)
        full.pop("TV_PORT", None)
        full.update(env)
        return subprocess.run([sys.executable, "-I", str(ROOT / "tools" / "adb-closed.py"), *args],
                              env=full, capture_output=True, text=True, timeout=30)

    def test_bad_port_from_environment(self):
        result = self.run_check("127.0.0.1", TV_PORT="abc")
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertIn("'abc'", result.stderr)

    def test_out_of_range_ports(self):
        for port in ("0", "65536", "-5"):
            with self.subTest(port=port):
                result = self.run_check("127.0.0.1", port)
                self.assertEqual(result.returncode, 2, result.stderr)
                self.assertNotIn("Traceback", result.stderr)
                result = self.run_check("127.0.0.1", TV_PORT=port)
                self.assertEqual(result.returncode, 2, result.stderr)
                self.assertNotIn("Traceback", result.stderr)


# A tools/tv.py whose "TV shell" is /bin/sh on this machine, so b64push.py's real commands run.
FAKE_SHELL_TV = r'''
import json
import os
import subprocess

LOG = os.environ["FAKE_LOG"]


class Device:
    max_chunk_size = int(os.environ.get("FAKE_MAX", "4096"))

    def shell(self, command, read_timeout_s=None):
        with open(LOG, "a") as f:
            f.write(json.dumps(command) + "\n")
        # Inject transport exceptions after staging exists, to verify cleanup on every failure phase.
        if os.environ.get("FAKE_RAISE") and command.startswith(os.environ["FAKE_RAISE"]):
            raise OSError("simulated transport failure")
        if os.environ.get("FAKE_CORRUPT") and command.startswith("echo '"):
            command = "echo 'QUJD' >>" + command.split(">>", 1)[1]
        return subprocess.run(["/bin/sh", "-c", command], capture_output=True, text=True).stdout

    def close(self):
        pass


def connect():
    if os.environ.get("FAKE_NO_CONNECT"):
        raise RuntimeError("fake: connect() was called")
    return Device()
'''


@unittest.skipUnless(shutil.which("md5sum") and shutil.which("base64"), "needs md5sum and base64")
class B64PushTest(TempDirTest):
    """M4 (safe replace of the remote file) and L3 (usage) in tools/b64push.py."""

    def setUp(self):
        super().setUp()
        self.tools = self.base / "tools"
        self.tools.mkdir()
        shutil.copy2(ROOT / "tools" / "b64push.py", self.tools / "b64push.py")
        (self.tools / "tv.py").write_text(FAKE_SHELL_TV)
        self.remote_dir = self.base / "remote"
        self.remote_dir.mkdir()
        self.log = self.base / "log.jsonl"

    def push(self, *args, **env):
        full = {k: v for k, v in os.environ.items() if k != "TV_HOST"}
        full.update(FAKE_LOG=str(self.log), **env)
        return subprocess.run([sys.executable, str(self.tools / "b64push.py"), *args], env=full,
                              capture_output=True, text=True, timeout=120)

    def commands(self):
        return [json.loads(line) for line in self.log.read_text().splitlines()] if self.log.exists() else []

    def local(self, data):
        path = self.base / "local.bin"
        path.write_bytes(data)
        return path

    def test_missing_arguments_print_usage_without_tv_host(self):
        for args in ([], ["only-one"], ["a", "b", "c"]):
            with self.subTest(args=args):
                result = self.push(*args, FAKE_NO_CONNECT="1")
                self.assertEqual(result.returncode, 2, result.stderr)
                self.assertIn("usage", result.stderr.lower())
                self.assertNotIn("Traceback", result.stderr)

    def test_unsafe_remote_path_is_refused_before_connecting(self):
        src = self.local(b"x")
        for remote in ("relative/path", "/data/local/tmp/a b", "/data/local/tmp/$(id)",
                       "/data/local/tmp/a;rm -rf /", "/data/local/../tmp/x", "/data/'q'", ""):
            with self.subTest(remote=remote):
                result = self.push(str(src), remote, FAKE_NO_CONNECT="1")
                self.assertEqual(result.returncode, 2, result.stderr)
                self.assertNotIn("connect() was called", result.stderr)
                self.assertEqual(self.commands(), [])

    def test_transfer_replaces_the_target_only_after_the_checksum_matches(self):
        data = os.urandom(10000)   # several chunks at a 4096-byte payload
        target = self.remote_dir / "file.apk"
        target.write_bytes(b"old contents")
        result = self.push(str(self.local(data)), str(target))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(target.read_bytes(), data)
        self.assertEqual(sorted(p.name for p in self.remote_dir.iterdir()), ["file.apk"],
                         "temporary files are cleaned up")
        first = self.commands()[0]
        self.assertNotIn(f"rm -f '{target}' ", first + " ", "the target must not be deleted up front")
        for command in self.commands():
            self.assertIn(f"'{target}", command, "every remote path is single-quoted")

    def test_failed_checksum_leaves_the_original_intact(self):
        target = self.remote_dir / "file.apk"
        target.write_bytes(b"old contents")
        result = self.push(str(self.local(os.urandom(5000))), str(target), FAKE_CORRUPT="1")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("MISMATCH", result.stdout)
        self.assertEqual(target.read_bytes(), b"old contents")
        self.assertEqual(sorted(p.name for p in self.remote_dir.iterdir()), ["file.apk"])

    def test_transport_exceptions_remove_staging_and_keep_original(self):
        for phase in ("echo '", "base64 -d", "mv -f"):
            with self.subTest(phase=phase):
                target = self.remote_dir / "file.apk"
                target.write_bytes(b"old contents")
                result = self.push(str(self.local(b"new contents")), str(target), FAKE_RAISE=phase)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(target.read_bytes(), b"old contents")
                self.assertEqual(sorted(p.name for p in self.remote_dir.iterdir()), ["file.apk"])

    def test_dot_dot_anywhere_is_refused(self):
        result = self.push(str(self.local(b"x")), str(self.remote_dir / "a..b"), FAKE_NO_CONNECT="1")
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertNotIn("connect() was called", result.stderr)

    def test_empty_file(self):
        target = self.remote_dir / "empty.txt"
        target.write_bytes(b"not empty yet")
        result = self.push(str(self.local(b"")), str(target))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(target.read_bytes(), b"")
        self.assertIn(hashlib.md5(b"").hexdigest(), result.stdout)
        self.assertEqual(sorted(p.name for p in self.remote_dir.iterdir()), ["empty.txt"])


class ListParsingParityTest(TempDirTest):
    """L4 and L5: disable.sh and recovery.py read a list line the same way."""

    def setUp(self):
        super().setUp()
        self.tools = self.base / "repo" / "tools"
        self.tools.mkdir(parents=True)
        shutil.copy2(ROOT / "tools" / "disable.sh", self.tools / "disable.sh")
        self.capture = self.base / "batch.txt"
        client = self.tools / "venv" / "bin" / "python"
        client.parent.mkdir(parents=True)
        # recovery --save is a no-op here; every tv.py call reads stdin into the capture, so the
        # last capture is the batch disable.sh sent.
        client.write_text(
            '#!/bin/sh\n'
            'if [ "$1" = "-I" ] && [ "$2" = "tools/recovery.py" ]; then exit 0; fi\n'
            'cat > "$CAPTURE"\n'
        )
        client.chmod(0o755)
        self.home = self.base / "home"
        self.home.mkdir()

    def disable_sh(self, text):
        path = self.base / "debloat.txt"
        path.write_text(text)
        env = {k: v for k, v in os.environ.items() if k != "TV_HOST"}
        env.update(CAPTURE=str(self.capture), HOME=str(self.home))
        result = subprocess.run(["bash", str(self.tools / "disable.sh"), str(path)], env=env,
                                capture_output=True, text=True, stdin=subprocess.DEVNULL, timeout=60)
        sent = []
        if result.returncode == 0:
            sent = [line.rsplit(" ", 1)[1] for line in self.capture.read_text().splitlines()
                    if line.startswith("pm disable-user")]
        return result, sent

    def recovery(self, text):
        path = self.base / "debloat.txt"
        path.write_text(text)
        result = subprocess.run(
            [sys.executable, "-I", "-c",
             "import sys; sys.path.insert(0, sys.argv[1]); import recovery; "
             "print('\\n'.join(recovery.read_list(sys.argv[2])))",
             str(ROOT / "tools"), str(path)],
            capture_output=True, text=True, timeout=60)
        return result, result.stdout.split() if result.returncode == 0 else []

    def test_hash_without_a_space_is_a_comment_in_both(self):
        text = "# TV\ncom.example.foo#note\ncom.example.bar  # spaced note\n"
        sh, sh_names = self.disable_sh(text)
        py, py_names = self.recovery(text)
        self.assertEqual(sh.returncode, 0, sh.stderr)
        self.assertEqual(py.returncode, 0, py.stderr)
        self.assertEqual(sh_names, ["com.example.foo", "com.example.bar"])
        self.assertEqual(py_names, sh_names)

    def test_two_names_on_one_line_are_refused_by_both(self):
        text = "com.example.one com.example.two\n"
        sh, sh_names = self.disable_sh(text)
        py, _ = self.recovery(text)
        self.assertNotEqual(sh.returncode, 0, "disable.sh dropped the second name silently")
        self.assertNotEqual(py.returncode, 0, "recovery.py dropped the second name silently")
        self.assertIn("debloat.txt:1", py.stderr)
        self.assertIn("com.example.two", sh.stderr)
        self.assertFalse(self.capture.exists() and "disable-user" in self.capture.read_text())

    def test_every_example_list_still_parses(self):
        sys.path.insert(0, str(ROOT / "tools"))
        try:
            import recovery
        finally:
            sys.path.pop(0)
        total = 0
        lists = sorted((ROOT / "examples").glob("*/*.txt"))
        self.assertGreater(len(lists), 100)
        for path in lists:
            with self.subTest(list=path.parent.name):
                total += len(recovery.read_list(path))
        self.assertGreater(total, 2000)


if __name__ == "__main__":
    unittest.main()
