package dev.marie.framework.diagnostics.api;

import dev.marie.framework.api.ApiStatus;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Optional details attached to a reported failure. */
@ApiStatus.Experimental
public final class FailureSubmission {

    public static final int MAX_CONTEXT_ENTRIES = 32;
    public static final int MAX_TEXT_CHARS = 512;

    @Nullable private final String component;
    @Nullable private final String subsystem;
    private final FailureCategory category;
    private final Severity severity;
    private final Map<String, String> context;
    private final boolean runProviders;
    private final boolean contextTruncated;

    private FailureSubmission(Builder b) {
        this.component = b.component;
        this.subsystem = b.subsystem;
        this.category = b.category;
        this.severity = b.severity;
        this.context = Collections.unmodifiableMap(new LinkedHashMap<>(b.context));
        this.runProviders = b.runProviders;
        this.contextTruncated = b.contextTruncated;
    }

    @ApiStatus.Internal
    public static Builder builder() {
        return new Builder();
    }

    @Nullable public String component() { return component; }
    @Nullable public String subsystem() { return subsystem; }
    public FailureCategory category() { return category; }
    public Severity severity() { return severity; }
    public Map<String, String> context() { return context; }
    public boolean runProviders() { return runProviders; }
    public boolean contextTruncated() { return contextTruncated; }

    public static final class Builder {
        @Nullable private String component;
        @Nullable private String subsystem;
        private FailureCategory category = FailureCategory.OTHER;
        private Severity severity = Severity.ERROR;
        private final Map<String, String> context = new LinkedHashMap<>();
        private boolean runProviders = true;
        private boolean contextTruncated;

        private Builder() {}

        public Builder component(String component) {
            this.component = cut(component);
            return this;
        }

        public Builder subsystem(String subsystem) {
            this.subsystem = cut(subsystem);
            return this;
        }

        public Builder category(FailureCategory category) {
            this.category = Objects.requireNonNull(category, "category");
            return this;
        }

        public Builder severity(Severity severity) {
            this.severity = Objects.requireNonNull(severity, "severity");
            return this;
        }

        public Builder context(String key, @Nullable Object value) {
            String k = cut(String.valueOf(key));
            if (!context.containsKey(k) && context.size() >= MAX_CONTEXT_ENTRIES) {
                contextTruncated = true;
                return this;
            }
            context.put(k, cut(String.valueOf(value)));
            return this;
        }

        public Builder runProviders(boolean runProviders) {
            this.runProviders = runProviders;
            return this;
        }

        @ApiStatus.Internal
        public FailureSubmission build() {
            return new FailureSubmission(this);
        }

        private String cut(String s) {
            if (s == null) {
                return null;
            }
            if (s.length() > MAX_TEXT_CHARS) {
                contextTruncated = true;
                return s.substring(0, MAX_TEXT_CHARS);
            }
            return s;
        }
    }
}
