package com.usmconnect.util;

import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class HttpUtil {

    private HttpUtil() {}

    public static String readBody(HttpExchange exchange) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        try (InputStream is = exchange.getRequestBody()) {
            while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        }
        return bos.toString("UTF-8");
    }

    public static byte[] readBodyBytes(HttpExchange exchange) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        try (InputStream is = exchange.getRequestBody()) {
            while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    public static void sendJson(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] bytes = JsonUtil.write(payload).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static Map<String, Object> error(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", message);
        return m;
    }

    public static Map<String, Object> ok() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        return m;
    }

    public static Map<String, String> parseCookies(HttpExchange exchange) {
        Map<String, String> cookies = new HashMap<>();
        List<String> headers = exchange.getRequestHeaders().get("Cookie");
        if (headers == null) return cookies;
        for (String header : headers) {
            for (String part : header.split(";")) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length == 2) cookies.put(kv[0].trim(), kv[1].trim());
            }
        }
        return cookies;
    }

    public static void setCookie(HttpExchange exchange, String name, String value, int maxAgeSeconds) {
        String cookie = name + "=" + value + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" + maxAgeSeconds;
        exchange.getResponseHeaders().add("Set-Cookie", cookie);
    }

    public static void clearCookie(HttpExchange exchange, String name) {
        exchange.getResponseHeaders().add("Set-Cookie", name + "=; Path=/; HttpOnly; Max-Age=0");
    }

    public static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null) return map;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            try {
                String key = java.net.URLDecoder.decode(kv[0], "UTF-8");
                String val = kv.length > 1 ? java.net.URLDecoder.decode(kv[1], "UTF-8") : "";
                map.put(key, val);
            } catch (UnsupportedEncodingException ignored) {}
        }
        return map;
    }
}
