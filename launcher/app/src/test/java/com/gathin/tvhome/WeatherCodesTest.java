package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WeatherCodesTest {
    @Test public void chineseLabelsForEveryWmoGroup() {
        assertEquals("晴", WeatherCodes.label(0, true));
        assertEquals("晴", WeatherCodes.label(1, true));
        assertEquals("多云", WeatherCodes.label(2, true));
        assertEquals("阴", WeatherCodes.label(3, true));
        assertEquals("雾", WeatherCodes.label(45, true));
        assertEquals("雾", WeatherCodes.label(48, true));
        assertEquals("毛毛雨", WeatherCodes.label(51, true));
        assertEquals("毛毛雨", WeatherCodes.label(55, true));
        assertEquals("冻雨", WeatherCodes.label(56, true));
        assertEquals("小雨", WeatherCodes.label(61, true));
        assertEquals("中雨", WeatherCodes.label(63, true));
        assertEquals("大雨", WeatherCodes.label(65, true));
        assertEquals("冻雨", WeatherCodes.label(67, true));
        assertEquals("小雪", WeatherCodes.label(71, true));
        assertEquals("中雪", WeatherCodes.label(73, true));
        assertEquals("大雪", WeatherCodes.label(75, true));
        assertEquals("雪", WeatherCodes.label(77, true));
        assertEquals("阵雨", WeatherCodes.label(80, true));
        assertEquals("阵雨", WeatherCodes.label(81, true));
        assertEquals("强阵雨", WeatherCodes.label(82, true));
        assertEquals("阵雪", WeatherCodes.label(85, true));
        assertEquals("阵雪", WeatherCodes.label(86, true));
        assertEquals("雷阵雨", WeatherCodes.label(95, true));
        assertEquals("雷阵雨伴冰雹", WeatherCodes.label(96, true));
        assertEquals("雷阵雨伴冰雹", WeatherCodes.label(99, true));
    }

    @Test public void englishLabels() {
        assertEquals("Clear", WeatherCodes.label(0, false));
        assertEquals("Cloudy", WeatherCodes.label(2, false));
        assertEquals("Thunderstorm", WeatherCodes.label(95, false));
    }

    @Test public void unknownCodeHasNoLabel() {
        assertEquals("", WeatherCodes.label(-1, true));
        assertEquals("", WeatherCodes.label(4, true));
        assertEquals("", WeatherCodes.label(100, false));
    }

    @Test public void iconKinds() {
        assertEquals(WeatherCodes.SUN, WeatherCodes.icon(0));
        assertEquals(WeatherCodes.SUN, WeatherCodes.icon(1));
        assertEquals(WeatherCodes.PARTLY, WeatherCodes.icon(2));
        assertEquals(WeatherCodes.CLOUD, WeatherCodes.icon(3));
        assertEquals(WeatherCodes.FOG, WeatherCodes.icon(45));
        assertEquals(WeatherCodes.RAIN, WeatherCodes.icon(53));
        assertEquals(WeatherCodes.RAIN, WeatherCodes.icon(63));
        assertEquals(WeatherCodes.RAIN, WeatherCodes.icon(81));
        assertEquals(WeatherCodes.SNOW, WeatherCodes.icon(73));
        assertEquals(WeatherCodes.SNOW, WeatherCodes.icon(86));
        assertEquals(WeatherCodes.THUNDER, WeatherCodes.icon(95));
        assertEquals(WeatherCodes.NONE, WeatherCodes.icon(4));
    }
}
