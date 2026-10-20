package com.gathin.tvhome;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/** Saved per-app "Crop to fill" choices (files/tile_fill.txt, "pkg=1" lines). Only explicit choices are stored. */
final class TileFillStore {
    private TileFillStore() { }

    private static AtomicFile store(Context context) {
        return new AtomicFile(new File(context.getFilesDir(), "tile_fill.txt"));
    }

    /** Never throws: a missing or unreadable file means every app keeps its default look. */
    static Map<String, Boolean> load(Context context) {
        AtomicFile file = store(context);
        if (!file.getBaseFile().exists() && !new File(file.getBaseFile() + ".bak").exists())
            return Collections.emptyMap();
        try (FileInputStream input = file.openRead()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (bytes.size() + count > TileFill.MAX_CHARS) return Collections.emptyMap();
                bytes.write(buffer, 0, count);
            }
            return TileFill.decode(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            Log.w("TvHome.TileFill", "Could not read crop-to-fill choices", e);
            return Collections.emptyMap();
        }
    }

    static boolean save(Context context, String pkg, boolean on) {
        AtomicFile file = store(context);
        FileOutputStream output = null;
        try {
            byte[] bytes = TileFill.encode(TileFill.with(load(context), pkg, on)).getBytes(StandardCharsets.UTF_8);
            output = file.startWrite();
            output.write(bytes);
            file.finishWrite(output);
            return true;
        } catch (IOException | RuntimeException e) {
            if (output != null) file.failWrite(output);
            Log.w("TvHome.TileFill", "Could not save crop-to-fill choice", e);
            return false;
        }
    }
}
