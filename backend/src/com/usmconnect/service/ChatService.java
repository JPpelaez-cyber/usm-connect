package com.usmconnect.service;

import com.usmconnect.db.JsonStore;

import java.util.*;

/**
 * Chat is only permitted between two users who are either a romantic Match
 * or platonic Friends (see MatchService / FriendService). Messages are
 * stored per "conversation" -- a stable, order-independent id derived from
 * the two participant ids -- so a single collection file covers everyone's
 * conversations at once.
 */
public final class ChatService {

    private final JsonStore store;
    private final MatchService matchService;
    private final FriendService friendService;

    public ChatService(JsonStore store, MatchService matchService, FriendService friendService) {
        this.store = store;
        this.matchService = matchService;
        this.friendService = friendService;
    }

    public static final class ChatException extends Exception {
        public ChatException(String msg) { super(msg); }
    }

    public boolean canChat(String a, String b) {
        return matchService.isMatched(a, b) || friendService.isFriend(a, b);
    }

    public Map<String, Object> sendMessage(String fromId, String toId, String text) throws ChatException {
        if (text == null || text.trim().isEmpty()) {
            throw new ChatException("Message can't be empty.");
        }
        String trimmed = text.trim();
        if (trimmed.length() > 1000) trimmed = trimmed.substring(0, 1000);
        if (!canChat(fromId, toId)) {
            throw new ChatException("You can only message people you've matched or are friends with.");
        }

        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("id", UUID.randomUUID().toString());
        rec.put("conversationId", conversationId(fromId, toId));
        rec.put("from", fromId);
        rec.put("to", toId);
        rec.put("text", trimmed);
        rec.put("createdAt", System.currentTimeMillis());
        store.append("messages", rec);
        return rec;
    }

    /** Returns messages in the conversation between a and b, newer than sinceTimestamp, oldest first. */
    public List<Map<String, Object>> getMessages(String a, String b, long sinceTimestamp) {
        String convo = conversationId(a, b);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> m : store.readAll("messages")) {
            if (!convo.equals(String.valueOf(m.get("conversationId")))) continue;
            long createdAt = toLong(m.get("createdAt"));
            if (createdAt > sinceTimestamp) out.add(m);
        }
        out.sort(Comparator.comparingLong(m -> toLong(m.get("createdAt"))));
        return out;
    }

    /** Everyone this user is allowed to chat with: matches unioned with friends. */
    public List<String> listConversationPartners(String userId) {
        LinkedHashSet<String> partners = new LinkedHashSet<>();
        partners.addAll(matchService.matchedUserIds(userId));
        partners.addAll(friendService.listFriendIds(userId));
        return new ArrayList<>(partners);
    }

    public static String conversationId(String a, String b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    private static long toLong(Object o) {
        return o instanceof Number ? ((Number) o).longValue() : 0L;
    }
}
