package com.usmconnect.service;

import com.usmconnect.db.JsonStore;
import com.usmconnect.util.PasswordUtil;

import java.util.*;

public final class AuthService {

    public static final class AuthException extends Exception {
        public AuthException(String msg) { super(msg); }
    }

    private final JsonStore store;

    public AuthService(JsonStore store) {
        this.store = store;
    }

    public Map<String, Object> register(String fullName, String username, String email, String password)
            throws AuthException {
        if (isBlank(fullName) || isBlank(username) || isBlank(email) || isBlank(password)) {
            throw new AuthException("All fields (name, username, email, password) are required.");
        }
        if (password.length() < 6) {
            throw new AuthException("Password must be at least 6 characters.");
        }
        String usernameNorm = username.trim().toLowerCase();
        String emailNorm = email.trim().toLowerCase();

        List<Map<String, Object>> users = store.readAll("users");
        for (Map<String, Object> u : users) {
            if (usernameNorm.equals(String.valueOf(u.get("username")))) {
                throw new AuthException("Username is already taken.");
            }
            if (emailNorm.equals(String.valueOf(u.get("email")))) {
                throw new AuthException("An account with this email already exists.");
            }
        }

        String id = UUID.randomUUID().toString();
        String salt = PasswordUtil.randomSalt();
        String hash = PasswordUtil.hash(password, salt);

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", id);
        user.put("fullName", fullName.trim());
        user.put("username", usernameNorm);
        user.put("email", emailNorm);
        user.put("passwordSalt", salt);
        user.put("passwordHash", hash);
        user.put("photoPath", null);
        user.put("college", null);
        user.put("course", null);
        user.put("yearLevel", null);
        user.put("hobbies", new ArrayList<>());
        user.put("games", new ArrayList<>());
        user.put("foods", new ArrayList<>());
        user.put("bio", "");
        user.put("profileComplete", false);
        user.put("createdAt", System.currentTimeMillis());
        user.put("blocked", false);

        store.append("users", user);
        return sanitize(user);
    }

    public Map<String, Object> login(String username, String password) throws AuthException {
        if (isBlank(username) || isBlank(password)) {
            throw new AuthException("Username and password are required.");
        }
        String usernameNorm = username.trim().toLowerCase();
        List<Map<String, Object>> users = store.readAll("users");
        for (Map<String, Object> u : users) {
            boolean matchesUsername = usernameNorm.equals(String.valueOf(u.get("username")));
            boolean matchesEmail = usernameNorm.equals(String.valueOf(u.get("email")));
            if (matchesUsername || matchesEmail) {
                if (Boolean.TRUE.equals(u.get("blocked"))) {
                    throw new AuthException("This account has been blocked.");
                }
                String salt = String.valueOf(u.get("passwordSalt"));
                String expected = String.valueOf(u.get("passwordHash"));
                if (PasswordUtil.verify(password, salt, expected)) {
                    return u;
                }
                throw new AuthException("Incorrect username or password.");
            }
        }
        throw new AuthException("Incorrect username or password.");
    }

    public Optional<Map<String, Object>> findById(String id) {
        if (id == null) return Optional.empty();
        for (Map<String, Object> u : store.readAll("users")) {
            if (id.equals(String.valueOf(u.get("id")))) return Optional.of(u);
        }
        return Optional.empty();
    }

    /** Removes sensitive fields (password hash/salt) before sending a user to the client. */
    public static Map<String, Object> sanitize(Map<String, Object> user) {
        Map<String, Object> copy = new LinkedHashMap<>(user);
        copy.remove("passwordHash");
        copy.remove("passwordSalt");
        copy.remove("email"); // never expose email publicly (requirement #13)
        return copy;
    }

    /** Like sanitize(), but keeps email visible -- used only when returning a user's OWN profile. */
    public static Map<String, Object> sanitizeForSelf(Map<String, Object> user) {
        Map<String, Object> copy = new LinkedHashMap<>(user);
        copy.remove("passwordHash");
        copy.remove("passwordSalt");
        return copy;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
