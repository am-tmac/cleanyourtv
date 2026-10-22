package com.gathin.tvhome;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import static org.junit.Assert.*;

/** Failures specified before implementation: traversal, outside symlinks, hidden metadata,
 * directories, non-images, empty/large images, malformed dates, lost titles, wrong choice reset,
 * failed deletes and stale titles after gallery pruning. */
public class WallpaperCollectionTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private File image(File dir, String name) throws Exception {
        File file = new File(dir, name);
        Files.write(file.toPath(), new byte[]{1, 2, 3});
        return file;
    }
    @Test public void listsOnlyCollectionImagesIncludingEmptyAndLargeForCleanup() throws Exception {
        File dir = temp.newFolder();
        image(dir, "venice.jpg"); image(dir, "中文.PNG"); image(dir, ".hidden.jpg");
        image(dir, ".titles.properties"); image(dir, "x.part");
        assertTrue(new File(dir, "folder.jpg").mkdir());
        assertTrue(new File(dir, "empty.webp").createNewFile());
        assertEquals(3, WallpaperCollection.images(dir).size());
        assertTrue(WallpaperCollection.images(null).isEmpty());
    }
    @Test public void rejectsTraversalAndInvalidNames() throws Exception {
        File dir = temp.newFolder();
        for (String name : Arrays.asList("../x.jpg", "a/b.jpg", "a\\b.jpg", "/x.jpg", ".x.jpg", "..", "x.txt", ""))
            assertNull(name, WallpaperCollection.resolve(dir, name));
        assertNull(WallpaperCollection.resolve(dir, null));
        assertNull(WallpaperCollection.resolve(null, "x.jpg"));
        assertEquals(new File(dir, "safe.jpg").getCanonicalFile(), WallpaperCollection.resolve(dir, "safe.jpg"));
    }
    @Test public void rejectsSymlinkOutsideCollection() throws Exception {
        File dir = temp.newFolder();
        File outside = temp.newFile("outside.jpg");
        Files.createSymbolicLink(new File(dir, "escape.jpg").toPath(), outside.toPath());
        assertNull(WallpaperCollection.resolve(dir, "escape.jpg"));
        assertTrue(WallpaperCollection.images(dir).isEmpty());
        assertFalse(WallpaperCollection.delete(dir, "escape.jpg"));
        assertTrue(outside.exists());
    }
    @Test public void formatsStoredChineseTitleAndDate() {
        assertEquals("必应 · 10月16日 · 法国肖维岩洞", WallpaperCollection.displayName(
                "bing-20261016-ChauvetPaintingsFrance_ZH-CN3150703955.jpg", "法国肖维岩洞", true));
    }
    @Test public void formatsOldBingCamelCaseAcronymsAndSuffix() {
        assertEquals("必应 · 10月16日 · Chauvet Paintings France", WallpaperCollection.displayName(
                "bing-20261016-ChauvetPaintingsFrance_ZH-CN3150703955.jpg", null, true));
        assertEquals("Bing · 1/2 · USA River", WallpaperCollection.displayName("bing-20260102-USARiver_EN-US123.jpg", " ", false));
    }
    @Test public void formatsUserNamesAndInvalidDatesWithoutCrashing() {
        assertEquals("venice", WallpaperCollection.displayName("venice.jpg", null, true));
        assertEquals("my.photo", WallpaperCollection.displayName("my.photo.PNG", null, true));
        assertEquals("bing-20260230-Test", WallpaperCollection.displayName("bing-20260230-Test.jpg", null, true));
    }
    @Test public void clearsOnlyDeletedCurrentChoice() {
        assertNull(WallpaperCollection.choiceAfterDelete("venice.jpg", "venice.jpg"));
        for (String choice : Arrays.asList("other.jpg", "none", "bing"))
            assertEquals(choice, WallpaperCollection.choiceAfterDelete(choice, "venice.jpg"));
        assertNull(WallpaperCollection.choiceAfterDelete(null, "venice.jpg"));
    }
    @Test public void persistsAddsReplacesAndRemovesTitles() throws Exception {
        File dir = temp.newFolder();
        WallpaperCollection.setTitle(dir, "a.jpg", "山川");
        WallpaperCollection.setTitle(dir, "b.jpg", "大海");
        assertEquals("山川", WallpaperCollection.title(dir, "a.jpg"));
        WallpaperCollection.setTitle(dir, "a.jpg", "新标题");
        assertEquals("新标题", WallpaperCollection.title(dir, "a.jpg"));
        WallpaperCollection.setTitle(dir, "a.jpg", null);
        assertNull(WallpaperCollection.title(dir, "a.jpg"));
        assertEquals("大海", WallpaperCollection.title(dir, "b.jpg"));
    }
    @Test public void deleteRemovesFileAndTitleButPreservesOtherTitles() throws Exception {
        File dir = temp.newFolder(); image(dir, "a.jpg"); image(dir, "b.jpg");
        WallpaperCollection.setTitle(dir, "a.jpg", "A"); WallpaperCollection.setTitle(dir, "b.jpg", "B");
        assertTrue(WallpaperCollection.delete(dir, "a.jpg"));
        assertFalse(new File(dir, "a.jpg").exists());
        assertNull(WallpaperCollection.title(dir, "a.jpg"));
        assertEquals("B", WallpaperCollection.title(dir, "b.jpg"));
        assertFalse(WallpaperCollection.delete(dir, "a.jpg"));
    }
    @Test public void refusesMissingDirectoriesAndUnsafeTitleKeys() throws Exception {
        File dir = temp.newFolder();
        assertFalse(WallpaperCollection.delete(dir, "folder.jpg"));
        try { WallpaperCollection.setTitle(dir, "../x.jpg", "bad"); fail(); }
        catch (java.io.IOException expected) { }
        assertNull(WallpaperCollection.title(dir, "../x.jpg"));
    }
    @Test public void galleryPruneUsesDeletionWithTitleCleanup() throws Exception {
        File dir = temp.newFolder();
        java.util.List<String> names = new java.util.ArrayList<>();
        for (int day = 10; day <= 18; day++) {
            String name = "bing-202610" + day + "-A.jpg";
            image(dir, name); names.add(name); WallpaperCollection.setTitle(dir, name, "标题");
        }
        for (String name : BingGallery.stale(names, names.get(0))) assertTrue(WallpaperCollection.delete(dir, name));
        assertEquals(6, WallpaperCollection.images(dir).size());
        assertNotNull(WallpaperCollection.title(dir, names.get(0)));
        assertNull(WallpaperCollection.title(dir, names.get(1)));
    }
}
