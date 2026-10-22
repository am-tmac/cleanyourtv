package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class BingDailyTest {
    static final String SAMPLE = "{\"images\":[{\"startdate\":\"20261021\",\"fullstartdate\":\"202610211600\",\"enddate\":\"20261022\","
            + "\"url\":\"/th?id=OHR.NaejangsanNP_ZH-CN6538893004_1920x1080.jpg&rf=LaDigue_1920x1080.jpg&pid=hp\","
            + "\"urlbase\":\"/th?id=OHR.NaejangsanNP_ZH-CN6538893004\",\"copyright\":\"内藏山国立公园的秋景\",\"title\":\"静水映斑斓\"}],"
            + "\"tooltips\":{\"loading\":\"正在加载...\"}}";

    @Test public void parsesFirstImage() {
        BingDaily.Image img = BingDaily.parse(SAMPLE);
        assertNotNull(img);
        assertEquals("20261021", img.startDate);
        assertEquals("https://www.bing.com/th?id=OHR.NaejangsanNP_ZH-CN6538893004_1920x1080.jpg", img.url);
        assertEquals("bing-20261021.jpg", img.fileName);
    }

    @Test public void rejectsForeignHostsAndBadDates() {
        assertNull(BingDaily.parse("{\"images\":[{\"startdate\":\"20261021\",\"urlbase\":\"//evil.example/x\"}]}"));
        assertNull(BingDaily.parse("{\"images\":[{\"startdate\":\"20261021\",\"urlbase\":\"https://evil.example/x\"}]}"));
        assertNull(BingDaily.parse("{\"images\":[{\"startdate\":\"2026-10\",\"urlbase\":\"/th?id=OHR.A\"}]}"));
        assertNull(BingDaily.parse("{\"images\":[{\"startdate\":\"../../x\",\"urlbase\":\"/th?id=OHR.A\"}]}"));
        assertNull(BingDaily.parse("{\"images\":[]}"));
        assertNull(BingDaily.parse("<html>"));
    }

    @Test public void imageUrl() {
        assertEquals("https://www.bing.com/th?id=OHR.A_1920x1080.jpg", BingDaily.imageUrl("/th?id=OHR.A"));
        assertNull(BingDaily.imageUrl("/th?id=OHR.A b"));
        assertNull(BingDaily.imageUrl(""));
    }

    @Test public void staleKeepsNewestTwo() {
        assertEquals(Arrays.asList("bing-20261019.jpg", "bing-20261018.jpg"),
                BingDaily.stale(Arrays.asList("bing-20261020.jpg", "bing-20261018.jpg", "bing-20261021.jpg",
                        "bing-20261019.jpg", "other.jpg", "bing-x.jpg.part"), 2));
        assertEquals(Collections.emptyList(), BingDaily.stale(Arrays.asList("bing-20261020.jpg"), 2));
    }

    @Test public void newest() {
        assertEquals("bing-20261021.jpg", BingDaily.newest(Arrays.asList("bing-20261020.jpg", "bing-20261021.jpg", "zz.jpg")));
        assertNull(BingDaily.newest(Collections.emptyList()));
    }
}
