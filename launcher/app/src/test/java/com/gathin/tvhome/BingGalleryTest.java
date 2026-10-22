package com.gathin.tvhome;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

/** Failure cases written before implementation: malformed/empty replies, multiple records, invalid
 * dates/hosts/IDs, copyright fallback, unsafe filenames, pruning chosen/user files, empty cache files. */
public class BingGalleryTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private String row(String date, String base, String copyright) {
        return "{\"startdate\":\"" + date + "\",\"urlbase\":\"" + base
                + "\",\"copyright\":\"" + copyright + "\",\"title\":\"备用标题\"}";
    }

    @Test public void parsesEightImagesAndExtractsChineseTitleAndDate() {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < 8; i++) rows.add(row("202610" + (22-i), "/th?id=OHR.Image" + i + "_ZH-CN123", "山川 " + i + " (© Credit)"));
        List<BingGallery.Image> images = BingGallery.parse("{\"images\":[" + String.join(",", rows) + "]}");
        assertEquals(8, images.size());
        assertEquals("山川 0", images.get(0).title);
        assertEquals("20261022", images.get(0).startDate);
        assertEquals("2026-10-22", images.get(0).dateLabel());
        assertEquals("bing-20261022-Image0_ZH-CN123.jpg", images.get(0).fileName);
    }

    @Test public void rejectsMalformedEmptyAndUnsafeRows() {
        for (String body : Arrays.asList(null, "<html>", "{}", "{\"images\":[]}")) assertTrue(BingGallery.parse(body).isEmpty());
        for (String base : Arrays.asList("//evil/x", "https://evil/x", "/th?id=Other", "/th?id=OHR.", "/th?id=OHR.A&x=1", "/th?id=OHR.A b"))
            assertTrue(BingGallery.parse("{\"images\":[" + row("20261022", base, "x") + "]}").isEmpty());
        for (String date : Arrays.asList("../../xx", "2026-10", "20260230"))
            assertTrue(BingGallery.parse("{\"images\":[" + row(date, "/th?id=OHR.A", "x") + "]}").isEmpty());
    }

    @Test public void fallsBackToTitleAndSkipsInvalidRows() {
        List<BingGallery.Image> images = BingGallery.parse("{\"images\":[null," + row("20261022", "/th?id=OHR.A", "") + "]}");
        assertEquals(1, images.size());
        assertEquals("备用标题", images.get(0).title);
    }

    @Test public void buildsValidatedThumbnailAndFullUrls() {
        assertEquals("https://www.bing.com/th?id=OHR.A_400x240.jpg", BingGallery.thumbnailUrl("/th?id=OHR.A"));
        assertNull(BingGallery.thumbnailUrl("//evil/x"));
        BingGallery.Image image = BingGallery.parse("{\"images\":[" + row("20261022", "/th?id=OHR.A", "x") + "]}").get(0);
        assertEquals(BingDaily.imageUrl("/th?id=OHR.A"), image.url);
    }

    @Test public void sanitizesNamesWithoutTraversal() {
        assertEquals("AB_C-12", BingGallery.sanitize("../A中B_C-12?%"));
        assertEquals("", BingGallery.sanitize("../中文"));
    }

    @Test public void pruningKeepsSixAndOldChosenAndIgnoresUserFiles() {
        List<String> names = new ArrayList<>();
        for (int i = 10; i <= 18; i++) names.add("bing-202610" + i + "-A.jpg");
        names.addAll(Arrays.asList("user.jpg", "bing-personal.jpg", "bing-20261001.jpg", "bing-20261001-A.jpg.part"));
        assertEquals(Arrays.asList("bing-20261013-A.jpg", "bing-20261012-A.jpg", "bing-20261011-A.jpg"),
                BingGallery.stale(names, "bing-20261010-A.jpg"));
        assertEquals(3, BingGallery.stale(names, "none").size());
        assertTrue(BingGallery.stale(Collections.singletonList("user.jpg"), null).isEmpty());
        assertTrue(BingGallery.stale(names.subList(0, 6), null).isEmpty());
    }

    @Test public void alreadyDownloadedRequiresNonemptyRegularSafeFile() throws Exception {
        File dir = temp.newFolder();
        String name = "bing-20261022-A.jpg";
        assertFalse(BingGallery.alreadyDownloaded(dir, name));
        File f = new File(dir, name);
        assertTrue(f.createNewFile());
        assertFalse(BingGallery.alreadyDownloaded(dir, name));
        Files.write(f.toPath(), new byte[] {1,2,3});
        assertTrue(BingGallery.alreadyDownloaded(dir, name));
        assertFalse(BingGallery.alreadyDownloaded(dir, "../" + name));
        assertFalse(BingGallery.alreadyDownloaded(null, name));
        assertTrue(new File(dir, "bing-20261021-B.jpg").mkdir());
        assertFalse(BingGallery.alreadyDownloaded(dir, "bing-20261021-B.jpg"));
    }
}
