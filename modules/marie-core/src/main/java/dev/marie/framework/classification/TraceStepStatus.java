package dev.marie.framework.classification;

import dev.marie.framework.api.ApiStatus;

/**
 * Status outcome of a single classification trace step.
 */
@ApiStatus.Internal
public enum TraceStepStatus {
    SUCCESS,
    FAILURE,
    SKIPPED,
    WARNING
}
