package dev.marie.framework.classification;

import dev.marie.framework.api.ApiStatus;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Immutable record representing a single step in the classification trace.
 */
@ApiStatus.Internal
public record ClassificationTraceStep(
        TraceStepId id,
        TraceStepStatus status,
        String message,
        @Nullable Map<String, Object> detail
) {
    public ClassificationTraceStep {
        if (detail != null && !detail.isEmpty()) {
            detail = Map.copyOf(detail);
        } else {
            detail = Map.of();
        }
    }

    public ClassificationTraceStep(TraceStepId id, TraceStepStatus status, String message) {
        this(id, status, message, null);
    }

    public Map<String, Object> detail() {
        return detail == null ? Map.of() : detail;
    }
}
