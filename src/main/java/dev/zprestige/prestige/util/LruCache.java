package dev.zprestige.prestige.util;

import java.util.LinkedHashMap;
import java.util.Map.Entry;

public class LruCache extends LinkedHashMap {
    public final int maxSize;

    public LruCache(int initialCapacity, float initialLoadFactor, boolean orderByAccess, int maxEntries) {
        super(initialCapacity, initialLoadFactor, orderByAccess);
        this.maxSize = maxEntries;
    }

    @Override
    public boolean removeEldestEntry(Entry eldest) {
        return this.size() > this.maxSize;
    }
}
