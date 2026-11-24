package dev.marie.framework.diagnostics.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.diagnostics.registry.DiagnosticModRegistry;
import dev.marie.framework.diagnostics.runtime.DiagnosticsRuntime;
import dev.marie.framework.diagnostics.service.DiagnosticsService;

import java.util.Optional;
import java.util.function.Consumer;

/** Registers diagnostic profiles and reports failures. See docs/diagnostics.md. */
@ApiStatus.Experimental
public final class DiagnosticsAPI {

    private DiagnosticsAPI() {}

    public static void register(DiagnosticProfile profile) {
        DiagnosticModRegistry.register(profile);
    }

    public static boolean isRegistered(String modId) {
        return DiagnosticModRegistry.contains(modId);
    }

    public static Optional<String> reportFailure(String modId, Throwable failure) {
        return reportFailure(modId, failure, f -> {});
    }

    public static Optional<String> reportFailure(String modId, Throwable failure,
                                                 Consumer<FailureSubmission.Builder> details) {
        if (failure == null) {
            return reportFailure(modId, "reportFailure called with a null exception", details);
        }
        return submit(modId, failure, null, details);
    }

    public static Optional<String> reportFailure(String modId, String description,
                                                 Consumer<FailureSubmission.Builder> details) {
        return submit(modId, null, description == null ? "(no description)" : description, details);
    }

    private static Optional<String> submit(String modId, Throwable failure, String description,
                                           Consumer<FailureSubmission.Builder> details) {
        FailureSubmission.Builder builder = FailureSubmission.builder();
        try {
            if (details != null) {
                details.accept(builder);
            }
        } catch (Throwable t) {
            DiagnosticsService.LOG.warn("Failure details from {} threw; reporting without them", modId, t);
            builder = FailureSubmission.builder();
        }
        try {
            return DiagnosticsRuntime.service().submit(modId, failure, description, builder.build());
        } catch (Throwable t) {
            DiagnosticsService.LOG.warn("Could not submit a failure for {}", modId, t);
            return Optional.empty();
        }
    }
}
