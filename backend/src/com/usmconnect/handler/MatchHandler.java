package com.usmconnect.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.usmconnect.service.AuthService;
import com.usmconnect.service.MatchService;
import com.usmconnect.util.HttpUtil;
import com.usmconnect.util.SessionManager;

import java.io.IOException;
import java.util.*;

public final class MatchHandler implements HttpHandler {

    private final AuthService authService;
    private final MatchService matchService;
    private final SessionManager sessions;

    public MatchHandler(AuthService authService, MatchService matchService, SessionManager sessions) {
        this.authService = authService;
        this.matchService = matchService;
        this.sessions = sessions;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            HttpUtil.sendJson(exchange, 404, HttpUtil.error("Not found"));
            return;
        }
        String userId = sessions.getUserId(HttpUtil.parseCookies(exchange).get("usmsession"));
        if (userId == null) {
            HttpUtil.sendJson(exchange, 401, HttpUtil.error("Not logged in"));
            return;
        }
        Optional<Map<String, Object>> me = authService.findById(userId);
        if (me.isEmpty()) {
            HttpUtil.sendJson(exchange, 401, HttpUtil.error("Session invalid"));
            return;
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (String otherId : matchService.matchedUserIds(userId)) {
            Optional<Map<String, Object>> other = authService.findById(otherId);
            if (other.isEmpty()) continue;
            Map<String, Object> compat = matchService.computeCompatibility(me.get(), other.get());
            Map<String, Object> card = new LinkedHashMap<>(AuthService.sanitize(other.get()));
            card.put("compatibility", compat.get("score"));
            card.put("commonInterests", compat.get("commonInterests"));
            results.add(card);
        }
        HttpUtil.sendJson(exchange, 200, results);
    }
}
