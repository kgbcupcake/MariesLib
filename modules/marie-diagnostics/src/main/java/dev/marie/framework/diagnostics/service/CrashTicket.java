package dev.marie.framework.diagnostics.service;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.diagnostics.capture.StackAttribution;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.List;

@ApiStatus.Internal
public record CrashTicket(
        String id,
        Instant timestamp,
        String storageMod,
        String fileName,
        String displayPath,
        @Nullable Throwable failure,
        StackAttribution.Result attribution,
        List<String> limitations
) {}
