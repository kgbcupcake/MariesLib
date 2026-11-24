package dev.marie.framework.diagnostics.service;

import dev.marie.framework.api.ApiStatus;

import java.util.HashMap;
import java.util.Map;

@ApiStatus.Internal
public final class SubmissionLimiter {

    static final int MAX_TRACKED_KEYS = 1024;

    public record Admission(boolean allowed, int occurrence, String reason) {}

    private final int maxPerSession;
    private final int maxDuplicates;
    private final Map<String, Integer> seen = new HashMap<>();
    private int written;

    public SubmissionLimiter(int maxPerSession, int maxDuplicates) {
        this.maxPerSession = maxPerSession;
        this.maxDuplicates = maxDuplicates;
    }

    public synchronized Admission admit(String key) {
        Integer previous = seen.get(key);
        if (previous == null && seen.size() >= MAX_TRACKED_KEYS) {
            return new Admission(false, 1, "too many distinct failures this session");
        }
        int occurrence = previous == null ? 1 : previous + 1;
        seen.put(key, occurrence);
        if (occurrence > maxDuplicates) {
            return new Admission(false, occurrence, "same failure already written " + maxDuplicates + " time(s)");
        }
        if (written >= maxPerSession) {
            return new Admission(false, occurrence, "session limit of " + maxPerSession + " incidents reached");
        }
        written++;
        return new Admission(true, occurrence, "");
    }
}
