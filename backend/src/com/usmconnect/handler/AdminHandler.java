package com.usmconnect.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.usmconnect.config.OptionsConfig;
import com.usmconnect.db.JsonStore;
import com.usmconnect.util.HttpUtil;
import com.usmconnect.util.JsonUtil;

import java.io.IOException;
import java.util.*;

/**
 * Minimal admin API, protected by a shared secret (the "X-Admin-Key" header).
 * The key is read from the ADMIN_KEY environment variable, falling back to
 * "usm-admin-2024" for local development -- change this before deploying
 * anywhere real (see README).
 *
 * This is intentionally lightweight (no per-admin accounts, no roles) so it
 * can be wired up quickly; it satisfies requirement #14 ("admin can add,
 * edit and remove selectable options without touching source code") via
 * POST /api/admin/options, plus basic user moderation.
 */
public final class AdminHandler implements HttpHandler {

    private static final String ADMIN_KEY =
            System.getenv().getOrDefault("ADMIN_KEY", "usm-admin-2024");

    private final JsonStore store;
    private final OptionsConfig options;

    public AdminHandler(JsonStore store, OptionsConfig options) {
        this.store = store;
        this.options = options;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String provided = exchange.getRequestHeaders().getFirst("X-Admin-Key");
        if (!ADMIN_KEY.equals(provided)) {
            HttpUtil.sendJson(exchange, 403, HttpUtil.error("Invalid or missing admin key."));
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        try {
            if (path.equals("/api/admin/users") && method.equals("GET")) {
                handleListUsers(exchange);
            } else if (path.equals("/api/admin/users/block") && method.equals("POST")) {
                handleBlockUser(exchange);
            } else if (path.equals("/api/admin/options") && method.equals("GET")) {
                HttpUtil.sendJson(exchange, 200, options.asMap());
            } else if (path.equals("/api/admin/options") && method.equals("POST")) {
                handleUpdateOptions(exchange);
            } else if (path.equals("/api/admin/reports") && method.equals("GET")) {
                HttpUtil.sendJson(exchange, 200, store.readAll("reports"));
            } else {
                HttpUtil.sendJson(exchange, 404, HttpUtil.error("Not found"));
            }
        } catch (Exception e) {
            HttpUtil.sendJson(exchange, 500, HttpUtil.error("Server error: " + e.getMessage()));
        }
    }

    private void handleListUsers(HttpExchange exchange) throws IOException {
        List<Map<String, Object>> users = store.readAll("users");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> u : users) {
            Map<String, Object> copy = new LinkedHashMap<>(u);
            copy.remove("passwordHash");
            copy.remove("passwordSalt");
            out.add(copy);
        }
        HttpUtil.sendJson(exchange, 200, out);
    }

    private void handleBlockUser(HttpExchange exchange) throws IOException {
        Map<String, Object> body = JsonUtil.parseObject(HttpUtil.readBody(exchange));
        String userId = String.valueOf(body.get("userId"));
        boolean blocked = Boolean.TRUE.equals(body.get("blocked"));

        List<Map<String, Object>> users = store.readAll("users");
        for (Map<String, Object> u : users) {
            if (userId.equals(String.valueOf(u.get("id")))) {
                Map<String, Object> updated = new LinkedHashMap<>(u);
                updated.put("blocked", blocked);
                store.update("users", "id", userId, updated);
                HttpUtil.sendJson(exchange, 200, HttpUtil.ok());
                return;
            }
        }
        HttpUtil.sendJson(exchange, 404, HttpUtil.error("User not found"));
    }

    private void handleUpdateOptions(HttpExchange exchange) throws IOException {
        Map<String, Object> body = JsonUtil.parseObject(HttpUtil.readBody(exchange));
        options.update(body);
        HttpUtil.sendJson(exchange, 200, HttpUtil.ok());
    }
}
