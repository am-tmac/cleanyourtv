"""Local source/resource wiring contracts for per-app Crop to fill and the shared switch.
Not device/remote E2E tests: nothing here touches a projector."""
import re
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'app/src/main'
JAVA = MAIN / 'java/com/gathin/tvhome'
A = '{http://schemas.android.com/apk/res/android}'


def strings(locale):
    result = {}
    for path in (MAIN / f'res/{locale}').glob('*.xml'):
        root = ET.parse(path).getroot()
        for node in root.findall('string'):
            result[node.get('name')] = ''.join(node.itertext())
    return result


def block(text, start, end):
    i = text.index(start)
    return text[i:text.index(end, i + len(start))]


class TileOptionsContractTest(unittest.TestCase):
    def test_version_is_experimental_4_code_7(self):
        gradle = (ROOT / 'app/build.gradle').read_text()
        self.assertIn('versionCode 7', gradle)
        self.assertIn("versionName '2.3-vidda-experimental.4'", gradle)

    def test_long_press_on_any_tile_opens_options_not_app_details(self):
        adapter = (JAVA / 'AppAdapter.java').read_text()
        bind = block(adapter, 'public void onBindViewHolder(', 'private void launch(')
        self.assertIn('setOnLongClickListener', bind)
        self.assertIn('listener.onTileOptions(', bind)
        self.assertNotIn('ACTION_APPLICATION_DETAILS_SETTINGS', bind)
        self.assertIn('void onTileOptions(', adapter)   # TileListener
        main = (JAVA / 'MainActivity.java').read_text()
        self.assertIn('public void onTileOptions(', main)
        self.assertIn('new TileOptionsDialog(', main)

    def test_panel_has_exactly_three_items_and_app_info_keeps_details(self):
        dialog = (JAVA / 'TileOptionsDialog.java').read_text()
        rows = re.findall(r'R\.id\.(option_\w+)', dialog)
        self.assertEqual({'option_fill', 'option_hide', 'option_info'}, set(rows))
        layout = ET.parse(MAIN / 'res/layout/dialog_tile_options.xml').getroot()
        ids = [n.get(A + 'id') for n in layout.iter() if (n.get(A + 'id') or '').startswith('@+id/option_')]
        self.assertEqual(['@+id/option_fill', '@+id/option_hide', '@+id/option_info'], ids)
        for n in layout.iter():
            if (n.get(A + 'id') or '').startswith('@+id/option_'):
                self.assertEqual('true', n.get(A + 'focusable'), n.get(A + 'id'))
        self.assertIn('ACTION_APPLICATION_DETAILS_SETTINGS', dialog)
        self.assertIn('findViewById(R.id.title)).setText(label)', dialog)   # titled with the app name

    def test_hide_reuses_the_single_desktop_state_source(self):
        dialog = (JAVA / 'TileOptionsDialog.java').read_text()
        for text in ['LibraryApps.loadChoices(', 'DesktopApps.toggle(', 'LibraryApps.saveChoices(']:
            self.assertIn(text, dialog)
        for forbidden in ['getSharedPreferences("user_hidden', 'HiddenApps', 'setApplicationEnabledSetting',
                          'deletePackage', 'ACTION_DELETE', 'clearApplicationUserData']:
            self.assertNotIn(forbidden, dialog)

    def test_fill_choice_is_persisted_per_package_and_drives_binding(self):
        store = (JAVA / 'TileFillStore.java').read_text()
        self.assertIn('TileFill.encode(', store)
        self.assertIn('TileFill.decode(', store)
        self.assertIn('AtomicFile', store)
        adapter = (JAVA / 'AppAdapter.java').read_text()
        bind = block(adapter, 'public void onBindViewHolder(', 'private void launch(')
        self.assertIn('TileFill.mode(', bind)
        self.assertIn('ScaleType.CENTER_CROP', bind)
        self.assertIn('ScaleType.FIT_CENTER', bind)
        self.assertIn('void refreshFill(', adapter)
        self.assertIn('notifyItemChanged(', block(adapter, 'void refreshFill(', '\n    }\n'))

    def test_fill_uses_highest_resolution_icon_when_no_banner(self):
        adapter = (JAVA / 'AppAdapter.java').read_text()
        self.assertIn('getDrawableForDensity(', block(adapter, 'private Drawable loadLargeIcon(', '\n    }\n'))

    def test_localized_strings(self):
        en, zh = strings('values'), strings('values-zh-rCN')
        self.assertEqual('Crop to fill', en['tile_option_fill'])
        self.assertEqual('裁剪后填充', zh['tile_option_fill'])
        self.assertEqual('在桌面隐藏', zh['tile_option_hide'])
        self.assertEqual('应用信息', zh['tile_option_info'])
        for key in ['tile_option_fill', 'tile_option_hide', 'tile_option_info']:
            self.assertIn(key, en)

    def test_untouched_areas(self):
        main = (JAVA / 'MainActivity.java').read_text()
        body = block(main, 'private void showSettingsMenu()', 'private void addSettingsMenuRow(')
        rows = re.findall(r'addSettingsMenuRow\(rows, popup, R\.string\.(\w+)', body)
        self.assertEqual(['launcher_settings', 'module_cards', 'reorder', 'app_management'], rows)
        self.assertTrue((JAVA / 'JuosHomeRedirectPolicy.java').exists())


class SharedSwitchContractTest(unittest.TestCase):
    def test_both_places_use_the_same_custom_switch(self):
        for layout in ['item_app_toggle.xml', 'dialog_tile_options.xml']:
            root = ET.parse(MAIN / 'res/layout' / layout).getroot()
            tags = [n.tag for n in root.iter()]
            self.assertIn('com.gathin.tvhome.PillSwitchView', tags, layout)
            self.assertNotIn('Switch', tags, layout)
            for n in root.iter('com.gathin.tvhome.PillSwitchView'):
                self.assertEqual('52dp', n.get(A + 'layout_width'))
                self.assertEqual('31dp', n.get(A + 'layout_height'))
                self.assertEqual('false', n.get(A + 'focusable'))

    def test_switch_view_draws_pill_geometry_and_animates(self):
        view = (JAVA / 'PillSwitchView.java').read_text()
        for text in ['PillSwitch.knobCenterX(', 'PillSwitch.knobRadius(', 'PillSwitch.cornerRadius(',
                     'PillSwitch.blend(', 'PillSwitch.strokeAlpha(', 'PillSwitch.ANIM_MS', 'drawRoundRect(']:
            self.assertIn(text, view)

    def test_row_focus_is_translucent_light_not_solid_white(self):
        root = ET.parse(MAIN / 'res/layout/item_app_toggle.xml').getroot()
        self.assertEqual('@drawable/switch_row_bg', root.get(A + 'background'))
        self.assertEqual('48dp', root.get(A + 'minHeight'))
        bg = ET.parse(MAIN / 'res/drawable/switch_row_bg.xml').getroot()
        focused = [i for i in bg.findall('item') if i.get(A + 'state_focused') == 'true']
        self.assertTrue(focused)
        solid = focused[0].find('shape/solid')
        self.assertIsNotNone(solid)
        colour = (solid.get(A + 'color') or '').upper()
        self.assertEqual('#26FFFFFF', colour)   # white at 15%
        # Text stays white when focused (no inverse-black on white).
        title = [n for n in root.iter() if n.get(A + 'id') == '@+id/title'][0]
        self.assertNotEqual('@drawable/pill_text', title.get(A + 'textColor'))


if __name__ == '__main__':
    unittest.main(verbosity=2)
