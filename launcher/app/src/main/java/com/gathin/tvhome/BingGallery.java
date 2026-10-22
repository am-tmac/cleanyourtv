package com.gathin.tvhome;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** On-demand gallery metadata and storage policy. No Android or network dependencies. */
final class BingGallery {
    static final String API_FIRST = "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=zh-CN";
    static final String API_SECOND = "https://www.bing.com/HPImageArchive.aspx?format=js&idx=8&n=8&mkt=zh-CN";
    private static final Pattern BASE = Pattern.compile("/th\\?id=OHR\\.[A-Za-z0-9_.%-]+(?:_[A-Za-z0-9_-]+)?");
    private static final Pattern FILE = Pattern.compile("bing-\\d{8}-[A-Za-z0-9_-]+\\.jpg");

    private BingGallery() { }

    static final class Image {
        final String startDate, title, fileName, url, fullUrl, thumbUrl;
        final WallpaperSources.Source source;
        Image(String date, String title, String base) {
            this(date, title, "bing-" + date + "-" + sanitize(base.substring("/th?id=OHR.".length())) + ".jpg",
                    BingDaily.imageUrl(base), thumbnailUrl(base), WallpaperSources.Source.BING);
        }
        Image(String date, String title, String fileName, String fullUrl, String thumbUrl, WallpaperSources.Source source) {
            this.startDate = date;
            this.title = title;
            this.fileName = fileName;
            this.fullUrl = fullUrl;
            this.url = fullUrl;
            this.thumbUrl = thumbUrl;
            this.source = source;
        }
        String dateLabel() { return LocalDate.parse(startDate, DateTimeFormatter.BASIC_ISO_DATE).toString(); }
    }

    static List<Image> parse(String body) {
        Map<String, Object> root = MiniJson.object(MiniJson.parse(body));
        List<?> rows = root == null ? null : MiniJson.array(root.get("images"));
        List<Image> result = new ArrayList<>();
        if (rows == null) return result;
        for (Object row : rows) {
            Map<String, Object> m = MiniJson.object(row);
            String date = MiniJson.string(m, "startdate");
            String base = MiniJson.string(m, "urlbase");
            if (date == null || !date.matches("\\d{8}") || thumbnailUrl(base) == null) continue;
            try { LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE); }
            catch (DateTimeParseException e) { continue; }
            String title = MiniJson.string(m, "copyright");
            if (title != null) {
                int credit = title.indexOf(" (©");
                if (credit >= 0) title = title.substring(0, credit);
                title = title.trim();
            }
            if (title == null || title.isEmpty()) title = MiniJson.string(m, "title");
            if (title == null || title.isEmpty()) title = date;
            Image image = new Image(date, title, base);
            if (!FILE.matcher(image.fileName).matches() || image.fileName.length() > 240) continue;
            boolean duplicate = false;
            for (Image old : result) if (old.fileName.equals(image.fileName)) duplicate = true;
            if (!duplicate) result.add(image);
            if (result.size() == 8) break;
        }
        return result;
    }

    static String thumbnailUrl(String base) {
        String full = BingDaily.imageUrl(base);
        if (full == null || !BASE.matcher(base).matches()) return null;
        return full.substring(0, full.length() - "_1920x1080.jpg".length()) + "_400x240.jpg";
    }

    static String sanitize(String name) { return name.replaceAll("[^A-Za-z0-9_-]", ""); }

    /** Keep the chosen file plus the newest remaining dates, at most six picked files total. */
    static List<String> stale(List<String> names, String chosen) {
        List<String> ours = new ArrayList<>();
        for (String name : names) if (FILE.matcher(name).matches()) ours.add(name);
        Collections.sort(ours, Collections.reverseOrder());
        boolean protectedChoice = ours.remove(chosen);
        int slots = protectedChoice ? 5 : 6;
        return ours.size() <= slots ? Collections.emptyList() : new ArrayList<>(ours.subList(slots, ours.size()));
    }

    static boolean alreadyDownloaded(File dir, String name) {
        if (dir == null || name == null || !FILE.matcher(name).matches()) return false;
        File file = new File(dir, name);
        return file.isFile() && file.length() > 0;
    }
}
