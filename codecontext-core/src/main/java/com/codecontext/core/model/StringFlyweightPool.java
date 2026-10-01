package com.codecontext.core.model;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe Flyweight pool for deduplicating repeated string instances (packages, types).
 */
public class StringFlyweightPool {

    private final ConcurrentMap<String, String> pool = new ConcurrentHashMap<>();

    /**
     * Returns the canonical interned representation of the string.
     * If the string is null, returns null.
     */
    public String intern(String str) {
        if (str == null) {
            return null;
        }
        return pool.computeIfAbsent(str, s -> s);
    }

    /**
     * Returns the total count of unique strings currently retained in the pool.
     */
    public int size() {
        return pool.size();
    }

    /**
     * Clears all cached strings from the pool.
     */
    public void clear() {
        pool.clear();
    }
}