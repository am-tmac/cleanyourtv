package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A small strict JSON reader for the weather and Bing replies. Pure Java (android.jar's org.json is a stub in
 * JVM tests). Any malformed input, an HTML challenge page or nesting deeper than 32 levels gives null.
 * Objects are Map, arrays List, numbers Double, plus String, Boolean and null.
 */
final class MiniJson {
    private static final int MAX_DEPTH = 32;
    private final String s;
    private int i;

    private MiniJson(String s) { this.s = s; }

    private static final class Bad extends Exception {
        Bad() { super(null, null, false, false); }
    }

    static Object parse(String text) {
        if (text == null) return null;
        MiniJson p = new MiniJson(text);
        try {
            p.ws();
            Object v = p.value(0);
            p.ws();
            return p.i == text.length() ? v : null;
        } catch (Bad | RuntimeException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object v) {
        return v instanceof Map ? (Map<String, Object>) v : null;
    }

    static List<?> array(Object v) {
        return v instanceof List ? (List<?>) v : null;
    }

    static String string(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        return v instanceof String ? (String) v : null;
    }

    /** The number at key, or NaN. */
    static double number(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        return v instanceof Double ? (Double) v : Double.NaN;
    }

    private void ws() {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') i++;
            else break;
        }
    }

    private Object value(int depth) throws Bad {
        if (depth > MAX_DEPTH || i >= s.length()) throw new Bad();
        char c = s.charAt(i);
        if (c == '{') return obj(depth);
        if (c == '[') return arr(depth);
        if (c == '"') return str();
        if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
        if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
        if (s.startsWith("null", i)) { i += 4; return null; }
        return num();
    }

    private Map<String, Object> obj(int depth) throws Bad {
        Map<String, Object> m = new HashMap<>();
        i++;
        ws();
        if (peek() == '}') { i++; return m; }
        while (true) {
            ws();
            if (peek() != '"') throw new Bad();
            String key = str();
            ws();
            if (peek() != ':') throw new Bad();
            i++;
            ws();
            m.put(key, value(depth + 1));
            ws();
            char c = peek();
            i++;
            if (c == '}') return m;
            if (c != ',') throw new Bad();
        }
    }

    private List<Object> arr(int depth) throws Bad {
        List<Object> l = new ArrayList<>();
        i++;
        ws();
        if (peek() == ']') { i++; return l; }
        while (true) {
            ws();
            l.add(value(depth + 1));
            ws();
            char c = peek();
            i++;
            if (c == ']') return l;
            if (c != ',') throw new Bad();
        }
    }

    private char peek() throws Bad {
        if (i >= s.length()) throw new Bad();
        return s.charAt(i);
    }

    private String str() throws Bad {
        i++;
        StringBuilder b = new StringBuilder();
        while (true) {
            char c = peek();
            i++;
            if (c == '"') return b.toString();
            if (c < 0x20) throw new Bad();
            if (c != '\\') { b.append(c); continue; }
            char e = peek();
            i++;
            switch (e) {
                case '"': b.append('"'); break;
                case '\\': b.append('\\'); break;
                case '/': b.append('/'); break;
                case 'b': b.append('\b'); break;
                case 'f': b.append('\f'); break;
                case 'n': b.append('\n'); break;
                case 'r': b.append('\r'); break;
                case 't': b.append('\t'); break;
                case 'u':
                    if (i + 4 > s.length()) throw new Bad();
                    try {
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw new Bad();
                    }
                    i += 4;
                    break;
                default: throw new Bad();
            }
        }
    }

    private Double num() throws Bad {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (start == i) throw new Bad();
        try {
            return Double.parseDouble(s.substring(start, i));
        } catch (NumberFormatException e) {
            throw new Bad();
        }
    }
}
