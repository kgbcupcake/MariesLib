package dev.marie.framework.diagnostics.api;

import dev.marie.framework.api.ApiStatus;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded key/value output of one provider. */
@ApiStatus.Experimental
public final class DiagnosticSection {

    public static final int MAX_ENTRIES = 64;
    public static final int MAX_VALUE_CHARS = 1024;
    public static final int MAX_TOTAL_CHARS = 8192;
    public static final long TIME_LIMIT_MS = 250L;

    private final Map<String, String> entries = new LinkedHashMap<>();
    private int totalChars;
    private boolean truncated;

    @ApiStatus.Internal
    public DiagnosticSection() {}

    public DiagnosticSection entry(String key, @Nullable Object value) {
        String k = cut(String.valueOf(key));
        String v = cut(String.valueOf(value));
        if (!entries.containsKey(k) && entries.size() >= MAX_ENTRIES) {
            truncated = true;
            return this;
        }
        int cost = k.length() + v.length();
        if (totalChars + cost > MAX_TOTAL_CHARS) {
            truncated = true;
            return this;
        }
        String previous = entries.put(k, v);
        if (previous != null) {
            totalChars -= k.length() + previous.length();
        }
        totalChars += cost;
        return this;
    }

    public Map<String, String> entries() {
        return Collections.unmodifiableMap(entries);
    }

    public boolean truncated() {
        return truncated;
    }

    private String cut(String s) {
        if (s.length() <= MAX_VALUE_CHARS) {
            return s;
        }
        truncated = true;
        return s.substring(0, MAX_VALUE_CHARS);
    }
}
