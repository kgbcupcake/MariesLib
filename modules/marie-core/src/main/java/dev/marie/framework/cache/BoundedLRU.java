package dev.marie.framework.cache;

import dev.marie.framework.api.ApiStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thread-safe bounded LRU cache backed by a synchronized access-order {@link LinkedHashMap}.
 * Evicts the eldest entry once size exceeds {@value #MAX}.
 */
@ApiStatus.Internal
public final class BoundedLRU<K, V> {

    private static final int MAX = 2048;

    private final Map<K, V> inner = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                    return size() > MAX;
                }
            }
    );

    public V get(K key) { return inner.get(key); }

    public V put(K key, V value) { return inner.put(key, value); }

    public int size() { return inner.size(); }

    public void clear() { inner.clear(); }
}
