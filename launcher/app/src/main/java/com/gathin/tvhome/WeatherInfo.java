package com.gathin.tvhome;

import java.util.Locale;
import java.util.Map;

/** Parses the IP location and Open-Meteo replies and formats the weather line. Pure Java. */
final class WeatherInfo {
    private WeatherInfo() { }

    static final class Location {
        final String city;
        final double lat, lon;

        Location(String city, double lat, double lon) {
            this.city = city;
            this.lat = lat;
            this.lon = lon;
        }
    }

    static final class Current {
        final int temp, code;

        Current(int temp, int code) {
            this.temp = temp;
            this.code = code;
        }
    }

    /**
     * Reads ipwho.is ({@code success, city, latitude, longitude}), ip-api.com ({@code status, city, lat, lon}),
     * ipapi.co ({@code city, latitude, longitude}) and ipinfo.io ({@code city, loc "lat,lon"}). Null on a failure
     * reply, an HTML challenge page or coordinates out of range.
     */
    static Location parseLocation(String body) {
        Map<String, Object> m = MiniJson.object(MiniJson.parse(body));
        if (m == null) return null;
        if (Boolean.FALSE.equals(m.get("success"))) return null;
        String status = MiniJson.string(m, "status");
        if (status != null && !"success".equals(status)) return null;
        if (Boolean.TRUE.equals(m.get("error"))) return null;
        double lat = MiniJson.number(m, "latitude"), lon = MiniJson.number(m, "longitude");
        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            lat = MiniJson.number(m, "lat");
            lon = MiniJson.number(m, "lon");
        }
        String loc = MiniJson.string(m, "loc");
        if ((Double.isNaN(lat) || Double.isNaN(lon)) && loc != null) {
            int comma = loc.indexOf(',');
            try {
                if (comma > 0) {
                    lat = Double.parseDouble(loc.substring(0, comma).trim());
                    lon = Double.parseDouble(loc.substring(comma + 1).trim());
                }
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (Double.isNaN(lat) || Double.isNaN(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) return null;
        return new Location(cleanCity(MiniJson.string(m, "city")), lat, lon);
    }

    /** Trims, drops a trailing 市 (厦门市 to 厦门, but not from a two-character name) and caps the length. */
    static String cleanCity(String city) {
        if (city == null) return "";
        String c = city.trim();
        if (c.length() > 2 && c.endsWith("市")) c = c.substring(0, c.length() - 1);
        return c.length() > 12 ? c.substring(0, 12) : c;
    }

    /** Open-Meteo {@code current.temperature_2m} (rounded) and {@code current.weather_code}; null if either is missing. */
    static Current parseCurrent(String body) {
        Map<String, Object> m = MiniJson.object(MiniJson.parse(body));
        Map<String, Object> current = m == null ? null : MiniJson.object(m.get("current"));
        if (current == null) return null;
        double t = MiniJson.number(current, "temperature_2m"), code = MiniJson.number(current, "weather_code");
        if (Double.isNaN(t) || Double.isNaN(code) || Math.abs(t) > 100) return null;
        return new Current((int) Math.round(t), (int) code);
    }

    /** "厦门 · 晴 26°"; the city or the label may be empty. */
    static String line(String city, String label, int temp) {
        String weather = (label == null || label.isEmpty() ? "" : label + " ") + temp + "°";
        return city == null || city.isEmpty() ? weather : city + " · " + weather;
    }

    static String forecastUrl(double lat, double lon) {
        return String.format(Locale.ROOT, "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                + "&current=temperature_2m,weather_code&timezone=auto", lat, lon);
    }
}
