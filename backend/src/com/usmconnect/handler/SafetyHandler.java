package com.usmconnect.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.usmconnect.service.MatchService;
import com.usmconnect.util.HttpUtil;
import com.usmconnect.util.JsonUtil;
import com.usmconnect.util.SessionManager;

import java.io.IOException;
import java.util.Map;

public final class SafetyHandler implements HttpHandler {

    private final MatchService matchService;
    private final SessionManager sessions;

    public SafetyHandler(MatchService matchService, SessionManager sessions) {
        this.matchService = matchService;
        this.sessions = sessions;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        String userId = sessions.getUserId(HttpUtil.parseCookies(exchange).get("usmsession"));
        if (userId == null) {
            HttpUtil.sendJson(exchange, 401, HttpUtil.error("Not logged in"));
            return;
        }

        try {
            if (path.equals("/api/block") && method.equals("POST")) {
                Map<String, Object> body = JsonUtil.parseObject(HttpUtil.readBody(exchange));
                String targetId = String.valueOf(body.get("targetUserId"));
                matchService.recordBlock(userId, targetId);
                HttpUtil.sendJson(exchange, 200, HttpUtil.ok());
            } else if (path.equals("/api/report") && method.equals("POST")) {
                Map<String, Object> body = JsonUtil.parseObject(HttpUtil.readBody(exchange));
                String targetId = String.valueOf(body.get("targetUserId"));
                String reason = String.valueOf(body.getOrDefault("reason", "Not specified"));
                matchService.recordReport(userId, targetId, reason);
                HttpUtil.sendJson(exchange, 200, HttpUtil.ok());
            } else {
                HttpUtil.sendJson(exchange, 404, HttpUtil.error("Not found"));
            }
        } catch (Exception e) {
            HttpUtil.sendJson(exchange, 500, HttpUtil.error("Server error: " + e.getMessage()));
        }
    }
}
