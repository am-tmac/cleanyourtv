package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Same panel and row focus styling as App management; no long press needed. */
final class WallpaperManagementDialog {
    private final Activity activity;
    private final SharedPreferences choices;
    private final Runnable changed, closed;
    private final File directory;
    private final ExecutorService thumbnails = Executors.newSingleThreadExecutor();
    private boolean dismissed;
    private List<File> files;
    private RecyclerView list;
    private View empty, close;

    WallpaperManagementDialog(Activity activity, SharedPreferences choices, Runnable changed, Runnable closed) {
        this.activity = activity;
        this.choices = choices;
        this.changed = changed;
        this.closed = closed;
        File base = activity.getExternalFilesDir(null);
        directory = base == null ? null : new File(base, "wallpapers");
    }

    void show() {
        files = WallpaperCollection.images(directory);
        View panel = LayoutInflater.from(activity).inflate(R.layout.dialog_wallpaper_management, new FrameLayout(activity), false);
        list = panel.findViewById(R.id.list);
        empty = panel.findViewById(R.id.empty);
        close = panel.findViewById(R.id.close);
        list.setLayoutManager(new LinearLayoutManager(activity));
        list.setItemAnimator(null);
        list.setAdapter(new Rows());
        empty.setVisibility(files.isEmpty() ? View.VISIBLE : View.GONE);
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(panel);
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(d -> {
            dismissed = true;
            thumbnails.shutdownNow();
            list.setAdapter(null);
            if (!activity.isFinishing() && !activity.isDestroyed()) closed.run();
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            float density = activity.getResources().getDisplayMetrics().density;
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(Math.min((int) (760 * density),
                            (int) (activity.getResources().getDisplayMetrics().widthPixels * .92f)),
                    (int) (activity.getResources().getDisplayMetrics().heightPixels * .86f));
            window.setDimAmount(.6f);
        }
        focus(0);
    }

    private String name(File file) {
        boolean chinese = "zh".equals(activity.getResources().getConfiguration().getLocales().get(0).getLanguage());
        return WallpaperCollection.displayName(file.getName(), WallpaperCollection.title(directory, file.getName()), chinese);
    }

    private void confirm(File file) {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.wallpaper_delete_confirm)
                .setMessage(name(file))
                .setPositiveButton(R.string.wallpaper_delete, (d, which) -> remove(file))
                .setNegativeButton(android.R.string.cancel, null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus());
        dialog.show();
    }

    private void remove(File file) {
        int position = files.indexOf(file);
        boolean deleted;
        try { deleted = WallpaperCollection.delete(directory, file.getName()); }
        catch (IOException | SecurityException e) { deleted = false; }
        if (!deleted) {
            Toast.makeText(activity, R.string.wallpaper_delete_failed, Toast.LENGTH_LONG).show();
            return;
        }
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        if (current != null && WallpaperCollection.choiceAfterDelete(current, file.getName()) == null) {
            // Identical to THEME DEFAULT: removing the key restores the theme's fallback.
            choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
        }
        changed.run();
        Toast.makeText(activity, R.string.wallpaper_deleted, Toast.LENGTH_SHORT).show();
        files.remove(position);
        list.getAdapter().notifyItemRemoved(position);
        empty.setVisibility(files.isEmpty() ? View.VISIBLE : View.GONE);
        focus(Math.max(0, Math.min(position, files.size() - 1)));
    }

    private void focus(int position) {
        if (files.isEmpty()) { close.requestFocus(); return; }
        list.scrollToPosition(position);
        list.post(() -> {
            if (dismissed) return;
            RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(position);
            if (holder != null) holder.itemView.requestFocus();
        });
    }

    private final class Rows extends RecyclerView.Adapter<Holder> {
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new Holder(LayoutInflater.from(activity).inflate(R.layout.item_wallpaper_management, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.cancel();
            File file = files.get(position);
            holder.file = file;
            String label = name(file);
            holder.title.setText(label);
            holder.note.setText(Formatter.formatFileSize(activity, file.length()));
            boolean current = file.getName().equals(choices.getString(Config.WALLPAPER_CHOICE_KEY, null));
            holder.badge.setVisibility(current ? View.VISIBLE : View.GONE);
            holder.itemView.setContentDescription(label + ", " + holder.note.getText()
                    + (current ? ", " + activity.getString(R.string.wallpaper_current) : ""));
            holder.icon.setImageDrawable(null);
            int token = holder.generation;
            holder.task = thumbnails.submit(() -> {
                Bitmap bitmap = thumbnail(file);
                activity.runOnUiThread(() -> {
                    if (!dismissed && token == holder.generation) holder.icon.setImageBitmap(bitmap);
                    else if (bitmap != null) bitmap.recycle();
                });
            });
        }
        @Override public void onViewRecycled(@NonNull Holder holder) { holder.cancel(); }
        @Override public int getItemCount() { return files.size(); }
    }

    private static Bitmap thumbnail(File file) {
        if (file.length() > 10L * 1024 * 1024) return null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            if (options.outWidth <= 0 || options.outHeight <= 0) return null;
            options.inSampleSize = 1;
            while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 384)
                options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(file.getPath(), options);
        } catch (RuntimeException e) { return null; }
    }

    private final class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title, note, badge;
        File file;
        Future<?> task;
        int generation;
        Holder(View view) {
            super(view);
            icon = view.findViewById(R.id.icon);
            title = view.findViewById(R.id.title);
            note = view.findViewById(R.id.note);
            badge = view.findViewById(R.id.currentBadge);
            view.setOnClickListener(v -> { if (file != null) confirm(file); });
        }
        void cancel() {
            generation++;
            if (task != null) { task.cancel(true); task = null; }
        }
    }
}
