package com.usmconnect.util;

import java.util.*;

/**
 * Minimal JSON reader/writer.
 *
 * We deliberately avoid third-party libraries (Gson/Jackson) because this
 * sandbox environment cannot reach Maven Central to download them. This
 * parser supports everything USM Connect needs: objects, arrays, strings,
 * numbers, booleans and null.
 *
 * Values are represented using plain Java types:
 *   JSON object -> LinkedHashMap<String, Object>
 *   JSON array  -> ArrayList<Object>
 *   string      -> String
 *   number      -> Double
 *   true/false  -> Boolean
 *   null        -> null
 */
public final class JsonUtil {

    private JsonUtil() {}

    // ---------- Writing ----------

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(value, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            writeString((String) value, sb);
        } else if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                sb.append((long) d);
            } else {
                sb.append(d);
            }
        } else if (value instanceof Boolean) {
            sb.append(value.toString());
        } else if (value instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) value).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeString(e.getKey(), sb);
                sb.append(':');
                writeValue(e.getValue(), sb);
            }
            sb.append('}');
        } else if (value instanceof Iterable) {
            sb.append('[');
            boolean first = true;
            for (Object o : (Iterable<Object>) value) {
                if (!first) sb.append(',');
                first = false;
                writeValue(o, sb);
            }
            sb.append(']');
        } else {
            writeString(value.toString(), sb);
        }
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // ---------- Reading ----------

    public static Object parse(String json) {
        Parser p = new Parser(json);
        p.skipWhitespace();
        Object result = p.parseValue();
        p.skipWhitespace();
        return result;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Object o = parse(json);
        if (o instanceof Map) return (Map<String, Object>) o;
        return new LinkedHashMap<>();
    }

    private static final class Parser {
        final String s;
        int i = 0;

        Parser(String s) { this.s = s == null ? "" : s; }

        void skipWhitespace() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        char peek() { return s.charAt(i); }

        Object parseValue() {
            skipWhitespace();
            if (i >= s.length()) return null;
            char c = peek();
            if (c == '{') return parseObjectValue();
            if (c == '[') return parseArrayValue();
            if (c == '"') return parseStringValue();
            if (c == 't') { i += 4; return Boolean.TRUE; }
            if (c == 'f') { i += 5; return Boolean.FALSE; }
            if (c == 'n') { i += 4; return null; }
            return parseNumberValue();
        }

        Map<String, Object> parseObjectValue() {
            Map<String, Object> map = new LinkedHashMap<>();
            i++; // {
            skipWhitespace();
            if (i < s.length() && peek() == '}') { i++; return map; }
            while (true) {
                skipWhitespace();
                String key = parseStringValue();
                skipWhitespace();
                i++; // :
                Object val = parseValue();
                map.put(key, val);
                skipWhitespace();
                if (i < s.length() && peek() == ',') { i++; continue; }
                break;
            }
            skipWhitespace();
            if (i < s.length() && peek() == '}') i++;
            return map;
        }

        List<Object> parseArrayValue() {
            List<Object> list = new ArrayList<>();
            i++; // [
            skipWhitespace();
            if (i < s.length() && peek() == ']') { i++; return list; }
            while (true) {
                Object val = parseValue();
                list.add(val);
                skipWhitespace();
                if (i < s.length() && peek() == ',') { i++; continue; }
                break;
            }
            skipWhitespace();
            if (i < s.length() && peek() == ']') i++;
            return list;
        }

        String parseStringValue() {
            StringBuilder sb = new StringBuilder();
            i++; // opening quote
            while (i < s.length() && peek() != '"') {
                char c = s.charAt(i);
                if (c == '\\') {
                    i++;
                    char esc = s.charAt(i);
                    switch (esc) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u':
                            String hex = s.substring(i + 1, i + 5);
                            sb.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                            break;
                        default: sb.append(esc);
                    }
                    i++;
                } else {
                    sb.append(c);
                    i++;
                }
            }
            i++; // closing quote
            return sb.toString();
        }

        Double parseNumberValue() {
            int start = i;
            while (i < s.length() && "-+.eE0123456789".indexOf(s.charAt(i)) >= 0) i++;
            return Double.parseDouble(s.substring(start, i));
        }
    }
}
