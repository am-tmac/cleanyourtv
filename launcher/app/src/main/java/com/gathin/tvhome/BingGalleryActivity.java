package com.gathin.tvhome;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Remote-only, on-demand Bing archive. All disk and network work stays on owned worker threads. */
public final class BingGalleryActivity extends Activity {
    private final ExecutorService metadata = Executors.newSingleThreadExecutor();
    private final ExecutorService thumbnails = Executors.newFixedThreadPool(3);
    private final Set<Net.Call> calls = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Object lifecycle = new Object();
    private volatile boolean destroyed;
    private boolean selecting;
    private RecyclerView grid;
    private LinearLayout status;
    private TextView message, hint;
    private ProgressBar progress;
    private Button retry;
    private String chosen;
    private List<BingGallery.Image> images = Collections.emptyList();
    // Accessed only on the UI thread. Approximately 32 thumbnails, bounded to 8 MiB.
    private final LruCache<String, Bitmap> memory = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getByteCount(); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(40), dp(24), dp(40), dp(16));
        root.setBackgroundColor(Color.rgb(15, 20, 29));
        TextView heading = text(27);
        heading.setText(R.string.wallpaper_bing_choose);
        root.addView(heading);
        hint = text(15);
        hint.setText(getString(R.string.bing_gallery_hint, 16, "Bing"));
        hint.setTextColor(0xFFBBC4D1);
        root.addView(hint);
        FrameLayout body = new FrameLayout(this);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        grid = new RecyclerView(this);
        GridLayoutManager layout = new GridLayoutManager(this, 4);
        // Do not fetch/prefetch thumbnails for the next off-screen row.
        layout.setItemPrefetchEnabled(false);
        grid.setLayoutManager(layout);
        grid.setItemAnimator(null);
        grid.setClipToPadding(false);
        grid.setClipChildren(false);
        grid.setPadding(dp(8), dp(16), dp(8), dp(16));
        body.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        status = new LinearLayout(this);
        status.setOrientation(LinearLayout.VERTICAL);
        status.setGravity(Gravity.CENTER);
        status.setBackgroundColor(0xF00F141D);
        progress = new ProgressBar(this);
        status.addView(progress, new LinearLayout.LayoutParams(dp(44), dp(44)));
        message = text(20);
        message.setGravity(Gravity.CENTER);
        message.setPadding(0, dp(16), 0, dp(16));
        status.addView(message);
        retry = new Button(this);
        retry.setText(R.string.bing_gallery_retry);
        retry.setOnClickListener(v -> load());
        retry.setOnFocusChangeListener((v, focused) -> {
            v.setScaleX(focused ? 1.06f : 1f);
            v.setScaleY(focused ? 1.06f : 1f);
        });
        status.addView(retry);
        body.addView(status, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        load();
    }

    private void busy(int text) {
        status.setVisibility(View.VISIBLE);
        message.setText(text);
        progress.setVisibility(View.VISIBLE);
        retry.setVisibility(View.GONE);
        grid.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    }

    private void error() {
        selecting = false;
        message.setText(R.string.bing_gallery_error);
        progress.setVisibility(View.GONE);
        retry.setVisibility(View.VISIBLE);
        retry.requestFocus();
    }

    private Net.Call call() {
        Net.Call call = new Net.Call();
        calls.add(call);
        if (destroyed) call.cancel();
        return call;
    }

    private void load() {
        busy(R.string.bing_gallery_loading);
        metadata.execute(() -> {
            Net.Call call = call();
            List<BingGallery.Image> result;
            try {
                result = WallpaperSources.gallery(url -> destroyed ? null : call.get(url, 128 * 1024));
            } finally { calls.remove(call); }
            String current = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE)
                    .getString(Config.WALLPAPER_CHOICE_KEY, null);
            runOnUiThread(() -> {
                if (destroyed) return;
                if (result.isEmpty()) { error(); return; }
                chosen = current;
                images = result;
                hint.setText(getString(R.string.bing_gallery_hint, images.size(), WallpaperSources.sourceLabel(images)));
                grid.setAdapter(new Pictures());
                grid.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
                status.setVisibility(View.GONE);
                int selected = 0;
                for (int i = 0; i < images.size(); i++) if (images.get(i).fileName.equals(chosen)) selected = i;
                final int initial = selected;
                grid.scrollToPosition(initial);
                grid.post(() -> {
                    if (destroyed) return;
                    RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(initial);
                    if (holder != null) holder.itemView.requestFocus();
                    else grid.requestFocus();
                });
            });
        });
    }

    private void select(BingGallery.Image image) {
        if (selecting || destroyed) return;
        selecting = true;
        busy(R.string.bing_gallery_applying);
        metadata.execute(() -> {
            File base = Config.dir(this);
            File dir = base == null ? null : new File(base, "wallpapers");
            boolean ok = false;
            if (dir != null && (dir.isDirectory() || dir.mkdirs())) {
                File target = new File(dir, image.fileName);
                ok = BingGallery.alreadyDownloaded(dir, image.fileName);
                if (!ok) {
                    Net.Call call = call();
                    File part = null;
                    try {
                        part = File.createTempFile("bing-pick-", ".part", dir);
                        if (call.download(image.fullUrl, part, 10L * 1024 * 1024) && !destroyed) {
                            BitmapFactory.Options bounds = new BitmapFactory.Options();
                            bounds.inJustDecodeBounds = true;
                            BitmapFactory.decodeFile(part.getPath(), bounds);
                            ok = bounds.outWidth > 0 && bounds.outHeight > 0 && !destroyed && part.renameTo(target);
                        }
                    } catch (IOException | RuntimeException e) {
                        ok = false;
                    } finally {
                        calls.remove(call);
                        if (part != null) delete(part);
                    }
                }
                synchronized (lifecycle) {
                    if (ok && !destroyed) {
                        SharedPreferences prefs = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
                        try {
                            WallpaperCollection.setTitle(dir, image.fileName, image.title);
                        } catch (IOException e) {
                            ok = false;
                        }
                        if (ok) prefs.edit().putString(Config.WALLPAPER_CHOICE_KEY, image.fileName).apply();
                        String[] names = dir.list();
                        if (ok && names != null) for (String old : BingGallery.stale(Arrays.asList(names), image.fileName)) {
                            try { WallpaperCollection.delete(dir, old); }
                            catch (IOException ignored) { /* Keep the file if title cleanup failed. */ }
                        }
                    }
                }
            }
            final boolean success = ok;
            runOnUiThread(() -> {
                if (destroyed) return;
                if (!success) {
                    selecting = false;
                    status.setVisibility(View.GONE);
                    grid.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
                    grid.requestFocus();
                    Toast.makeText(this, R.string.bing_gallery_error, Toast.LENGTH_LONG).show();
                    return;
                }
                setResult(RESULT_OK);
                Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    private final class Pictures extends RecyclerView.Adapter<Picture> {
        @NonNull @Override public Picture onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            int width = Math.max(dp(100), (grid.getWidth() - grid.getPaddingLeft() - grid.getPaddingRight()) / 4 - dp(20));
            return new Picture(width);
        }
        @Override public void onBindViewHolder(@NonNull Picture holder, int position) {
            holder.cancel();
            holder.image = images.get(position);
            holder.title.setText(holder.image.title);
            holder.date.setText(holder.image.dateLabel());
            holder.badge.setVisibility(holder.image.fileName.equals(chosen) ? View.VISIBLE : View.GONE);
            holder.photo.setImageBitmap(memory.get(holder.image.fileName));
            holder.itemView.setContentDescription(holder.image.title + ", " + holder.image.dateLabel()
                    + (holder.image.fileName.equals(chosen) ? ", " + getString(R.string.bing_gallery_selected) : ""));
        }
        @Override public int getItemCount() { return images.size(); }
        @Override public void onViewAttachedToWindow(@NonNull Picture holder) { holder.loadThumbnail(); }
        @Override public void onViewDetachedFromWindow(@NonNull Picture holder) { holder.cancel(); }
        @Override public void onViewRecycled(@NonNull Picture holder) { holder.cancel(); }
    }

    private final class Picture extends RecyclerView.ViewHolder {
        final ImageView photo;
        final TextView title, date, badge;
        BingGallery.Image image;
        Net.Call request;
        Future<?> task;
        int generation;

        Picture(int width) {
            super(new LinearLayout(BingGalleryActivity.this));
            LinearLayout card = (LinearLayout) itemView;
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(4), dp(4), dp(4), dp(6));
            card.setFocusable(true);
            card.setClickable(true);
            card.setBackground(rim(false));
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, -2);
            params.setMargins(dp(10), dp(10), dp(10), dp(10));
            card.setLayoutParams(params);
            card.setOnFocusChangeListener((v, focused) -> {
                v.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f).setDuration(130).start();
                v.setElevation(focused ? dp(8) : 0);
                v.setBackground(rim(focused));
                if (focused) loadThumbnail();
            });
            card.setOnClickListener(v -> select(image));
            FrameLayout preview = new FrameLayout(BingGalleryActivity.this);
            preview.setBackground(rim(false));
            preview.setClipToOutline(true);
            photo = new ImageView(BingGalleryActivity.this);
            photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            preview.addView(photo, new FrameLayout.LayoutParams(-1, -1));
            badge = text(21);
            badge.setText("✓");
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(rim(true));
            badge.setContentDescription(getString(R.string.bing_gallery_selected));
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.TOP | Gravity.END);
            badgeParams.setMargins(dp(6), dp(6), dp(6), dp(6));
            preview.addView(badge, badgeParams);
            card.addView(preview, new LinearLayout.LayoutParams(-1, (width - dp(8)) * 240 / 400));
            title = text(16);
            title.setMaxLines(2);
            title.setMinLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setPadding(dp(4), dp(5), dp(4), 0);
            card.addView(title);
            date = text(13);
            date.setTextColor(0xFFBBC4D1);
            date.setPadding(dp(4), 0, dp(4), 0);
            card.addView(date);
        }

        void loadThumbnail() {
            if (destroyed || image == null || request != null) return;
            Bitmap cached = memory.get(image.fileName);
            if (cached != null) { photo.setImageBitmap(cached); return; }
            final BingGallery.Image wanted = image;
            final int token = generation;
            final Net.Call call = call();
            request = call;
            task = thumbnails.submit(() -> {
                File part = null;
                Bitmap bitmap = null;
                try {
                    if (!Thread.currentThread().isInterrupted() && !destroyed) {
                        part = File.createTempFile("bing-thumb-", ".part", getCacheDir());
                        if (call.download(wanted.thumbUrl, part, 1024 * 1024)) {
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inJustDecodeBounds = true;
                            BitmapFactory.decodeFile(part.getPath(), options);
                            if (options.outWidth > 0 && options.outWidth <= 800 && options.outHeight > 0 && options.outHeight <= 480) {
                                options.inJustDecodeBounds = false;
                                bitmap = BitmapFactory.decodeFile(part.getPath(), options);
                            }
                        }
                    }
                } catch (IOException | RuntimeException ignored) {
                    // A failed thumbnail can be retried by focusing it or re-entering the viewport.
                } finally {
                    if (part != null) delete(part);
                    calls.remove(call);
                }
                final Bitmap loaded = bitmap;
                runOnUiThread(() -> {
                    if (destroyed || generation != token) return;
                    request = null;
                    if (loaded != null) {
                        memory.put(wanted.fileName, loaded);
                        photo.setImageBitmap(loaded);
                    }
                });
            });
        }

        void cancel() {
            generation++;
            if (request != null) { request.cancel(); calls.remove(request); request = null; }
            if (task != null) { task.cancel(true); task = null; }
        }
    }

    private GradientDrawable rim(boolean focused) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0xFF263142);
        drawable.setCornerRadius(dp(12));
        drawable.setStroke(dp(2), focused ? Color.WHITE : Color.TRANSPARENT);
        return drawable;
    }

    private TextView text(int sp) {
        TextView text = new TextView(this);
        text.setTextColor(Color.WHITE);
        text.setTextSize(sp);
        return text;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static void delete(File file) {
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    @Override protected void onDestroy() {
        synchronized (lifecycle) { destroyed = true; }
        for (Net.Call call : calls) call.cancel();
        calls.clear();
        metadata.shutdownNow();
        thumbnails.shutdownNow();
        grid.setAdapter(null);
        memory.evictAll();
        super.onDestroy();
    }
}
