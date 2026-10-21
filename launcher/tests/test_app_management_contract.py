"""Local source wiring contracts for App management; not remote/device E2E tests."""
import re
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

MAIN = Path(__file__).resolve().parents[1] / 'app/src/main'
JAVA = MAIN / 'java/com/gathin/tvhome'
TEST = Path(__file__).resolve().parents[1] / 'app/src/test/java/com/gathin/tvhome'
A = '{http://schemas.android.com/apk/res/android}'


def strings(locale):
    result = {}
    for path in (MAIN / f'res/{locale}').glob('*.xml'):
        root = ET.parse(path).getroot()
        for node in root.findall('string'):
            result[node.get('name')] = ''.join(node.itertext())
    return result


class AppManagementContractTest(unittest.TestCase):
    def test_settings_menu_keeps_upstream_rows_and_app_management(self):
        main = (JAVA / 'MainActivity.java').read_text()
        body = main[main.index('private void showSettingsMenu()'):main.index('private void addSettingsMenuRow(')]
        rows = re.findall(r'addSettingsMenuRow\(rows, popup, R\.string\.(\w+)', body)
        # Upstream adds dock editing before reorder; retain its row as well as Vidda app management.
        self.assertEqual(['launcher_settings', 'module_cards', 'dock_edit', 'reorder', 'app_management'], rows)
        self.assertIn('this::showAppManagement', body)

    def test_localized_strings(self):
        self.assertEqual('App management', strings('values')['app_management'])
        self.assertEqual('应用管理', strings('values-zh-rCN')['app_management'])
        for locale in ['values', 'values-zh-rCN']:
            names = strings(locale)
            for key in ['app_management', 'app_hidden_by_theme', 'app_management_save_failed',
                        'app_management_theme_locked', 'no_apps_to_manage']:
                self.assertIn(key, names, (locale, key))

    def test_row_layout_has_icon_name_and_non_focusable_switch(self):
        root = ET.parse(MAIN / 'res/layout/item_app_toggle.xml').getroot()
        self.assertEqual('true', root.get(A + 'focusable'))
        ids = {n.get(A + 'id'): n for n in root.iter()}
        for view_id in ['@+id/icon', '@+id/title', '@+id/note', '@+id/toggle']:
            self.assertIn(view_id, ids)
        toggle = ids['@+id/toggle']
        # Shared custom switch (2.3-vidda-experimental.4) replaces the platform Switch.
        self.assertEqual('com.gathin.tvhome.PillSwitchView', toggle.tag)
        self.assertEqual('false', toggle.get(A + 'focusable'))
        self.assertEqual('false', toggle.get(A + 'clickable'))

    def test_desktop_uses_one_state_source_for_both_categories(self):
        adapter = (JAVA / 'AppAdapter.java').read_text()
        reload = adapter[adapter.index('void reload('):adapter.index('Collections.sort(fresh')]
        self.assertIn('Intent.CATEGORY_LAUNCHER', reload)
        self.assertNotIn('if (!added.isEmpty())', reload)  # launcher-only apps are always considered
        self.assertIn('DesktopApps.shown(', reload)
        self.assertNotIn('HiddenApps', adapter)
        dialog = (JAVA / 'AppManagementDialog.java').read_text()
        for text in ['Intent.CATEGORY_LEANBACK_LAUNCHER', 'Intent.CATEGORY_LAUNCHER', 'DesktopApps.rows(',
                     'DesktopApps.toggle(', 'LibraryApps.saveChoices(', 'LibraryApps.loadChoices(']:
            self.assertIn(text, dialog)
        for forbidden in ['setApplicationEnabledSetting', 'deletePackage', 'ACTION_DELETE',
                          'clearApplicationUserData', 'killBackgroundProcesses', 'forceStopPackage']:
            self.assertNotIn(forbidden, dialog)

    def test_legacy_hidden_data_is_migrated_before_first_load(self):
        library = (JAVA / 'LibraryApps.java').read_text()
        self.assertIn('"user_hidden_apps"', library)
        self.assertIn('DesktopApps.migrate(', library)
        main = (JAVA / 'MainActivity.java').read_text()
        on_create = main[main.index('protected void onCreate('):main.index('adapter = new AppAdapter(')]
        self.assertIn('LibraryApps.migrateLegacyHidden(this)', on_create)

    def test_old_show_hide_entry_is_removed(self):
        for name in ['HiddenApps.java', 'HiddenAppsDialog.java', 'HiddenAppsStore.java']:
            self.assertFalse((JAVA / name).exists(), name)
        self.assertFalse((TEST / 'HiddenAppsTest.java').exists())
        for locale in ['values', 'values-zh-rCN']:
            self.assertFalse((MAIN / f'res/{locale}/hidden_apps_strings.xml').exists())
        layout = (MAIN / 'res/layout/activity_launcher_settings.xml').read_text()
        self.assertNotIn('hiddenAppsChoice', layout)
        self.assertNotIn('HiddenAppsDialog', (JAVA / 'LauncherSettingsActivity.java').read_text())
        # JUOS redirect from the previous round stays.
        self.assertIn('juosRedirectChoice', layout)


if __name__ == '__main__':
    unittest.main(verbosity=2)
