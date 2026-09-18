package com.usmconnect.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.Map;

public final class StaticFileHandler implements HttpHandler {

    private static final Map<String, String> MIME = Map.ofEntries(
            Map.entry(".html", "text/html; charset=utf-8"),
            Map.entry(".css", "text/css; charset=utf-8"),
            Map.entry(".js", "application/javascript; charset=utf-8"),
            Map.entry(".png", "image/png"),
            Map.entry(".jpg", "image/jpeg"),
            Map.entry(".jpeg", "image/jpeg"),
            Map.entry(".gif", "image/gif"),
            Map.entry(".webp", "image/webp"),
            Map.entry(".svg", "image/svg+xml"),
            Map.entry(".ico", "image/x-icon"),
            Map.entry(".json", "application/json"));

    private final Path rootDir;
    private final String urlPrefix;
    private final boolean spaFallback;

    public StaticFileHandler(Path rootDir, String urlPrefix, boolean spaFallback) {
        this.rootDir = rootDir.normalize();
        this.urlPrefix = urlPrefix;
        this.spaFallback = spaFallback;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        String relative;
        if (urlPrefix.equals("/")) {
            relative = path; // already starts with "/", e.g. "/css/style.css"
        } else {
            relative = path.substring(urlPrefix.length()); // e.g. "/uploads/x.jpg" -> "/x.jpg"
        }
        if (relative.isEmpty() || relative.equals("/")) relative = "/index.html";
        // relative is now guaranteed to start with exactly one "/"

        Path target = rootDir.resolve(relative.substring(1)).normalize();

        // Prevent path traversal outside the served directory.
        if (!target.startsWith(rootDir)) {
            exchange.sendResponseHeaders(403, -1);
            return;
        }

        if (!Files.exists(target) || Files.isDirectory(target)) {
            if (spaFallback) {
                target = rootDir.resolve("index.html");
            } else {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
        }

        String ext = "";
        int dot = target.toString().lastIndexOf('.');
        if (dot >= 0) ext = target.toString().substring(dot);
        String mime = MIME.getOrDefault(ext, "application/octet-stream");

        byte[] bytes = Files.readAllBytes(target);
        exchange.getResponseHeaders().set("Content-Type", mime);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
