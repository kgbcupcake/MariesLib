package dev.marie.framework.cache;

import dev.marie.framework.api.ApiStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe mutable holder that accumulates per-value sums and produces an average map on demand.
 */
@ApiStatus.Stable
public final class RunningAverage {

    private volatile int count;
    private final ConcurrentHashMap<String, Float> sumPerValue = new ConcurrentHashMap<>();

    public void add(Map<String, Float> values) {
        for (Map.Entry<String, Float> e : values.entrySet()) {
            sumPerValue.merge(e.getKey(), e.getValue(), Float::sum);
        }
        count++;
    }

    public int count() { return count; }

    public Map<String, Float> average() {
        int c = count;
        if (c == 0) return Map.of();
        Map<String, Float> avg = new HashMap<>();
        for (Map.Entry<String, Float> e : sumPerValue.entrySet()) {
            avg.put(e.getKey(), e.getValue() / c);
        }
        return avg;
    }
}
