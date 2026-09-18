package com.usmconnect.service;

import com.usmconnect.db.JsonStore;

import java.util.*;

public final class MatchService {

    private final JsonStore store;

    public MatchService(JsonStore store) {
        this.store = store;
    }

    /**
     * Compatibility scoring:
     *   - shared hobbies : 6 pts each
     *   - shared games   : 6 pts each
     *   - shared foods   : 8 pts each  (favorite Filipino dish carries a bit more weight)
     *   - same course    : 15 pts
     *   - same college    : 8 pts (only if not same course, to avoid double counting)
     *   - same year level: 7 pts
     * Score is capped at 99% (100% is reserved to feel special / rare) and
     * floored at 5% so results never look insultingly close to zero.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> computeCompatibility(Map<String, Object> a, Map<String, Object> b) {
        List<String> sharedHobbies = intersect((List<Object>) a.get("hobbies"), (List<Object>) b.get("hobbies"));
        List<String> sharedGames = intersect((List<Object>) a.get("games"), (List<Object>) b.get("games"));
        List<String> sharedFoods = intersect((List<Object>) a.get("foods"), (List<Object>) b.get("foods"));

        int score = 0;
        score += sharedHobbies.size() * 6;
        score += sharedGames.size() * 6;
        score += sharedFoods.size() * 8;

        boolean sameCourse = a.get("course") != null && Objects.equals(a.get("course"), b.get("course"));
        boolean sameCollege = a.get("college") != null && Objects.equals(a.get("college"), b.get("college"));
        boolean sameYear = a.get("yearLevel") != null && Objects.equals(a.get("yearLevel"), b.get("yearLevel"));

        if (sameCourse) score += 15;
        else if (sameCollege) score += 8;
        if (sameYear) score += 7;

        score = Math.max(5, Math.min(99, score));

        List<String> reasons = new ArrayList<>();
        for (String h : sharedHobbies) reasons.add(h);
        for (String g : sharedGames) reasons.add(g);
        for (String f : sharedFoods) reasons.add(f + " 🍽");
        if (sameCourse) reasons.add("Same course: " + a.get("course"));
        else if (sameCollege) reasons.add("Same college: " + a.get("college"));
        if (sameYear) reasons.add("Both " + a.get("yearLevel"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", score);
        result.put("commonInterests", reasons);
        return result;
    }

    private List<String> intersect(List<Object> x, List<Object> y) {
        List<String> out = new ArrayList<>();
        if (x == null || y == null) return out;
        Set<String> setY = new HashSet<>();
        for (Object o : y) setY.add(String.valueOf(o));
        Set<String> seen = new HashSet<>();
        for (Object o : x) {
            String s = String.valueOf(o);
            if (setY.contains(s) && seen.add(s)) out.add(s);
        }
        return out;
    }

    // ---- likes / passes / matches ----

    public boolean hasActedOn(String fromUserId, String toUserId) {
        for (Map<String, Object> like : store.readAll("likes")) {
            if (fromUserId.equals(like.get("from")) && toUserId.equals(like.get("to"))) return true;
        }
        for (Map<String, Object> pass : store.readAll("passes")) {
            if (fromUserId.equals(pass.get("from")) && toUserId.equals(pass.get("to"))) return true;
        }
        return false;
    }

    public void recordPass(String fromUserId, String toUserId) {
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("from", fromUserId);
        rec.put("to", toUserId);
        rec.put("createdAt", System.currentTimeMillis());
        store.append("passes", rec);
    }

    /** Records a like. Returns true if this creates a mutual match. */
    public boolean recordLike(String fromUserId, String toUserId) {
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("from", fromUserId);
        rec.put("to", toUserId);
        rec.put("createdAt", System.currentTimeMillis());
        store.append("likes", rec);

        boolean reciprocal = false;
        for (Map<String, Object> like : store.readAll("likes")) {
            if (toUserId.equals(like.get("from")) && fromUserId.equals(like.get("to"))) {
                reciprocal = true;
                break;
            }
        }
        if (reciprocal && !alreadyMatched(fromUserId, toUserId)) {
            Map<String, Object> match = new LinkedHashMap<>();
            match.put("id", UUID.randomUUID().toString());
            match.put("userA", fromUserId);
            match.put("userB", toUserId);
            match.put("createdAt", System.currentTimeMillis());
            store.append("matches", match);
            return true;
        }
        return reciprocal;
    }

    private boolean alreadyMatched(String u1, String u2) {
        for (Map<String, Object> m : store.readAll("matches")) {
            String a = String.valueOf(m.get("userA"));
            String b = String.valueOf(m.get("userB"));
            if ((a.equals(u1) && b.equals(u2)) || (a.equals(u2) && b.equals(u1))) return true;
        }
        return false;
    }

    /** Public check: are these two users a mutual match? Used by ChatService's permission check. */
    public boolean isMatched(String u1, String u2) {
        return alreadyMatched(u1, u2);
    }

    public List<String> matchedUserIds(String userId) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> m : store.readAll("matches")) {
            String a = String.valueOf(m.get("userA"));
            String b = String.valueOf(m.get("userB"));
            if (a.equals(userId)) out.add(b);
            else if (b.equals(userId)) out.add(a);
        }
        return out;
    }

    public Set<String> excludedUserIds(String userId) {
        Set<String> excluded = new HashSet<>();
        for (Map<String, Object> like : store.readAll("likes")) {
            if (userId.equals(like.get("from"))) excluded.add(String.valueOf(like.get("to")));
        }
        for (Map<String, Object> pass : store.readAll("passes")) {
            if (userId.equals(pass.get("from"))) excluded.add(String.valueOf(pass.get("to")));
        }
        for (Map<String, Object> block : store.readAll("blocks")) {
            String blocker = String.valueOf(block.get("blocker"));
            String blocked = String.valueOf(block.get("blocked"));
            if (userId.equals(blocker)) excluded.add(blocked);
            if (userId.equals(blocked)) excluded.add(blocker); // mutual: they don't see you either
        }
        return excluded;
    }

    /** Blocking is mutual and permanent: neither user will see the other again. */
    public void recordBlock(String blockerId, String blockedId) {
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("blocker", blockerId);
        rec.put("blocked", blockedId);
        rec.put("createdAt", System.currentTimeMillis());
        store.append("blocks", rec);
    }

    public void recordReport(String reporterId, String reportedId, String reason) {
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("reporter", reporterId);
        rec.put("reported", reportedId);
        rec.put("reason", reason);
        rec.put("createdAt", System.currentTimeMillis());
        rec.put("status", "open");
        store.append("reports", rec);
    }
}
