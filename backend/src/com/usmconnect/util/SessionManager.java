package com.usmconnect.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps session tokens (stored in the "usmsession" cookie) to user IDs.
 * Kept in memory: if the server restarts, users simply need to log in again
 * (their account data is unaffected, since that lives in JsonStore on disk).
 */
public final class SessionManager {

    private final Map<String, String> tokenToUserId = new ConcurrentHashMap<>();

    public String createSession(String userId) {
        String token = PasswordUtil.randomToken();
        tokenToUserId.put(token, userId);
        return token;
    }

    public String getUserId(String token) {
        if (token == null) return null;
        return tokenToUserId.get(token);
    }

    public void destroy(String token) {
        if (token != null) tokenToUserId.remove(token);
    }
}
