package com.usmconnect;

import com.sun.net.httpserver.HttpServer;
import com.usmconnect.config.OptionsConfig;
import com.usmconnect.db.JsonStore;
import com.usmconnect.handler.*;
import com.usmconnect.service.AuthService;
import com.usmconnect.service.ChatService;
import com.usmconnect.service.FriendService;
import com.usmconnect.service.MatchService;
import com.usmconnect.util.SessionManager;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

/**
 * USM Connect -- a matching app for University of Southern Mindanao students.
 *
 * Run with:  java -cp out com.usmconnect.Main
 * Then open: http://localhost:8080
 *
 * See README.md at the project root for full setup instructions.
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        Path backendRoot = Paths.get(System.getProperty("user.dir"));
        Path dataDir = backendRoot.resolve("data");
        Path frontendDir = resolveFrontendDir(backendRoot);

        JsonStore store = new JsonStore(dataDir);
        OptionsConfig options = new OptionsConfig(dataDir.resolve("options.json"));
        SessionManager sessions = new SessionManager();
        AuthService authService = new AuthService(store);
        MatchService matchService = new MatchService(store);
        FriendService friendService = new FriendService(store);
        ChatService chatService = new ChatService(store, matchService, friendService);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(16));

        // API routes
        server.createContext("/api/register", new AuthHandler(authService, sessions));
        server.createContext("/api/login", new AuthHandler(authService, sessions));
        server.createContext("/api/logout", new AuthHandler(authService, sessions));
        server.createContext("/api/me", new AuthHandler(authService, sessions));

        server.createContext("/api/profile", new ProfileHandler(store, authService, sessions));

        server.createContext("/api/discover",
                new DiscoverHandler(store, authService, matchService, friendService, sessions));
        server.createContext("/api/like",
                new DiscoverHandler(store, authService, matchService, friendService, sessions));
        server.createContext("/api/pass",
                new DiscoverHandler(store, authService, matchService, friendService, sessions));

        server.createContext("/api/matches", new MatchHandler(authService, matchService, sessions));
        server.createContext("/api/options", new OptionsHandler(options));

        server.createContext("/api/block", new SafetyHandler(matchService, sessions));
        server.createContext("/api/report", new SafetyHandler(matchService, sessions));

        server.createContext("/api/admin", new AdminHandler(store, options));

        // Friend system
        FriendHandler friendHandler = new FriendHandler(friendService, authService, sessions);
        server.createContext("/api/friends", friendHandler);
        server.createContext("/api/friends/requests", friendHandler);
        server.createContext("/api/friends/request", friendHandler);
        server.createContext("/api/friends/respond", friendHandler);
        server.createContext("/api/friends/remove", friendHandler);

        // Chat system (permitted only between matches or friends)
        ChatHandler chatHandler = new ChatHandler(chatService, authService, sessions);
        server.createContext("/api/chat/conversations", chatHandler);
        server.createContext("/api/chat/messages", chatHandler);
        server.createContext("/api/chat/send", chatHandler);

        // Static files: uploaded photos + the frontend itself
        server.createContext("/uploads", new StaticFileHandler(store.getUploadsDir(), "/uploads", false));
        server.createContext("/", new StaticFileHandler(frontendDir, "/", true));

        server.start();

        System.out.println("=================================================");
        System.out.println(" USM Connect is running!");
        System.out.println(" Open: http://localhost:" + port);
        System.out.println(" Data stored in: " + dataDir.toAbsolutePath());
        System.out.println(" Admin panel: http://localhost:" + port + "/admin.html");
        System.out.println("   (default admin key: usm-admin-2024 -- set ADMIN_KEY env var to change it)");
        System.out.println("=================================================");
    }

    private static Path resolveFrontendDir(Path backendRoot) {
        // Works whether launched from backend/ (frontend is ../frontend)
        // or from the project root (frontend is ./frontend).
        Path candidate1 = backendRoot.resolve("../frontend").normalize();
        Path candidate2 = backendRoot.resolve("frontend").normalize();
        if (java.nio.file.Files.exists(candidate1)) return candidate1;
        return candidate2;
    }
}
