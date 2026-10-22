package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class WeatherInfoTest {
    @Test public void ipwhoisChinese() {
        WeatherInfo.Location l = WeatherInfo.parseLocation(
                "{\"ip\":\"1.2.3.4\",\"success\":true,\"city\":\"厦门市\",\"latitude\":24.48,\"longitude\":118.09}");
        assertNotNull(l);
        assertEquals("厦门", l.city);
        assertEquals(24.48, l.lat, 1e-9);
        assertEquals(118.09, l.lon, 1e-9);
    }

    @Test public void ipApiCom() {
        WeatherInfo.Location l = WeatherInfo.parseLocation(
                "{\"status\":\"success\",\"city\":\"葵涌\",\"lat\":22.374,\"lon\":114.1369}");
        assertNotNull(l);
        assertEquals("葵涌", l.city);
        assertEquals(22.374, l.lat, 1e-9);
    }

    @Test public void ipapiCoAndIpinfo() {
        WeatherInfo.Location a = WeatherInfo.parseLocation("{\"city\":\"Xiamen\",\"latitude\":24.4,\"longitude\":118.1}");
        assertEquals("Xiamen", a.city);
        WeatherInfo.Location b = WeatherInfo.parseLocation("{\"city\":\"Xiamen\",\"loc\":\"24.4798,118.0819\"}");
        assertNotNull(b);
        assertEquals(118.0819, b.lon, 1e-9);
    }

    @Test public void failuresAndChallengesAreNull() {
        assertNull(WeatherInfo.parseLocation("{\"success\":false,\"message\":\"rate limited\"}"));
        assertNull(WeatherInfo.parseLocation("{\"status\":\"fail\",\"message\":\"reserved range\"}"));
        assertNull(WeatherInfo.parseLocation("<!DOCTYPE html><title>Just a moment...</title>"));
        assertNull(WeatherInfo.parseLocation("{\"city\":\"X\",\"latitude\":200,\"longitude\":0}"));
        assertNull(WeatherInfo.parseLocation("{\"city\":\"X\"}"));
    }

    @Test public void missingCityStillGivesCoordinates() {
        WeatherInfo.Location l = WeatherInfo.parseLocation("{\"success\":true,\"city\":\"\",\"latitude\":1,\"longitude\":2}");
        assertNotNull(l);
        assertEquals("", l.city);
    }

    @Test public void cleanCity() {
        assertEquals("厦门", WeatherInfo.cleanCity("厦门市"));
        assertEquals("沙市", WeatherInfo.cleanCity("沙市"));   // too short to strip
        assertEquals("Xiamen", WeatherInfo.cleanCity(" Xiamen "));
        assertEquals("", WeatherInfo.cleanCity(null));
        assertEquals(12, WeatherInfo.cleanCity("ABCDEFGHIJKLMNOPQRSTUVWXYZ").length());
    }

    @Test public void openMeteoCurrent() {
        WeatherInfo.Current c = WeatherInfo.parseCurrent("{\"latitude\":24.5,\"current_units\":{\"temperature_2m\":\"°C\",\"weather_code\":\"wmo code\"},"
                + "\"current\":{\"time\":\"2026-10-22T01:45\",\"interval\":900,\"temperature_2m\":26.8,\"weather_code\":1}}");
        assertNotNull(c);
        assertEquals(27, c.temp);
        assertEquals(1, c.code);
    }

    @Test public void openMeteoNegativeRounding() {
        WeatherInfo.Current c = WeatherInfo.parseCurrent("{\"current\":{\"temperature_2m\":-0.4,\"weather_code\":71}}");
        assertEquals(0, c.temp);
        WeatherInfo.Current d = WeatherInfo.parseCurrent("{\"current\":{\"temperature_2m\":-3.6,\"weather_code\":71}}");
        assertEquals(-4, d.temp);
    }

    @Test public void openMeteoErrors() {
        assertNull(WeatherInfo.parseCurrent("{\"error\":true,\"reason\":\"bad\"}"));
        assertNull(WeatherInfo.parseCurrent("{\"current\":{\"temperature_2m\":\"x\",\"weather_code\":1}}"));
        assertNull(WeatherInfo.parseCurrent("{\"current\":{\"temperature_2m\":20}}"));
        assertNull(WeatherInfo.parseCurrent("nope"));
    }

    @Test public void lineFormat() {
        assertEquals("厦门 · 晴 26°", WeatherInfo.line("厦门", "晴", 26));
        assertEquals("晴 26°", WeatherInfo.line("", "晴", 26));
        assertEquals("厦门 · -3°", WeatherInfo.line("厦门", "", -3));
    }

    @Test public void forecastUrl() {
        assertEquals("https://api.open-meteo.com/v1/forecast?latitude=24.4800&longitude=118.0900"
                + "&current=temperature_2m,weather_code&timezone=auto",
                WeatherInfo.forecastUrl(24.48, 118.09));
    }
}
