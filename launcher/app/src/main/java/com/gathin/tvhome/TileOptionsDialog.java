package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Set;

/**
 * Long-press on a desktop tile (dock or grid): the app's options, titled with its name.
 * Crop to fill (per package, TileFillStore), Hide from desktop (the same added/removed files as
 * App management) and App info (the system app details page, as long-press used to open). Back closes.
 */
final class TileOptionsDialog {
    interface Callbacks {
        /** The fill choice changed: refresh this package's tiles now. */
        void fillChanged(String pkg);
        /** The app was taken off the desktop: rebuild the tiles. */
        void hidden(String pkg);
        void closed();
    }

    private final Activity activity;
    private final String pkg, label;
    private final boolean hasBanner, fillAvailable;
    private final Set<String> theme;
    private final Callbacks callbacks;
    private Dialog dialog;

    /** fillAvailable false for module cards, which keep their own look. */
    TileOptionsDialog(Activity activity, String pkg, String label, boolean hasBanner, boolean fillAvailable,
                      Set<String> theme, Callbacks callbacks) {
        this.activity = activity;
        this.pkg = pkg;
        this.label = label;
        this.hasBanner = hasBanner;
        this.fillAvailable = fillAvailable;
        this.theme = theme;
        this.callbacks = callbacks;
    }

    void show() {
        View panel = LayoutInflater.from(activity).inflate(R.layout.dialog_tile_options, null, false);
        ((TextView) panel.findViewById(R.id.title)).setText(label);
        View fillRow = panel.findViewById(R.id.option_fill);
        PillSwitchView fillSwitch = panel.findViewById(R.id.fill_switch);
        fillSwitch.setChecked(TileFill.enabled(TileFillStore.load(activity), pkg, hasBanner), false);
        if (fillAvailable) {
            onOk(fillRow, () -> {
                boolean next = !fillSwitch.isChecked();
                if (!TileFillStore.save(activity, pkg, next)) {
                    Toast.makeText(activity, R.string.tile_option_save_failed, Toast.LENGTH_LONG).show();
                    return;
                }
                fillSwitch.setChecked(next, true);
                callbacks.fillChanged(pkg);
            });
        } else {
            fillRow.setVisibility(View.GONE);
        }
        onOk(panel.findViewById(R.id.option_hide), this::hide);
        onOk(panel.findViewById(R.id.option_info), () -> {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            dialog.dismiss();
            try {
                activity.startActivity(i);
            } catch (ActivityNotFoundException ignored) {
            }
        });

        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(panel);
        Window w = dialog.getWindow();
        if (w != null) {
            float density = activity.getResources().getDisplayMetrics().density;
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (400 * density), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.6f);
        }
        dialog.setOnDismissListener(d -> callbacks.closed());
        dialog.show();
        (fillAvailable ? fillRow : panel.findViewById(R.id.option_hide)).requestFocus();
    }

    void dismiss() {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
    }

    private void hide() {
        // Re-read the files so a change made elsewhere is never overwritten; same rules as App management.
        DesktopApps.Choices current = LibraryApps.loadChoices(activity);
        DesktopApps.Choices next = current == null ? null
                : DesktopApps.toggle(current, pkg, false, false, theme, activity.getPackageName());
        if (next == null || !LibraryApps.saveChoices(activity, next)) {
            Toast.makeText(activity, R.string.tile_option_save_failed, Toast.LENGTH_LONG).show();
            return;
        }
        dialog.dismiss();
        callbacks.hidden(pkg);
    }

    /**
     * OK / Enter / click on a row. The OK that long-pressed the tile is still held when the panel opens:
     * only a fresh press (repeat 0) seen by this row may act on its release.
     */
    private static void onOk(View row, Runnable action) {
        row.setOnClickListener(v -> action.run());
        final boolean[] pressed = {false};
        row.setOnKeyListener((v, code, event) -> {
            if (code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER
                    && code != KeyEvent.KEYCODE_NUMPAD_ENTER) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) pressed[0] = true;
                return true;
            }
            boolean activate = pressed[0];
            pressed[0] = false;
            if (activate) action.run();
            return true;
        });
    }
}
