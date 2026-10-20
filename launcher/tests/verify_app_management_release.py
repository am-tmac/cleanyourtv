"""Verify/copy the local App management release; never contacts a device or runs adb."""
from pathlib import Path
import collections, hashlib, json, os, re, shutil, subprocess, zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ['ANDROID_HOME']) / 'build-tools/34.0.0'
DEST = Path.home() / 'Documents/Vidda-C3S-optimization/cleanyourtv-vidda'
APK = DEST / 'CleanYourTV-2.3-vidda-experimental.3-app-management-release.apk'
OLD = DEST / 'CleanYourTV-2.3-vidda-experimental.2-hide-apps-release.apk'
assert OLD.exists(), 'previous delivery must be kept'
assert not APK.exists(), 'Keep existing delivery unchanged; choose a new filename'
shutil.copy2(ROOT / 'app/build/outputs/apk/release/app-release.apk', APK)

def run(tool, *args):
    return subprocess.check_output([str(SDK / tool), *map(str, args)], text=True)

badging = run('aapt2', 'dump', 'badging', APK)
assert "name='com.gathin.tvhome' versionCode='6' versionName='2.3-vidda-experimental.3'" in badging, badging[:200]
signature = run('apksigner', 'verify', '--verbose', '--print-certs', APK)
cert = re.search(r'certificate SHA-256 digest: (\w+)', signature).group(1)
old_cert = re.search(r'certificate SHA-256 digest: (\w+)', run('apksigner', 'verify', '--print-certs', OLD)).group(1)
assert cert == old_cert, 'signer changed: upgrade would not keep data'
run('zipalign', '-c', '4', APK)
resources = run('aapt2', 'dump', 'resources', APK)
for marker in ['"App management"', '"应用管理"', '"由主题隐藏（hidden_apps）"', '"Hidden by theme (hidden_apps)"']:
    assert marker in resources, marker
for gone in ['Show / hide apps', '显示 / 隐藏应用', 'hiddenAppsChoice', 'hidden_apps_title']:
    assert gone not in resources, gone
# The first three menu strings stay as they were.
for marker in ['"简洁桌面设置"', '"调整应用顺序"', '"Module cards"', '"Launcher settings"', '"Reorder apps"']:
    assert marker in resources, marker

def resource_path(name):
    m = re.search(r'resource [^\n]+ ' + re.escape(name) + r'\n\s+\(\) \(file\) (\S+)', resources)
    assert m, name
    return m.group(1)

row = run('aapt2', 'dump', 'xmltree', APK, '--file', resource_path('layout/item_app_toggle'))
assert 'E: Switch' in row and 'E: ImageView' in row, row
manifest = run('aapt2', 'dump', 'xmltree', APK, '--file', 'AndroidManifest.xml')
assert 'com.gathin.tvhome.JuosHomeRedirectService' in manifest
with zipfile.ZipFile(APK) as archive:
    assert archive.testzip() is None
    dex = b''.join(archive.read(n) for n in archive.namelist() if n.endswith('.dex'))
for needle in [b'user_hidden_apps', b'library_home_removed.txt', b'library_apps.txt']:
    assert needle in dex, needle
for forbidden in [b'setApplicationEnabledSetting', b'clearApplicationUserData', b'killBackgroundProcesses']:
    assert forbidden not in dex, forbidden

counts = {}
for variant in ['Debug', 'Release']:
    suites = [ET.parse(p).getroot() for p in (ROOT / f'app/build/test-results/test{variant}UnitTest').glob('TEST-*.xml')]
    counts[variant] = {k: sum(int(s.get(k, 0)) for s in suites) for k in ['tests', 'failures', 'errors', 'skipped']}
    assert counts[variant]['failures'] == counts[variant]['errors'] == 0
issues = ET.parse(ROOT / 'app/build/reports/lint-results-debug.xml').getroot().findall('issue')
lint = dict(collections.Counter(i.get('severity') for i in issues))
assert not lint.get('Error') and not lint.get('Fatal')
summary = dict(apk=str(APK), bytes=APK.stat().st_size, sha256=hashlib.sha256(APK.read_bytes()).hexdigest(),
               signer_sha256=cert, same_signer_as_previous=True, tests=counts, lint=lint, device_tested=False)
for name, content in [('verification.json', json.dumps(summary, indent=2, ensure_ascii=False)),
                      ('badging.txt', badging), ('signature.txt', signature), ('row-layout.txt', row),
                      ('manifest.txt', manifest)]:
    (DEST / ('app-management-' + name)).write_text(content)
shutil.copy2(ROOT / 'app/build/reports/lint-results-debug.html', DEST / 'app-management-lint-results.html')
shutil.copy2(Path.home() / '.hermes/cache/scratch/vidda-appmgmt-release-build.log', DEST / 'app-management-build.log')
print(json.dumps(summary, indent=2, ensure_ascii=False))
