package dev.marie.framework.diagnostics.report;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.diagnostics.api.FailureCategory;
import dev.marie.framework.diagnostics.api.IncidentKind;
import dev.marie.framework.diagnostics.api.Severity;
import dev.marie.framework.diagnostics.capture.StackAttribution;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@ApiStatus.Internal
public record IncidentRequest(
        String id,
        Instant timestamp,
        IncidentKind kind,
        String reportingMod,
        String storageMod,
        @Nullable String component,
        @Nullable String subsystem,
        FailureCategory category,
        Severity severity,
        @Nullable Throwable failure,
        @Nullable String description,
        @Nullable StackTraceElement[] reportSite,
        StackAttribution.Result attribution,
        Map<String, String> context,
        boolean contextTruncated,
        boolean runProviders,
        @Nullable String crashReport,
        @Nullable String reportType,
        int occurrence,
        List<String> limitations
) {}
