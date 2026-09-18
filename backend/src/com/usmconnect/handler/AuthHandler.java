package com.usmconnect.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.usmconnect.service.AuthService;
import com.usmconnect.util.HttpUtil;
import com.usmconnect.util.SessionManager;

import java.io.IOException;
import java.util.Map;

public final class AuthHandler implements HttpHandler {

    private final AuthService authService;
    private final SessionManager sessions;

    public AuthHandler(AuthService authService, SessionManager sessions) {
        this.authService = authService;
        this.sessions = sessions;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        try {
            if (path.equals("/api/register") && method.equals("POST")) {
                handleRegister(exchange);
            } else if (path.equals("/api/login") && method.equals("POST")) {
                handleLogin(exchange);
            } else if (path.equals("/api/logout") && method.equals("POST")) {
                handleLogout(exchange);
            } else if (path.equals("/api/me") && method.equals("GET")) {
                handleMe(exchange);
            } else {
                HttpUtil.sendJson(exchange, 404, HttpUtil.error("Not found"));
            }
        } catch (Exception e) {
            HttpUtil.sendJson(exchange, 500, HttpUtil.error("Server error: " + e.getMessage()));
        }
    }

    private void handleRegister(HttpExchange exchange) throws IOException {
        Map<String, Object> body = com.usmconnect.util.JsonUtil.parseObject(HttpUtil.readBody(exchange));
        try {
            Map<String, Object> user = authService.register(
                    str(body.get("fullName")), str(body.get("username")),
                    str(body.get("email")), str(body.get("password")));
            String token = sessions.createSession(String.valueOf(user.get("id")));
            HttpUtil.setCookie(exchange, "usmsession", token, 60 * 60 * 24 * 7);
            HttpUtil.sendJson(exchange, 200, AuthService.sanitizeForSelf(user));
        } catch (AuthService.AuthException e) {
            HttpUtil.sendJson(exchange, 400, HttpUtil.error(e.getMessage()));
        }
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        Map<String, Object> body = com.usmconnect.util.JsonUtil.parseObject(HttpUtil.readBody(exchange));
        try {
            Map<String, Object> user = authService.login(str(body.get("username")), str(body.get("password")));
            String token = sessions.createSession(String.valueOf(user.get("id")));
            HttpUtil.setCookie(exchange, "usmsession", token, 60 * 60 * 24 * 7);
            HttpUtil.sendJson(exchange, 200, AuthService.sanitizeForSelf(user));
        } catch (AuthService.AuthException e) {
            HttpUtil.sendJson(exchange, 401, HttpUtil.error(e.getMessage()));
        }
    }

    private void handleLogout(HttpExchange exchange) throws IOException {
        String token = HttpUtil.parseCookies(exchange).get("usmsession");
        sessions.destroy(token);
        HttpUtil.clearCookie(exchange, "usmsession");
        HttpUtil.sendJson(exchange, 200, HttpUtil.ok());
    }

    private void handleMe(HttpExchange exchange) throws IOException {
        String token = HttpUtil.parseCookies(exchange).get("usmsession");
        String userId = sessions.getUserId(token);
        if (userId == null) {
            HttpUtil.sendJson(exchange, 401, HttpUtil.error("Not logged in"));
            return;
        }
        authService.findById(userId).ifPresentOrElse(
                u -> {
                    try {
                        HttpUtil.sendJson(exchange, 200, AuthService.sanitizeForSelf(u));
                    } catch (IOException ignored) {}
                },
                () -> {
                    try {
                        HttpUtil.sendJson(exchange, 401, HttpUtil.error("Session invalid"));
                    } catch (IOException ignored) {}
                });
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
