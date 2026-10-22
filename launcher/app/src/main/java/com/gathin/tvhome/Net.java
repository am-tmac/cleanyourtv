package com.gathin.tvhome;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Tiny blocking HTTP GET for the weather line and the Bing wallpaper. Background threads only; 8 s timeouts. */
final class Net {
    static final int TIMEOUT_MS = 8_000;

    private Net() { }

    private static HttpURLConnection open(String url, String accept) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setInstanceFollowRedirects(true);
        c.setUseCaches(false);
        c.setRequestProperty("User-Agent", "CleanYourTV-launcher");
        c.setRequestProperty("Accept", accept);
        return c;
    }

    /** The body of a 200 reply as UTF-8, or null on any error or a body over maxBytes. */
    static String get(String url, int maxBytes) {
        return new Call().get(url, maxBytes);
    }

    /** One cancellable request; gallery owns and cancels these on detach/destroy. */
    static final class Call {
        private volatile HttpURLConnection active;
        private volatile boolean cancelled;

        void cancel() {
            cancelled = true;
            HttpURLConnection c = active;
            if (c != null) c.disconnect();
        }

        private HttpURLConnection connect(String url, String accept) throws IOException {
            HttpURLConnection c = open(url, accept);
            active = c;
            if (cancelled || Thread.currentThread().isInterrupted()) {
                c.disconnect();
                throw new IOException("Cancelled");
            }
            return c;
        }

        String get(String url, int maxBytes) {
            HttpURLConnection c = null;
            try {
                c = connect(url, "application/json");
                if (c.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
                try (InputStream in = c.getInputStream()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        if (cancelled || Thread.currentThread().isInterrupted() || out.size() > maxBytes) return null;
                    }
                    return new String(out.toByteArray(), StandardCharsets.UTF_8);
                }
            } catch (IOException | RuntimeException e) {
                return null;
            } finally {
                if (c != null) c.disconnect();
                active = null;
            }
        }

        /** Saves a 200 image/* reply; removes partial files on failure or cancellation. */
        boolean download(String url, File out, long maxBytes) {
            HttpURLConnection c = null;
            boolean ok = false;
            try {
                if (!WallpaperSources.safeImageUrl(url)) return false;
                c = connect(url, "image/*");
                // Image URLs are allowlisted; do not let a redirect escape that boundary.
                c.setInstanceFollowRedirects(false);
                String type = c.getContentType();
                if (c.getResponseCode() != HttpURLConnection.HTTP_OK || type == null || !type.startsWith("image/")) return false;
                long total = 0;
                try (InputStream in = c.getInputStream(); OutputStream o = new FileOutputStream(out)) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        total += n;
                        if (cancelled || Thread.currentThread().isInterrupted() || total > maxBytes) return false;
                        o.write(buf, 0, n);
                    }
                }
                ok = total > 0;
                return ok;
            } catch (IOException | RuntimeException e) {
                return false;
            } finally {
                if (c != null) c.disconnect();
                active = null;
                if (!ok) //noinspection ResultOfMethodCallIgnored
                    out.delete();
            }
        }
    }

    static boolean download(String url, File out, long maxBytes) {
        return new Call().download(url, out, maxBytes);
    }
}
