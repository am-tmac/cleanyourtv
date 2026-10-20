package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Settings menu → App management. Lists every launchable app (leanback and launcher-only, one row
 * per package, CleanYourTV itself excluded) with a desktop switch. The switch reads and writes the
 * same added/removed choice files the tiles use, so it always shows what the desktop shows. It only
 * changes the desktop: nothing is uninstalled, disabled, stopped or cleared.
 */
final class AppManagementDialog {
    private final Activity activity;
    private final Set<String> theme;
    private final Runnable closed;
    private final Map<String, ResolveInfo> entries = new HashMap<>();
    private final Map<String, Drawable> icons = new HashMap<>();
    private List<DesktopApps.Row> rows = new ArrayList<>();
    private RecyclerView list;

    AppManagementDialog(Activity activity, Set<String> theme, Runnable closed) {
        this.activity = activity;
        this.theme = theme;
        this.closed = closed;
    }

    void show() {
        DesktopApps.Choices choices = LibraryApps.loadChoices(activity);
        if (choices == null) {
            Toast.makeText(activity, R.string.app_management_save_failed, Toast.LENGTH_LONG).show();
            return;
        }
        rows = load(choices);
        if (rows.isEmpty()) {
            Toast.makeText(activity, R.string.no_apps_to_manage, Toast.LENGTH_SHORT).show();
            return;
        }
        View panel = LayoutInflater.from(activity).inflate(R.layout.dialog_app_management, null, false);
        list = panel.findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(activity));
        list.setItemAnimator(null);   // a switch flip must not fade the row and lose focus
        list.setAdapter(new Rows());

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(panel);
        Window w = dialog.getWindow();
        if (w != null) {
            float density = activity.getResources().getDisplayMetrics().density;
            int height = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.86f);
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (560 * density), height);
            w.setDimAmount(0.6f);
        }
        dialog.setOnDismissListener(d -> closed.run());   // Back: close and rebuild the desktop
        dialog.show();
        list.post(() -> {
            RecyclerView.ViewHolder first = list.findViewHolderForAdapterPosition(0);
            if (first != null) first.itemView.requestFocus();
        });
    }

    private List<DesktopApps.Row> load(DesktopApps.Choices choices) {
        PackageManager pm = activity.getPackageManager();
        List<DesktopApps.Launchable> found = new ArrayList<>();
        entries.clear();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            boolean leanback = Intent.CATEGORY_LEANBACK_LAUNCHER.equals(category);
            for (ResolveInfo info : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                if (info.activityInfo == null || !info.activityInfo.exported) continue;
                String pkg = info.activityInfo.packageName;
                String label;
                try { label = Cards.cleanLabel(String.valueOf(info.loadLabel(pm)), pkg); }
                catch (RuntimeException unavailable) { label = pkg; }
                found.add(new DesktopApps.Launchable(pkg, label, leanback));
                if (!entries.containsKey(pkg)) entries.put(pkg, info);   // leanback queried first
            }
        }
        Collator collator = Collator.getInstance();
        return DesktopApps.rows(found, theme, choices, activity.getPackageName(), collator::compare);
    }

    private void toggle(int position) {
        if (position < 0 || position >= rows.size()) return;
        DesktopApps.Row row = rows.get(position);
        if (row.state == DesktopApps.State.THEME_HIDDEN) {
            new AlertDialog.Builder(activity).setTitle(row.label)
                    .setMessage(R.string.app_management_theme_locked)
                    .setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        // Re-read the files so a library change made elsewhere is never overwritten.
        DesktopApps.Choices current = LibraryApps.loadChoices(activity);
        DesktopApps.Choices next = current == null ? null : DesktopApps.toggle(current, row.pkg,
                !row.shown(), row.leanback, theme, activity.getPackageName());
        if (next == null || !LibraryApps.saveChoices(activity, next)) {
            Toast.makeText(activity, R.string.app_management_save_failed, Toast.LENGTH_LONG).show();
            return;
        }
        String pkg = row.pkg;
        rows = load(next);
        int now = 0;
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).pkg.equals(pkg)) now = i;
        list.getAdapter().notifyDataSetChanged();
        final int focus = now;
        list.post(() -> {
            RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(focus);
            if (holder != null) holder.itemView.requestFocus();
        });
    }

    private Drawable icon(String pkg) {
        if (icons.containsKey(pkg)) return icons.get(pkg);
        Drawable icon = null;
        ResolveInfo info = entries.get(pkg);
        if (info != null) {
            try { icon = info.loadIcon(activity.getPackageManager()); }
            catch (RuntimeException unavailable) { icon = null; }
        }
        icons.put(pkg, icon);
        return icon;
    }

    private final class Rows extends RecyclerView.Adapter<Rows.Holder> {
        final class Holder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView title, note;
            final PillSwitchView toggle;
            /** Package last bound here: a rebind of the same app slides the knob instead of jumping. */
            String bound;
            Holder(View view) {
                super(view);
                icon = view.findViewById(R.id.icon);
                title = view.findViewById(R.id.title);
                note = view.findViewById(R.id.note);
                toggle = view.findViewById(R.id.toggle);
                view.setOnClickListener(v -> toggle(getBindingAdapterPosition()));
            }
        }

        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_toggle, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            DesktopApps.Row row = rows.get(position);
            holder.icon.setImageDrawable(icon(row.pkg));
            holder.title.setText(row.label);
            boolean locked = row.state == DesktopApps.State.THEME_HIDDEN;
            holder.note.setVisibility(locked ? View.VISIBLE : View.GONE);
            if (locked) holder.note.setText(R.string.app_hidden_by_theme);
            holder.toggle.setChecked(row.shown(), row.pkg.equals(holder.bound));
            holder.bound = row.pkg;
            holder.toggle.setEnabled(!locked);
            holder.itemView.setContentDescription(row.label);
        }

        @Override public int getItemCount() { return rows.size(); }
    }
}
