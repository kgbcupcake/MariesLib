package dev.marie.framework.diagnostics.api;

import dev.marie.framework.api.ApiStatus;

/** Adds a mod's own information to reports. Runs off the game thread, read-only, within {@link DiagnosticSection#TIME_LIMIT_MS} ms. */
@ApiStatus.Experimental
@FunctionalInterface
public interface DiagnosticProvider {

    void contribute(ProviderContext context, DiagnosticSection section) throws Exception;
}
