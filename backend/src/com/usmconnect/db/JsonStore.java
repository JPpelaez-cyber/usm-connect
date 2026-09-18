package com.usmconnect.db;

import com.usmconnect.util.JsonUtil;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * File-based persistence layer.
 *
 * This plays the role of "the database" for USM Connect. Each collection
 * (users, likes, matches, reports) is a JSON array of JSON objects stored in
 * its own file under backend/data/. Reads/writes are synchronized with a
 * read-write lock so concurrent requests don't corrupt the file, and every
 * write is flushed to disk immediately, so data survives a server restart
 * (requirement #16: "Data persists after restarting the server").
 *
 * Swapping this for a real relational database (MySQL/Postgres/SQLite) later
 * is a matter of re-implementing this one class with JDBC calls -- nothing
 * in the handler/service layer needs to change, since they only talk to the
 * methods below.
 */
public final class JsonStore {

    private final Path dir;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public JsonStore(Path dataDir) {
        this.dir = dataDir;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // ---- generic collection helpers ----

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> readAll(String collection) {
        lock.readLock().lock();
        try {
            Path f = dir.resolve(collection + ".json");
            if (!Files.exists(f)) return new ArrayList<>();
            String json = new String(Files.readAllBytes(f), "UTF-8");
            Object parsed = JsonUtil.parse(json);
            if (parsed instanceof List) {
                List<Object> raw = (List<Object>) parsed;
                List<Map<String, Object>> out = new ArrayList<>();
                for (Object o : raw) out.add((Map<String, Object>) o);
                return out;
            }
            return new ArrayList<>();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void writeAll(String collection, List<Map<String, Object>> records) {
        lock.writeLock().lock();
        try {
            Path f = dir.resolve(collection + ".json");
            Files.write(f, JsonUtil.write(records).getBytes("UTF-8"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Appends one record to a collection, atomically w.r.t. other JsonStore calls. */
    public void append(String collection, Map<String, Object> record) {
        lock.writeLock().lock();
        try {
            List<Map<String, Object>> all = readAllUnlocked(collection);
            all.add(record);
            Path f = dir.resolve(collection + ".json");
            Files.write(f, JsonUtil.write(all).getBytes("UTF-8"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Replaces the record matching idField==idValue with `updated`. */
    public boolean update(String collection, String idField, String idValue, Map<String, Object> updated) {
        lock.writeLock().lock();
        try {
            List<Map<String, Object>> all = readAllUnlocked(collection);
            boolean found = false;
            for (int i = 0; i < all.size(); i++) {
                if (Objects.equals(String.valueOf(all.get(i).get(idField)), idValue)) {
                    all.set(i, updated);
                    found = true;
                    break;
                }
            }
            if (found) {
                Path f = dir.resolve(collection + ".json");
                Files.write(f, JsonUtil.write(all).getBytes("UTF-8"));
            }
            return found;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readAllUnlocked(String collection) throws IOException {
        Path f = dir.resolve(collection + ".json");
        if (!Files.exists(f)) return new ArrayList<>();
        String json = new String(Files.readAllBytes(f), "UTF-8");
        Object parsed = JsonUtil.parse(json);
        List<Map<String, Object>> out = new ArrayList<>();
        if (parsed instanceof List) {
            for (Object o : (List<Object>) parsed) out.add((Map<String, Object>) o);
        }
        return out;
    }

    public Path getUploadsDir() {
        return dir.resolveSibling("uploads");
    }
}
