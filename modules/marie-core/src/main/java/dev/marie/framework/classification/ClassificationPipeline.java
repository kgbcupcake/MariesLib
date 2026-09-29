package dev.marie.framework.classification;

import dev.marie.framework.api.ApiStatus;

// Called by ItemClassifier for each item — do not call directly from addon code.

/**
 * Identifies which pipeline produced a classification trace.
 */
@ApiStatus.Internal
public enum ClassificationPipeline {
    RUNTIME,
    SCANNER,
    HELD_DEBUG
}
