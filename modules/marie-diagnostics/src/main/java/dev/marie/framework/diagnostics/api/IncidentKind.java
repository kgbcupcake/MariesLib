package dev.marie.framework.diagnostics.api;

import dev.marie.framework.api.ApiStatus;

@ApiStatus.Experimental
public enum IncidentKind {
    MINECRAFT_CRASH,
    REPORTED_FAILURE
}
