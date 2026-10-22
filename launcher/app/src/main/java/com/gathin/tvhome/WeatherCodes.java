package com.gathin.tvhome;

/** WMO weather interpretation codes (Open-Meteo weather_code) to a short label and an icon kind. Pure Java. */
final class WeatherCodes {
    static final int NONE = 0, SUN = 1, PARTLY = 2, CLOUD = 3, FOG = 4, RAIN = 5, SNOW = 6, THUNDER = 7;

    private WeatherCodes() { }

    /** Short label, Chinese or English; empty for a code WMO does not define. */
    static String label(int code, boolean chinese) {
        switch (code) {
            case 0: case 1: return chinese ? "晴" : "Clear";
            case 2: return chinese ? "多云" : "Cloudy";
            case 3: return chinese ? "阴" : "Overcast";
            case 45: case 48: return chinese ? "雾" : "Fog";
            case 51: case 53: case 55: return chinese ? "毛毛雨" : "Drizzle";
            case 56: case 57: case 66: case 67: return chinese ? "冻雨" : "Freezing rain";
            case 61: return chinese ? "小雨" : "Light rain";
            case 63: return chinese ? "中雨" : "Rain";
            case 65: return chinese ? "大雨" : "Heavy rain";
            case 71: return chinese ? "小雪" : "Light snow";
            case 73: return chinese ? "中雪" : "Snow";
            case 75: return chinese ? "大雪" : "Heavy snow";
            case 77: return chinese ? "雪" : "Snow grains";
            case 80: case 81: return chinese ? "阵雨" : "Showers";
            case 82: return chinese ? "强阵雨" : "Heavy showers";
            case 85: case 86: return chinese ? "阵雪" : "Snow showers";
            case 95: return chinese ? "雷阵雨" : "Thunderstorm";
            case 96: case 99: return chinese ? "雷阵雨伴冰雹" : "Thunderstorm, hail";
            default: return "";
        }
    }

    static int icon(int code) {
        switch (code) {
            case 0: case 1: return SUN;
            case 2: return PARTLY;
            case 3: return CLOUD;
            case 45: case 48: return FOG;
            case 51: case 53: case 55: case 56: case 57: case 61: case 63: case 65: case 66: case 67:
            case 80: case 81: case 82: return RAIN;
            case 71: case 73: case 75: case 77: case 85: case 86: return SNOW;
            case 95: case 96: case 99: return THUNDER;
            default: return NONE;
        }
    }
}
