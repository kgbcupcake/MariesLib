package dev.marie.framework.diagnostics.api;

import dev.marie.framework.api.ApiStatus;

import javax.annotation.Nullable;

@ApiStatus.Experimental
public record ProviderContext(
        String incidentId,
        IncidentKind kind,
        String reportingMod,
        @Nullable String component,
        FailureCategory category
) {}
