package com.usmconnect.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.usmconnect.db.JsonStore;
import com.usmconnect.service.AuthService;
import com.usmconnect.util.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class ProfileHandler implements HttpHandler {

    private static final Set<String> ALLOWED_IMAGE_TYPES = new HashSet<>(Arrays.asList(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif"));
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024; // 5 MB

    private final JsonStore store;
    private final AuthService authService;
    private final SessionManager sessions;

    public ProfileHandler(JsonStore store, AuthService authService, SessionManager sessions) {
        this.store = store;
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
            if (path.equals("/api/profile") && method.equals("PUT")) {
                handleUpdate(exchange, userId);
            } else if (path.equals("/api/profile/photo") && method.equals("POST")) {
                handlePhoto(exchange, userId);
            } else {
                HttpUtil.sendJson(exchange, 404, HttpUtil.error("Not found"));
            }
        } catch (Exception e) {
            HttpUtil.sendJson(exchange, 500, HttpUtil.error("Server error: " + e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private void handleUpdate(HttpExchange exchange, String userId) throws IOException {
        Optional<Map<String, Object>> maybeUser = authService.findById(userId);
        if (maybeUser.isEmpty()) {
            HttpUtil.sendJson(exchange, 401, HttpUtil.error("Session invalid"));
            return;
        }
        Map<String, Object> user = new LinkedHashMap<>(maybeUser.get());
        Map<String, Object> body = JsonUtil.parseObject(HttpUtil.readBody(exchange));

        if (body.containsKey("college")) user.put("college", body.get("college"));
        if (body.containsKey("course")) user.put("course", body.get("course"));
        if (body.containsKey("yearLevel")) user.put("yearLevel", body.get("yearLevel"));
        if (body.containsKey("bio")) user.put("bio", truncate(String.valueOf(body.get("bio")), 300));
        if (body.containsKey("hobbies")) user.put("hobbies", body.get("hobbies"));
        if (body.containsKey("games")) user.put("games", body.get("games"));
        if (body.containsKey("foods")) user.put("foods", body.get("foods"));

        boolean complete = user.get("course") != null && user.get("yearLevel") != null
                && !((List<Object>) user.getOrDefault("hobbies", List.of())).isEmpty();
        user.put("profileComplete", complete);

        store.update("users", "id", userId, user);
        HttpUtil.sendJson(exchange, 200, AuthService.sanitizeForSelf(user));
    }

    private void handlePhoto(HttpExchange exchange, String userId) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        String boundary = MultipartParser.extractBoundary(contentType);
        if (boundary == null) {
            HttpUtil.sendJson(exchange, 400, HttpUtil.error("Expected multipart/form-data upload."));
            return;
        }
        byte[] body = HttpUtil.readBodyBytes(exchange);
        if (body.length > MAX_IMAGE_BYTES) {
            HttpUtil.sendJson(exchange, 413, HttpUtil.error("Image too large (max 5MB)."));
            return;
        }
        List<MultipartParser.Part> parts = MultipartParser.parse(body, boundary);
        MultipartParser.Part filePart = null;
        for (MultipartParser.Part p : parts) {
            if ("photo".equals(p.name) && p.filename != null) {
                filePart = p;
                break;
            }
        }
        if (filePart == null) {
            HttpUtil.sendJson(exchange, 400, HttpUtil.error("No 'photo' file field found."));
            return;
        }
        String ct = filePart.contentType == null ? "" : filePart.contentType.toLowerCase(Locale.ROOT);
        if (!ALLOWED_IMAGE_TYPES.contains(ct)) {
            HttpUtil.sendJson(exchange, 415,
                    HttpUtil.error("Only JPEG, PNG, WEBP or GIF images are allowed."));
            return;
        }

        String ext = ct.equals("image/png") ? ".png" : ct.equals("image/webp") ? ".webp"
                : ct.equals("image/gif") ? ".gif" : ".jpg";
        String fileName = userId + "-" + System.currentTimeMillis() + ext;
        Path uploadsDir = store.getUploadsDir();
        Files.createDirectories(uploadsDir);
        Path dest = uploadsDir.resolve(fileName);
        Files.write(dest, filePart.data);

        Optional<Map<String, Object>> maybeUser = authService.findById(userId);
        if (maybeUser.isPresent()) {
            Map<String, Object> user = new LinkedHashMap<>(maybeUser.get());
            user.put("photoPath", "/uploads/" + fileName);
            store.update("users", "id", userId, user);
            HttpUtil.sendJson(exchange, 200, AuthService.sanitizeForSelf(user));
        } else {
            HttpUtil.sendJson(exchange, 401, HttpUtil.error("Session invalid"));
        }
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
