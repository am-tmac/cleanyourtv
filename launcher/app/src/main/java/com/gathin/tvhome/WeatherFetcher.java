package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.Executor;

/**
 * Weather line data (launcher settings, Weather). Location from the network IP (ipwho.is, then ip-api.com,
 * then ipinfo.io; cached 6 h), current weather from Open-Meteo. At most one attempt per 30 minutes; the last
 * reading is kept in preferences and shown for up to 3 hours. Network calls run on the given executor only.
 */
final class WeatherFetcher {
    static final String PREFS = "weather_line";
    /** ipapi.co is left out: it often answers with a Cloudflare challenge page instead of JSON. */
    private static String[] locationUrls(boolean chinese) {
        String lang = chinese ? "zh-CN" : "en";
        return new String[]{
                "https://ipwho.is/?lang=" + lang,
                // Plain HTTP on the free tier: allowed for this one host in network_security_config.xml.
                "http://ip-api.com/json/?lang=" + lang + "&fields=status,city,lat,lon",
                "https://ipinfo.io/json",
        };
    }

    static final class Reading {
        final String city;
        final int temp, code;

        Reading(String city, int temp, int code) {
            this.city = city;
            this.temp = temp;
            this.code = code;
        }
    }

    private WeatherFetcher() { }

    /** The cached reading, or null when there is none or it is over three hours old. */
    static Reading cached(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!RefreshPolicy.showCached(p.getLong("fetched", 0), System.currentTimeMillis())) return null;
        return new Reading(p.getString("city", ""), p.getInt("temp", 0), p.getInt("code", -1));
    }

    /** Starts a refresh when one is due; {@code done} runs on the executor's thread after a successful one. */
    static boolean refreshIfDue(Context ctx, boolean chinese, Executor executor, Runnable done) {
        Context app = ctx.getApplicationContext();
        SharedPreferences p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (!RefreshPolicy.weatherDue(p.getLong("attempt", 0), now)) return false;
        p.edit().putLong("attempt", now).apply();
        executor.execute(() -> {
            if (fetch(p, chinese)) done.run();
        });
        return true;
    }

    private static boolean fetch(SharedPreferences p, boolean chinese) {
        long now = System.currentTimeMillis();
        String city = p.getString("city", "");
        double lat = Double.longBitsToDouble(p.getLong("lat", 0)), lon = Double.longBitsToDouble(p.getLong("lon", 0));
        if (RefreshPolicy.locationDue(p.getLong("located", 0), now)) {
            WeatherInfo.Location loc = null;
            for (String url : locationUrls(chinese)) {
                loc = WeatherInfo.parseLocation(Net.get(url, 32 * 1024));
                if (loc != null) break;
            }
            if (loc == null && p.getLong("located", 0) == 0) return false;
            if (loc != null) {
                city = loc.city;
                lat = loc.lat;
                lon = loc.lon;
                p.edit().putString("city", city).putLong("lat", Double.doubleToLongBits(lat))
                        .putLong("lon", Double.doubleToLongBits(lon)).putLong("located", now).apply();
            }
        }
        WeatherInfo.Current current = WeatherInfo.parseCurrent(Net.get(WeatherInfo.forecastUrl(lat, lon), 32 * 1024));
        if (current == null) return false;
        p.edit().putInt("temp", current.temp).putInt("code", current.code)
                .putLong("fetched", System.currentTimeMillis()).apply();
        return true;
    }
}
