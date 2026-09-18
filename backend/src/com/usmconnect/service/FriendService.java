package com.usmconnect.service;

import com.usmconnect.db.JsonStore;

import java.util.*;

/**
 * Friendship is intentionally separate from the Like/Pass/Match system used
 * for romantic matching. Two students can be friends without ever liking
 * each other romantically, and two people can be a romantic Match without
 * being (or wanting to be) "friends" in this platonic sense. Both, however,
 * unlock the ability to chat (see ChatService).
 */
public final class FriendService {

    private final JsonStore store;

    public FriendService(JsonStore store) {
        this.store = store;
    }

    public static final class FriendException extends Exception {
        public FriendException(String msg) { super(msg); }
    }

    /**
     * Sends a friend request. If the target already sent *you* a pending
     * request, this instead auto-accepts theirs (a mutual request becomes an
     * instant friendship, the same way a mutual Like becomes a Match).
     */
    public Map<String, Object> sendRequest(String fromId, String toId) throws FriendException {
        if (fromId.equals(toId)) {
            throw new FriendException("You can't send a friend request to yourself.");
        }
        List<Map<String, Object>> all = store.readAll("friend_requests");

        for (Map<String, Object> r : all) {
            if (isBetween(String.valueOf(r.get("from")), String.valueOf(r.get("to")), fromId, toId)
                    && "accepted".equals(r.get("status"))) {
                throw new FriendException("You're already friends.");
            }
        }

        // Mutual request -> auto-accept theirs instead of creating a duplicate.
        for (Map<String, Object> r : all) {
            if (toId.equals(r.get("from")) && fromId.equals(r.get("to")) && "pending".equals(r.get("status"))) {
                Map<String, Object> updated = new LinkedHashMap<>(r);
                updated.put("status", "accepted");
                updated.put("respondedAt", System.currentTimeMillis());
                store.update("friend_requests", "id", String.valueOf(r.get("id")), updated);
                return updated;
            }
        }

        for (Map<String, Object> r : all) {
            if (fromId.equals(r.get("from")) && toId.equals(r.get("to")) && "pending".equals(r.get("status"))) {
                throw new FriendException("You already sent a friend request to this person.");
            }
        }

        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("id", UUID.randomUUID().toString());
        rec.put("from", fromId);
        rec.put("to", toId);
        rec.put("status", "pending");
        rec.put("createdAt", System.currentTimeMillis());
        rec.put("respondedAt", null);
        store.append("friend_requests", rec);
        return rec;
    }

    public void respond(String requestId, String userId, boolean accept) throws FriendException {
        for (Map<String, Object> r : store.readAll("friend_requests")) {
            if (!requestId.equals(String.valueOf(r.get("id")))) continue;
            if (!userId.equals(String.valueOf(r.get("to")))) {
                throw new FriendException("This request isn't addressed to you.");
            }
            if (!"pending".equals(r.get("status"))) {
                throw new FriendException("This request has already been handled.");
            }
            Map<String, Object> updated = new LinkedHashMap<>(r);
            updated.put("status", accept ? "accepted" : "declined");
            updated.put("respondedAt", System.currentTimeMillis());
            store.update("friend_requests", "id", requestId, updated);
            return;
        }
        throw new FriendException("Friend request not found.");
    }

    /** Ends a friendship (sets the accepted request to "removed" rather than deleting it). */
    public void removeFriend(String userId, String otherId) {
        for (Map<String, Object> r : store.readAll("friend_requests")) {
            String a = String.valueOf(r.get("from"));
            String b = String.valueOf(r.get("to"));
            if (isBetween(a, b, userId, otherId) && "accepted".equals(r.get("status"))) {
                Map<String, Object> updated = new LinkedHashMap<>(r);
                updated.put("status", "removed");
                store.update("friend_requests", "id", String.valueOf(r.get("id")), updated);
            }
        }
    }

    public boolean isFriend(String a, String b) {
        for (Map<String, Object> r : store.readAll("friend_requests")) {
            if (isBetween(String.valueOf(r.get("from")), String.valueOf(r.get("to")), a, b)
                    && "accepted".equals(r.get("status"))) {
                return true;
            }
        }
        return false;
    }

    public List<String> listFriendIds(String userId) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> r : store.readAll("friend_requests")) {
            if (!"accepted".equals(r.get("status"))) continue;
            String a = String.valueOf(r.get("from"));
            String b = String.valueOf(r.get("to"));
            if (a.equals(userId)) out.add(b);
            else if (b.equals(userId)) out.add(a);
        }
        return out;
    }

    public List<Map<String, Object>> listPendingIncoming(String userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : store.readAll("friend_requests")) {
            if (userId.equals(r.get("to")) && "pending".equals(r.get("status"))) out.add(r);
        }
        return out;
    }

    /** Returns "none", "pending_outgoing", "pending_incoming", or "friends" for the pair (a, b). */
    public String statusBetween(String a, String b) {
        for (Map<String, Object> r : store.readAll("friend_requests")) {
            String from = String.valueOf(r.get("from"));
            String to = String.valueOf(r.get("to"));
            if (!isBetween(from, to, a, b)) continue;
            String status = String.valueOf(r.get("status"));
            if ("accepted".equals(status)) return "friends";
            if ("pending".equals(status)) return from.equals(a) ? "pending_outgoing" : "pending_incoming";
        }
        return "none";
    }

    private static boolean isBetween(String a, String b, String x, String y) {
        return (a.equals(x) && b.equals(y)) || (a.equals(y) && b.equals(x));
    }
}
