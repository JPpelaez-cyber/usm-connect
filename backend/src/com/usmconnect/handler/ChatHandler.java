package com.usmconnect.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.usmconnect.service.AuthService;
import com.usmconnect.service.ChatService;
import com.usmconnect.util.*;

import java.io.IOException;
import java.util.*;

public final class ChatHandler implements HttpHandler {

    private final ChatService chatService;
    private final AuthService authService;
    private final SessionManager sessions;

    public ChatHandler(ChatService chatService, AuthService authService, SessionManager sessions) {
        this.chatService = chatService;
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
            if (path.equals("/api/chat/conversations") && method.equals("GET")) {
                handleConversations(exchange, userId);
            } else if (path.equals("/api/chat/messages") && method.equals("GET")) {
                handleMessages(exchange, userId);
            } else if (path.equals("/api/chat/send") && method.equals("POST")) {
                handleSend(exchange, userId);
            } else {
                HttpUtil.sendJson(exchange, 404, HttpUtil.error("Not found"));
            }
        } catch (Exception e) {
            HttpUtil.sendJson(exchange, 500, HttpUtil.error("Server error: " + e.getMessage()));
        }
    }

    private void handleConversations(HttpExchange exchange, String userId) throws IOException {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String partnerId : chatService.listConversationPartners(userId)) {
            authService.findById(partnerId).ifPresent(u -> out.add(AuthService.sanitize(u)));
        }
        HttpUtil.sendJson(exchange, 200, out);
    }

    private void handleMessages(HttpExchange exchange, String userId) throws IOException {
        Map<String, String> query = HttpUtil.parseQuery(exchange.getRequestURI().getQuery());
        String withId = query.get("with");
        if (withId == null || withId.isEmpty()) {
            HttpUtil.sendJson(exchange, 400, HttpUtil.error("Missing 'with' parameter"));
            return;
        }
        if (!chatService.canChat(userId, withId)) {
            HttpUtil.sendJson(exchange, 403, HttpUtil.error("You can only message matches or friends."));
            return;
        }
        long since = 0;
        try {
            since = Long.parseLong(query.getOrDefault("since", "0"));
        } catch (NumberFormatException ignored) { /* default to 0 */ }

        HttpUtil.sendJson(exchange, 200, chatService.getMessages(userId, withId, since));
    }

    private void handleSend(HttpExchange exchange, String userId) throws IOException {
        Map<String, Object> body = JsonUtil.parseObject(HttpUtil.readBody(exchange));
        String toId = String.valueOf(body.get("toUserId"));
        String text = body.get("text") == null ? "" : String.valueOf(body.get("text"));
        try {
            Map<String, Object> msg = chatService.sendMessage(userId, toId, text);
            HttpUtil.sendJson(exchange, 200, msg);
        } catch (ChatService.ChatException e) {
            HttpUtil.sendJson(exchange, 400, HttpUtil.error(e.getMessage()));
        }
    }
}
