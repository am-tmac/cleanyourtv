"""Device-free regression checks using isolated transport stubs."""
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

class TempDirTest(unittest.TestCase):
    def setUp(self):
        self.base = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.base, ignore_errors=True)


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

