package com.usmconnect.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.usmconnect.service.AuthService;
import com.usmconnect.service.FriendService;
import com.usmconnect.util.*;

import java.io.IOException;
import java.util.*;

public final class FriendHandler implements HttpHandler {

    private final FriendService friendService;
    private final AuthService authService;
    private final SessionManager sessions;

    public FriendHandler(FriendService friendService, AuthService authService, SessionManager sessions) {
        this.friendService = friendService;
        this.authService = authService;
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
            if (path.equals("/api/friends") && method.equals("GET")) {
                handleListFriends(exchange, userId);
            } else if (path.equals("/api/friends/requests") && method.equals("GET")) {
                handleListRequests(exchange, userId);
            } else if (path.equals("/api/friends/request") && method.equals("POST")) {
                handleSendRequest(exchange, userId);
            } else if (path.equals("/api/friends/respond") && method.equals("POST")) {
                handleRespond(exchange, userId);
            } else if (path.equals("/api/friends/remove") && method.equals("POST")) {
                handleRemove(exchange, userId);
            } else {
                HttpUtil.sendJson(exchange, 404, HttpUtil.error("Not found"));
            }
        } catch (Exception e) {
            HttpUtil.sendJson(exchange, 500, HttpUtil.error("Server error: " + e.getMessage()));
        }
    }

    private void handleListFriends(HttpExchange exchange, String userId) throws IOException {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String friendId : friendService.listFriendIds(userId)) {
            authService.findById(friendId).ifPresent(u -> out.add(AuthService.sanitize(u)));
        }
        HttpUtil.sendJson(exchange, 200, out);
    }

    private void handleListRequests(HttpExchange exchange, String userId) throws IOException {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : friendService.listPendingIncoming(userId)) {
            String fromId = String.valueOf(r.get("from"));
            Optional<Map<String, Object>> fromUser = authService.findById(fromId);
            if (fromUser.isEmpty()) continue;
            Map<String, Object> card = new LinkedHashMap<>(AuthService.sanitize(fromUser.get()));
            card.put("requestId", r.get("id"));
            card.put("createdAt", r.get("createdAt"));
            out.add(card);
        }
        HttpUtil.sendJson(exchange, 200, out);
    }

    private void handleSendRequest(HttpExchange exchange, String userId) throws IOException {
        Map<String, Object> body = JsonUtil.parseObject(HttpUtil.readBody(exchange));
        String targetId = String.valueOf(body.get("targetUserId"));
        try {
            Map<String, Object> result = friendService.sendRequest(userId, targetId);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", true);
            resp.put("status", result.get("status")); // "pending" or "accepted" (mutual auto-accept)
            HttpUtil.sendJson(exchange, 200, resp);
        } catch (FriendService.FriendException e) {
            HttpUtil.sendJson(exchange, 400, HttpUtil.error(e.getMessage()));
        }
    }

    private void handleRespond(HttpExchange exchange, String userId) throws IOException {
        Map<String, Object> body = JsonUtil.parseObject(HttpUtil.readBody(exchange));
        String requestId = String.valueOf(body.get("requestId"));
        boolean accept = Boolean.TRUE.equals(body.get("accept"));
        try {
            friendService.respond(requestId, userId, accept);
            HttpUtil.sendJson(exchange, 200, HttpUtil.ok());
        } catch (FriendService.FriendException e) {
            HttpUtil.sendJson(exchange, 400, HttpUtil.error(e.getMessage()));
        }
    }

    private void handleRemove(HttpExchange exchange, String userId) throws IOException {
        Map<String, Object> body = JsonUtil.parseObject(HttpUtil.readBody(exchange));
        String targetId = String.valueOf(body.get("targetUserId"));
        friendService.removeFriend(userId, targetId);
        HttpUtil.sendJson(exchange, 200, HttpUtil.ok());
    }
}
