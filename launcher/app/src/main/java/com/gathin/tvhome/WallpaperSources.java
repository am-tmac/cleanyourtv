package com.gathin.tvhome;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Pure metadata selection. A failed primary is exhausted before the mirror is requested. */
final class WallpaperSources {
    static final String PEAPIX_API = "https://peapix.com/bing/feed?country=cn";
    enum Source { BING, PEAPIX }
    interface Get {
        /** Return only a 200 body; null represents HTTP/network failure. */
        String get(String url) throws Exception;
    }
    private static final Pattern MIRROR = Pattern.compile("https://img\\.peapix\\.com/[a-fA-F0-9]{32}(?:_1920|_640)?\\.jpg");
    private static final Pattern BING = Pattern.compile("https://www\\.bing\\.com/th\\?id=OHR\\.[A-Za-z0-9_-][A-Za-z0-9_.-]*_(?:1920x1080|400x240)\\.jpg");

    private WallpaperSources() { }

    static boolean safeImageUrl(String url) {
        return url != null && (MIRROR.matcher(url).matches()
                || (BING.matcher(url).matches() && !url.contains("..")));
    }

    static List<BingGallery.Image> parsePeapix(String body) {
        List<?> rows = MiniJson.array(MiniJson.parse(body));
        List<BingGallery.Image> images = new ArrayList<>();
        if (rows == null) return images;
        for (Object row : rows) {
            Map<String, Object> m = MiniJson.object(row);
            String date = MiniJson.string(m, "date");
            String full = MiniJson.string(m, "fullUrl");
            String thumb = MiniJson.string(m, "thumbUrl");
            if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")
                    || full == null || thumb == null || !MIRROR.matcher(full).matches()
                    || !MIRROR.matcher(thumb).matches()) continue;
            try { date = LocalDate.parse(date).format(DateTimeFormatter.BASIC_ISO_DATE); }
            catch (DateTimeParseException ignored) { continue; }
            String hash = full.substring("https://img.peapix.com/".length(), "https://img.peapix.com/".length() + 32);
            String title = MiniJson.string(m, "title");
            if (title == null || title.trim().isEmpty()) title = date;
            BingGallery.Image image = new BingGallery.Image(date, title,
                    "bing-" + date + "-peapix-" + hash + ".jpg", full, thumb, Source.PEAPIX);
            addUnique(images, Collections.singletonList(image));
        }
        images.sort(Comparator.comparing((BingGallery.Image image) -> image.startDate).reversed());
        return images.size() <= 7 ? images : new ArrayList<>(images.subList(0, 7));
    }

    private static String body(Get get, String url) {
        try { return get.get(url); }
        catch (Exception ignored) { return null; }
    }

    private static List<BingGallery.Image> first(Get get, String primary) {
        List<BingGallery.Image> images = BingGallery.parse(body(get, primary));
        return images.isEmpty() ? parsePeapix(body(get, PEAPIX_API)) : images;
    }

    static List<BingGallery.Image> gallery(Get get) {
        List<BingGallery.Image> images = first(get, BingGallery.API_FIRST);
        if (!images.isEmpty() && images.get(0).source == Source.BING)
            addUnique(images, BingGallery.parse(body(get, BingGallery.API_SECOND)));
        return images;
    }

    static BingDaily.Image daily(Get get) {
        List<BingGallery.Image> images = first(get, BingDaily.API);
        if (images.isEmpty()) return null;
        BingGallery.Image first = images.get(0);
        return new BingDaily.Image(first.startDate, first.fullUrl, "bing-" + first.startDate + ".jpg");
    }

    private static void addUnique(List<BingGallery.Image> into, List<BingGallery.Image> more) {
        for (BingGallery.Image image : more) {
            boolean duplicate = false;
            for (BingGallery.Image old : into) if (old.fileName.equals(image.fileName)) duplicate = true;
            if (!duplicate) into.add(image);
        }
    }

    static String sourceLabel(List<BingGallery.Image> images) {
        return !images.isEmpty() && images.get(0).source == Source.PEAPIX ? "peapix（必应镜像）" : "Bing";
    }

    static String subtitle(List<BingGallery.Image> images) {
        return "最近 " + images.size() + " 张 · 来源 " + sourceLabel(images);
    }
}
