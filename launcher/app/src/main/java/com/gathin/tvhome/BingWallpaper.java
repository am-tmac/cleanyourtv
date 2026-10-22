package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Bing's daily image as the wallpaper (launcher settings, Wallpaper, Bing daily image). Saved as
 * {@code files/bing/bing-YYYYMMDD.jpg}; the newest two are kept and a failed day keeps the last good picture.
 * Checked once per local day (retry every 30 minutes after a failure) while Home is visible.
 */
final class BingWallpaper {
    private static final String PREFS = "bing_wallpaper";
    private static final long MAX_BYTES = 10L * 1024 * 1024;

    private BingWallpaper() { }

    static File dir(Context ctx) {
        File base = Config.dir(ctx);
        return base == null ? null : new File(base, "bing");
    }

    /** The newest downloaded picture, or null. */
    static File current(File dir) {
        String[] names = dir == null ? null : dir.list();
        String newest = names == null ? null : BingDaily.newest(Arrays.asList(names));
        File f = newest == null ? null : new File(dir, newest);
        return f != null && f.isFile() && f.length() > 0 ? f : null;
    }

    interface Listener {
        /** Runs on the executor's thread with the picture that is now the newest. */
        void onNewImage(File image);
    }

    static boolean refreshIfDue(Context ctx, Executor executor, Listener listener) {
        Context app = ctx.getApplicationContext();
        File dir = dir(app);
        if (dir == null) return false;
        SharedPreferences p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        long now = System.currentTimeMillis();
        // A picture deleted by hand (or never saved) is fetched again the same day.
        String lastDay = current(dir) == null ? "" : p.getString("day", "");
        if (!RefreshPolicy.bingDue(lastDay, today, p.getLong("attempt", 0), now)) return false;
        p.edit().putLong("attempt", now).apply();
        executor.execute(() -> {
            File image = fetch(dir);
            if (image == null) return;
            p.edit().putString("day", today).apply();
            listener.onNewImage(image);
        });
        return true;
    }

    private static File fetch(File dir) {
        BingDaily.Image info = WallpaperSources.daily(url -> Net.get(url, 128 * 1024));
        if (info == null) return null;
        if (!dir.isDirectory() && !dir.mkdirs()) return null;
        File target = new File(dir, info.fileName);
        if (!(target.isFile() && target.length() > 0)) {
            File part = new File(dir, info.fileName + ".part");
            if (!Net.download(info.url, part, MAX_BYTES)) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(part.getPath(), o);
            if (o.outWidth <= 0 || o.outHeight <= 0 || !part.renameTo(target)) {
                //noinspection ResultOfMethodCallIgnored
                part.delete();
                return null;
            }
        }
        String[] names = dir.list();
        if (names != null) {
            List<String> all = new ArrayList<>(Arrays.asList(names));
            for (String old : BingDaily.stale(all, 2)) //noinspection ResultOfMethodCallIgnored
                new File(dir, old).delete();
        }
        return target;
    }
}
