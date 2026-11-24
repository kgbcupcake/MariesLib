package dev.marie.framework.diagnostics.runtime;

import dev.marie.framework.api.ApiStatus;

import java.util.concurrent.atomic.AtomicBoolean;

@ApiStatus.Internal
public final class DiagnosticsRuntimeHooks {

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private DiagnosticsRuntimeHooks() {}

    public static void installShutdownHook() {
        if (INSTALLED.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(DiagnosticsRuntime::shutdown,
                    "marieslib-diagnostics-shutdown"));
        }
    }
}
