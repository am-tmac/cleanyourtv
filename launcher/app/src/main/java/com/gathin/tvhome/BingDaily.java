package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Bing's daily image: the HPImageArchive reply, the 1920x1080 download URL and which old files to delete. Pure Java. */
final class BingDaily {
    static final String API = "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=zh-CN";
    private static final Pattern DATE = Pattern.compile("\\d{8}");
    private static final Pattern URLBASE = Pattern.compile("/[A-Za-z0-9/?=._&%-]+");
    private static final Pattern FILE = Pattern.compile("bing-\\d{8}\\.jpg");

    private BingDaily() { }

    static final class Image {
        final String startDate, url, fileName;

        Image(String startDate, String url, String fileName) {
            this.startDate = startDate;
            this.url = url;
            this.fileName = fileName;
        }
    }

    static Image parse(String body) {
        Map<String, Object> m = MiniJson.object(MiniJson.parse(body));
        List<?> images = m == null ? null : MiniJson.array(m.get("images"));
        if (images == null || images.isEmpty()) return null;
        Map<String, Object> first = MiniJson.object(images.get(0));
        String date = MiniJson.string(first, "startdate");
        String url = imageUrl(MiniJson.string(first, "urlbase"));
        if (date == null || !DATE.matcher(date).matches() || url == null) return null;
        return new Image(date, url, "bing-" + date + ".jpg");
    }

    /** Only a path on www.bing.com; anything else (another host, a protocol-relative URL, spaces) is refused. */
    static String imageUrl(String urlbase) {
        if (urlbase == null || urlbase.startsWith("//") || !URLBASE.matcher(urlbase).matches()) return null;
        String url = "https://www.bing.com" + urlbase + "_1920x1080.jpg";
        return WallpaperSources.safeImageUrl(url) ? url : null;
    }

    /** Downloaded Bing files beyond the newest {@code keep}, newest first; other files are never listed. */
    static List<String> stale(List<String> names, int keep) {
        List<String> ours = new ArrayList<>();
        for (String n : names) if (FILE.matcher(n).matches()) ours.add(n);
        Collections.sort(ours, Collections.reverseOrder());
        return ours.size() <= keep ? Collections.emptyList() : new ArrayList<>(ours.subList(keep, ours.size()));
    }

    static String newest(List<String> names) {
        String best = null;
        for (String n : names) {
            if (FILE.matcher(n).matches() && (best == null || n.compareTo(best) > 0)) best = n;
        }
        return best;
    }
}
