package com.gathin.tvhome;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.file.Files;
import java.io.IOException;
import java.util.*;
import static org.junit.Assert.*;

/** Failure inventory before implementation: empty/invalid/zero-image/failed HTTP metadata;
 * fallback invoked early or twice; mixed gallery pages; mirror dates cross-matched to Bing;
 * unsafe hosts, schemes, traversal, encoded/query tricks; lost titles/cache/prune protection. */
public class WallpaperSourcesTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    static final String HASH = "5a32350b396b4cbab32bb6ae5088b3da";
    static final String FULL = "https://img.peapix.com/" + HASH + "_1920.jpg";
    static final String THUMB = "https://img.peapix.com/" + HASH + "_640.jpg";
    static final String NAME = "bing-20261022-peapix-" + HASH + ".jpg";
    static final String FEED = "[{\"title\":\"\\u5185藏山国立公园的秋景，韩国\",\"copyright\":\"© Twenty47studio/Getty Image\","
            + "\"fullUrl\":\"" + FULL + "\",\"thumbUrl\":\"" + THUMB + "\","
            + "\"imageUrl\":\"https://img.peapix.com/" + HASH + ".jpg\","
            + "\"pageUrl\":\"https://peapix.com/bing/58031\",\"date\":\"2026-10-22\"}]";

    @Test public void parsesMirrorOwnDateTitleUrlsSourceAndFilename() {
        BingGallery.Image i = WallpaperSources.parsePeapix(FEED).get(0);
        assertEquals("20261022", i.startDate); assertEquals("2026-10-22", i.dateLabel());
        assertEquals("内藏山国立公园的秋景，韩国", i.title);
        assertEquals(FULL, i.fullUrl); assertEquals(THUMB, i.thumbUrl);
        assertEquals(WallpaperSources.Source.PEAPIX, i.source); assertEquals(NAME, i.fileName);
    }
    @Test public void rejectsBadMirrorBodiesDatesUrlsAndMissingFields() {
        for (String body : Arrays.asList(null, "", "not json", "{}", "[]", "[null]",
                FEED.replace("2026-10-22", "2026-02-30"), FEED.replace("2026-10-22", "20261022"),
                FEED.replace(FULL, "http://img.peapix.com/" + HASH + ".jpg"),
                FEED.replace(THUMB, "https://evil.example/a.jpg"), FEED.replace("fullUrl", "missing")))
            assertTrue(String.valueOf(body), WallpaperSources.parsePeapix(body).isEmpty());
    }
    @Test public void bingSuccessNeverCallsMirrorAndKeepsPartialGallery() {
        List<String> calls = new ArrayList<>();
        List<BingGallery.Image> result = WallpaperSources.gallery(url -> {
            calls.add(url); return url.equals(BingGallery.API_FIRST) ? BingDailyTest.SAMPLE : "";
        });
        assertEquals(Arrays.asList(BingGallery.API_FIRST, BingGallery.API_SECOND), calls);
        assertEquals(1, result.size()); assertEquals(WallpaperSources.Source.BING, result.get(0).source);
        assertEquals("最近 1 张 · 来源 Bing", WallpaperSources.subtitle(result));
    }
    @Test public void fullBingGalleryKeepsSixteenAndNoMirror() {
        List<BingGallery.Image> result = WallpaperSources.gallery(url -> {
            assertNotEquals(WallpaperSources.PEAPIX_API, url);
            StringJoiner rows = new StringJoiner(",", "{\"images\":[", "]}");
            for (int n = 0; n < 8; n++) rows.add("{\"startdate\":\"20261021\",\"urlbase\":\"/th?id=OHR."
                    + (url.equals(BingGallery.API_FIRST) ? "First" : "Second") + n + "\"}");
            return rows.toString();
        });
        assertEquals(16, result.size()); assertEquals("最近 16 张 · 来源 Bing", WallpaperSources.subtitle(result));
    }
    @Test public void unusableBingFallsBackSequentiallyWithoutSecondPage() {
        for (String bad : Arrays.asList(null, "", "  ", "oops", "{}", "{\"images\":[]}")) {
            List<String> calls = new ArrayList<>();
            List<BingGallery.Image> result = WallpaperSources.gallery(url -> {
                calls.add(url); return url.equals(BingGallery.API_FIRST) ? bad : FEED;
            });
            assertEquals(Arrays.asList(BingGallery.API_FIRST, WallpaperSources.PEAPIX_API), calls);
            assertEquals(1, result.size()); assertEquals(WallpaperSources.Source.PEAPIX, result.get(0).source);
            assertEquals("最近 1 张 · 来源 peapix（必应镜像）", WallpaperSources.subtitle(result));
        }
    }
    @Test public void thrownNetworkErrorFallsBackAndBothFailuresAreEmpty() {
        assertEquals(1, WallpaperSources.gallery(url -> {
            if (url.equals(BingGallery.API_FIRST)) throw new IOException("offline"); return FEED;
        }).size());
        assertTrue(WallpaperSources.gallery(url -> null).isEmpty()); // Net maps non-200 to null.
        assertTrue(WallpaperSources.gallery(url -> { throw new IOException("offline"); }).isEmpty());
    }
    @Test public void dailyUsesOneBingRequestOrNewestMirrorAndOwnDate() {
        List<String> calls = new ArrayList<>();
        BingDaily.Image bing = WallpaperSources.daily(url -> { calls.add(url); return BingDailyTest.SAMPLE; });
        assertEquals(Collections.singletonList(BingDaily.API), calls);
        assertEquals("bing-20261021.jpg", bing.fileName);
        calls.clear();
        BingDaily.Image mirror = WallpaperSources.daily(url -> {
            calls.add(url); return url.equals(BingDaily.API) ? "" : FEED;
        });
        assertEquals(Arrays.asList(BingDaily.API, WallpaperSources.PEAPIX_API), calls);
        assertEquals("bing-20261022.jpg", mirror.fileName); assertEquals(FULL, mirror.url);
        assertNull(WallpaperSources.daily(url -> "[]"));
    }
    @Test public void validatesExactImageHostsPathsAndSchemes() {
        for (String url : Arrays.asList(FULL, THUMB, "https://img.peapix.com/" + HASH + ".jpg",
                "https://www.bing.com/th?id=OHR.A_1920x1080.jpg")) assertTrue(url, WallpaperSources.safeImageUrl(url));
        for (String url : Arrays.asList(null, "http://www.bing.com/th?id=OHR.A.jpg", FULL.replace("https:", "http:"),
                FULL + "?x=1", FULL + "#x", FULL.replace("img.peapix.com", "evil.example"),
                FULL.replace("img.peapix.com", "img.peapix.com.evil.example"), FULL.replace("/" + HASH, "/../" + HASH),
                FULL.replace("/" + HASH, "/%2e%2e/" + HASH), FULL.replace("_1920", "_1080"),
                FULL.replace("img.peapix.com", "user@img.peapix.com"), FULL.replace("img.peapix.com", "img.peapix.com:443"),
                "https://www.bing.com/../a.jpg", "https://www.bing.com/th?id=OHR.A.jpg&url=https://evil.example",
                "https://www.bing.com/th?id=OHR.%2e%2e.jpg", "https://www.bing.com/th?id=OHR.A..jpg"))
            assertFalse(String.valueOf(url), WallpaperSources.safeImageUrl(url));
    }
    @Test public void mirrorNamesWorkWithTitlesCacheAndMixedPruning() throws Exception {
        File dir = temp.newFolder(); Files.write(new File(dir, NAME).toPath(), new byte[]{1});
        assertTrue(BingGallery.alreadyDownloaded(dir, NAME));
        WallpaperCollection.setTitle(dir, NAME, "内藏山");
        assertEquals("必应 · 10月22日 · 内藏山", WallpaperCollection.displayName(NAME, WallpaperCollection.title(dir, NAME), true));
        assertEquals("必应 · 10月22日 · peapix", WallpaperCollection.displayName(NAME, null, true));
        List<String> names = new ArrayList<>(Arrays.asList(NAME, "user.jpg", "bing-20261001.jpg"));
        for (int day = 10; day < 17; day++) names.add("bing-202610" + day + "-A.jpg");
        assertEquals(Arrays.asList("bing-20261012-A.jpg", "bing-20261011-A.jpg"), BingGallery.stale(names, "bing-20261010-A.jpg"));
        assertEquals(Arrays.asList("bing-20261011-A.jpg", "bing-20261010-A.jpg"), BingGallery.stale(names, NAME));
    }
}
