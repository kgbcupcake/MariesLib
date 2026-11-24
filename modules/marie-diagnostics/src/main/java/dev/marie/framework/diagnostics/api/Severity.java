package dev.marie.framework.diagnostics.api;

import dev.marie.framework.api.ApiStatus;

@ApiStatus.Experimental
public enum Severity {
    WARNING,
    ERROR,
    FATAL
}
