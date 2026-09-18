package com.usmconnect.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.usmconnect.db.JsonStore;
import com.usmconnect.service.AuthService;
import com.usmconnect.service.FriendService;
import com.usmconnect.service.MatchService;
import com.usmconnect.util.*;

import java.io.IOException;
import java.util.*;

public final class DiscoverHandler implements HttpHandler {

    private final JsonStore store;
    private final AuthService authService;
    private final MatchService matchService;
    private final FriendService friendService;
    private final SessionManager sessions;

    public DiscoverHandler(JsonStore store, AuthService authService, MatchService matchService,
                            FriendService friendService, SessionManager sessions) {
        this.store = store;
        this.authService = authService;
        this.matchService = matchService;
        this.friendService = friendService;
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
            if (path.equals("/api/discover") && method.equals("GET")) {
                handleDiscover(exchange, userId);
            } else if (path.equals("/api/like") && method.equals("POST")) {
                handleLike(exchange, userId);
            } else if (path.equals("/api/pass") && method.equals("POST")) {
                handlePass(exchange, userId);
            } else {
                HttpUtil.sendJson(exchange, 404, HttpUtil.error("Not found"));
            }
        } catch (Exception e) {
            HttpUtil.sendJson(exchange, 500, HttpUtil.error("Server error: " + e.getMessage()));
        }
    }

    private void handleDiscover(HttpExchange exchange, String userId) throws IOException {
        Optional<Map<String, Object>> maybeMe = authService.findById(userId);
        if (maybeMe.isEmpty()) {
            HttpUtil.sendJson(exchange, 401, HttpUtil.error("Session invalid"));
            return;
        }
        Map<String, Object> me = maybeMe.get();
        Set<String> excluded = matchService.excludedUserIds(userId);
        excluded.add(userId);

        List<Map<String, Object>> candidates = new ArrayList<>();
        for (Map<String, Object> other : store.readAll("users")) {
            String otherId = String.valueOf(other.get("id"));
            if (excluded.contains(otherId)) continue;
            if (Boolean.TRUE.equals(other.get("blocked"))) continue;
            if (!Boolean.TRUE.equals(other.get("profileComplete"))) continue;

            Map<String, Object> compat = matchService.computeCompatibility(me, other);
            Map<String, Object> card = new LinkedHashMap<>(AuthService.sanitize(other));
            card.put("compatibility", compat.get("score"));
            card.put("commonInterests", compat.get("commonInterests"));
            card.put("friendStatus", friendService.statusBetween(userId, otherId));
            candidates.add(card);
        }

        candidates.sort((a, b) -> Integer.compare(
                toInt(b.get("compatibility")), toInt(a.get("compatibility"))));

        HttpUtil.sendJson(exchange, 200, candidates);
    }

    private static Integer toInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        return 0;
    }

    private void handleLike(HttpExchange exchange, String userId) throws IOException {
        Map<String, Object> body = JsonUtil.parseObject(HttpUtil.readBody(exchange));
        String targetId = String.valueOf(body.get("targetUserId"));
        if (targetId == null || targetId.equals("null") || targetId.equals(userId)) {
            HttpUtil.sendJson(exchange, 400, HttpUtil.error("Invalid targetUserId"));
            return;
        }
        boolean matched = matchService.recordLike(userId, targetId);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("matched", matched);
        if (matched) {
            authService.findById(targetId).ifPresent(u -> resp.put("matchedWith", AuthService.sanitize(u)));
        }
        HttpUtil.sendJson(exchange, 200, resp);
    }

    private void handlePass(HttpExchange exchange, String userId) throws IOException {
        Map<String, Object> body = JsonUtil.parseObject(HttpUtil.readBody(exchange));
        String targetId = String.valueOf(body.get("targetUserId"));
        if (targetId == null || targetId.equals("null") || targetId.equals(userId)) {
            HttpUtil.sendJson(exchange, 400, HttpUtil.error("Invalid targetUserId"));
            return;
        }
        matchService.recordPass(userId, targetId);
        HttpUtil.sendJson(exchange, 200, HttpUtil.ok());
    }
}
