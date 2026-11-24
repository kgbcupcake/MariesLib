package dev.marie.framework.diagnostics.api;

import dev.marie.framework.api.ApiStatus;

@ApiStatus.Experimental
public enum FailureCategory {
    CRASH,
    DATA_LOADING,
    DATA_PROCESSING,
    REGISTRY,
    NETWORK_SYNC,
    CONFIGURATION,
    COMPATIBILITY,
    RENDERING,
    GAMEPLAY,
    OTHER
}
