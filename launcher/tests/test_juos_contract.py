"""Offline integration contract checks; not a replacement for device testing."""
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

MAIN = Path(__file__).resolve().parents[1] / 'app/src/main'
A = '{http://schemas.android.com/apk/res/android}'

class JuosContractTest(unittest.TestCase):
    def test_minimal_service_registration(self):
        root = ET.parse(MAIN / 'AndroidManifest.xml').getroot()
        services = root.findall("application/service")
        matches = [s for s in services if s.get(A+'name') == '.JuosHomeRedirectService']
        self.assertEqual(1, len(matches))
        service = matches[0]
        self.assertEqual('android.permission.BIND_ACCESSIBILITY_SERVICE', service.get(A+'permission'))
        metadata = service.find('meta-data')
        self.assertIsNotNone(metadata)
        assert metadata is not None
        self.assertEqual('@xml/juos_home_redirect_service', metadata.get(A+'resource'))
        config = ET.parse(MAIN / 'res/xml/juos_home_redirect_service.xml').getroot()
        self.assertEqual('typeWindowStateChanged', config.get(A+'accessibilityEventTypes'))
        self.assertEqual('false', config.get(A+'canRetrieveWindowContent'))
        self.assertEqual('com.keylab.speech.view.vidaa', config.get(A+'packageNames'))
        self.assertIsNone(config.get(A+'accessibilityFlags'))
        self.assertIsNone(config.get(A+'canRequestFilterKeyEvents'))

    def test_service_fails_closed_and_never_reads_nodes(self):
        source = MAIN / 'java/com/gathin/tvhome/JuosHomeRedirectService.java'
        self.assertTrue(source.exists())
        text = source.read_text()
        for required in ['event == null', 'TYPE_WINDOW_STATE_CHANGED', 'isKeyguardLocked()',
                         'isDeviceLocked()', 'SystemClock.elapsedRealtime()',
                         'getBoolean(ENABLED, false)', 'MainActivity.class', 'policy.isTripped()',
                         'putBoolean(ENABLED, false)', 'ActivityNotFoundException | SecurityException']:
            self.assertIn(required, text)
        for forbidden in ['getSource(', 'getText(', 'getRootInActiveWindow(', 'Settings.Secure',
                          'performGlobalAction(', 'java.net', 'android.net']:
            self.assertNotIn(forbidden, text)

    def test_settings_offer_opt_in_and_user_authorization(self):
        text = (MAIN / 'java/com/gathin/tvhome/LauncherSettingsActivity.java').read_text()
        self.assertIn('JuosHomeRedirectService.ENABLED', text)
        # AccessibilitySettingsRoute now owns the standard action and JUOS fallback; verify wiring
        # and the actual action instead of requiring an obsolete inline constant in the Activity.
        self.assertIn('AccessibilitySettingsRoute.candidates()', text)
        self.assertIn('if (tryStart(intent)) return;', text)
        route = (MAIN / 'java/com/gathin/tvhome/AccessibilitySettingsRoute.java').read_text()
        self.assertIn('android.settings.ACCESSIBILITY_SETTINGS', route)
        self.assertIn('com.android.tv.settings.system.AccessibilityActivity', route)
        self.assertIn('protected void onResume()', text)
        layout = ET.parse(MAIN / 'res/layout/activity_launcher_settings.xml').getroot()
        ids = {n.get(A+'id') for n in layout.iter()}
        self.assertIn('@+id/juosRedirectChoice', ids)
        self.assertIn('@+id/juosAccessibilityChoice', ids)
        for locale in ['values', 'values-zh-rCN']:
            resources = ET.parse(MAIN / f'res/{locale}/juos_strings.xml').getroot()
            names = {n.get('name') for n in resources}
            self.assertTrue({'juos_redirect_name', 'juos_redirect_help', 'juos_redirect_state',
                             'juos_accessibility_settings', 'juos_redirect_stopped'} <= names)

if __name__ == '__main__':
    unittest.main(verbosity=2)
