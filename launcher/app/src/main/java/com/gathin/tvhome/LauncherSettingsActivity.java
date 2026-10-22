package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    private static final int BING_GALLERY_REQUEST = 301;
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private TextView wallpaperChoice;
    private TextView wallpaperDimChoice;
    private PillSwitchView glassDockSwitch;
    private PillSwitchView weatherSwitch;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private int columns;
    private int rowsBelowDock;
    private boolean animations;
    private boolean advancedEffects;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                setResult(RESULT_OK);
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                setResult(RESULT_OK);
                refresh();
            });
        }
        // Keep the numeric choices increasing in the Hebrew reading direction. Reorder the
        // actual views so D-pad focus follows their positions without changing the saved values.
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
        }
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        wallpaperDimChoice = findViewById(R.id.wallpaperDimChoice);
        wallpaperDimChoice.setOnClickListener(v -> {
            int next = AppearanceSettings.nextDim(
                    choices.getInt(AppearanceSettings.DIM_KEY, AppearanceSettings.DIM_DEFAULT));
            if (next < 0) choices.edit().remove(AppearanceSettings.DIM_KEY).apply();
            else choices.edit().putInt(AppearanceSettings.DIM_KEY, next).apply();
            setResult(RESULT_OK);
            refresh();
        });
        glassDockSwitch = findViewById(R.id.glassDockSwitch);
        findViewById(R.id.glassDockChoice).setOnClickListener(v -> {
            boolean on = !choices.getBoolean(AppearanceSettings.GLASS_DOCK_KEY, AppearanceSettings.GLASS_DOCK_DEFAULT);
            choices.edit().putBoolean(AppearanceSettings.GLASS_DOCK_KEY, on).apply();
            glassDockSwitch.setChecked(on, true);
            setResult(RESULT_OK);
        });
        weatherSwitch = findViewById(R.id.weatherSwitch);
        findViewById(R.id.weatherChoice).setOnClickListener(v -> {
            boolean on = !choices.getBoolean(AppearanceSettings.WEATHER_KEY, AppearanceSettings.WEATHER_DEFAULT);
            choices.edit().putBoolean(AppearanceSettings.WEATHER_KEY, on).apply();
            weatherSwitch.setChecked(on, true);
            setResult(RESULT_OK);
        });
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            setResult(RESULT_OK);
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(AppearanceSettings.DIM_KEY).commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            setResult(RESULT_OK);
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        findViewById(R.id.juosRedirectChoice).setOnClickListener(v -> {
            SharedPreferences prefs = getSharedPreferences(JuosHomeRedirectService.PREFS, MODE_PRIVATE);
            boolean enabled = !prefs.getBoolean(JuosHomeRedirectService.ENABLED, false);
            if (enabled) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.juos_redirect_name)
                        .setMessage(R.string.juos_redirect_help)
                        .setPositiveButton(R.string.enable, (dialog, which) -> {
                            prefs.edit().putBoolean(JuosHomeRedirectService.ENABLED, true)
                                    .putBoolean(JuosHomeRedirectService.STOPPED, false).apply();
                            refresh();
                        })
                        .setNegativeButton(android.R.string.cancel, null).show();
            } else {
                prefs.edit().putBoolean(JuosHomeRedirectService.ENABLED, false).apply();
                refresh();
            }
        });
        findViewById(R.id.juosAccessibilityChoice).setOnClickListener(v -> openAccessibilitySettings());
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(new Intent(Settings.ACTION_SETTINGS)));
        refresh();
        columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        images.removeIf(file -> WallpaperCollection.resolve(file.getParentFile(), file.getName()) == null);
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        // Rows: daily Bing, on-demand Bing gallery, collection pictures, None.
        String[] labels = new String[images.size() + 3];
        labels[0] = getString(R.string.wallpaper_bing);
        labels[1] = getString(R.string.wallpaper_bing_choose);
        for (int i = 0; i < images.size(); i++) labels[i + 2] = wallpaperName(images.get(i).getName());
        labels[images.size() + 2] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int found = indexOf(images, current);
        int checked = "none".equals(current) ? images.size() + 2
                : AppearanceSettings.WALLPAPER_BING.equals(current) ? 0 : found < 0 ? -1 : found + 2;
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == images.size() + 2) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    } else if (which == 0) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, AppearanceSettings.WALLPAPER_BING).apply();
                    } else if (which == 1) {
                        startActivityForResult(new Intent(this, BingGalleryActivity.class), BING_GALLERY_REQUEST);
                        return;
                    } else {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                images.get(which - 2).getName()).apply();
                    }
                    setResult(RESULT_OK);
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    setResult(RESULT_OK);
                    refresh();
                })
                .setPositiveButton(R.string.wallpaper_manage, (dialog, which) ->
                        new WallpaperManagementDialog(this, choices, () -> {
                            setResult(RESULT_OK);
                            refresh();
                        }, () -> {
                            refresh();
                            showWallpaperPicker();
                        }).show())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String wallpaperName(String name) {
        File base = Config.dir(this);
        File directory = base == null ? null : new File(base, "wallpapers");
        boolean chinese = "zh".equals(getResources().getConfiguration().getLocales().get(0).getLanguage());
        return WallpaperCollection.displayName(name, WallpaperCollection.title(directory, name), chinese);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BING_GALLERY_REQUEST && resultCode == RESULT_OK) {
            setResult(RESULT_OK);
            refresh();
        }
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        setResult(RESULT_OK);
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        if (choices != null) refresh();
    }

    private void refresh() {
        SharedPreferences prefs = getSharedPreferences(JuosHomeRedirectService.PREFS, MODE_PRIVATE);
        boolean redirectEnabled = prefs.getBoolean(JuosHomeRedirectService.ENABLED, false);
        TextView redirectChoice = findViewById(R.id.juosRedirectChoice);
        redirectChoice.setText(getString(R.string.juos_redirect_state,
                getString(redirectEnabled ? R.string.on : R.string.off)));
        redirectChoice.setSelected(redirectEnabled);
        findViewById(R.id.juosSafetyStatus).setVisibility(
                prefs.getBoolean(JuosHomeRedirectService.STOPPED, false)
                        ? android.view.View.VISIBLE : android.view.View.GONE);
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : AppearanceSettings.WALLPAPER_BING.equals(wallpaperCurrent) ? getString(R.string.wallpaper_bing)
                        : wallpaperCurrent != null ? wallpaperName(wallpaperCurrent)
                        : getString(R.string.wallpaper_theme_default)));
        followThemeChoice.setSelected(!hasOnTvChoices());
        int dim = choices.getInt(AppearanceSettings.DIM_KEY, AppearanceSettings.DIM_DEFAULT);
        wallpaperDimChoice.setText(getString(R.string.wallpaper_dim_state,
                dim < 0 ? getString(R.string.wallpaper_theme_default) : dim + "%"));
        glassDockSwitch.setChecked(choices.getBoolean(AppearanceSettings.GLASS_DOCK_KEY,
                AppearanceSettings.GLASS_DOCK_DEFAULT), false);
        weatherSwitch.setChecked(choices.getBoolean(AppearanceSettings.WEATHER_KEY,
                AppearanceSettings.WEATHER_DEFAULT), false);
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(AppearanceSettings.DIM_KEY);
    }

    private void open(Intent intent) {
        if (!tryStart(intent)) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }

    private boolean tryStart(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    /** JUOS hides the standard action; fall back to its AOSP page, then to one-time ADB help. */
    private void openAccessibilitySettings() {
        for (AccessibilitySettingsRoute.Target target : AccessibilitySettingsRoute.candidates()) {
            Intent intent = target.action != null ? new Intent(target.action)
                    : new Intent().setClassName(target.packageName, target.className);
            if (tryStart(intent)) return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.juos_accessibility_adb_title)
                .setMessage(getString(R.string.juos_accessibility_adb_help,
                        AccessibilitySettingsRoute.commandsText()))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
