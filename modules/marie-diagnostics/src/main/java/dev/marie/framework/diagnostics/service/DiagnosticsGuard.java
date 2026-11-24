package dev.marie.framework.diagnostics.service;

import dev.marie.framework.api.ApiStatus;

@ApiStatus.Internal
public final class DiagnosticsGuard {

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private DiagnosticsGuard() {}

    public static boolean isActive() {
        return DEPTH.get() > 0;
    }

    public static void run(Runnable action) {
        DEPTH.set(DEPTH.get() + 1);
        try {
            action.run();
        } finally {
            int depth = DEPTH.get() - 1;
            if (depth <= 0) {
                DEPTH.remove();
            } else {
                DEPTH.set(depth);
            }
        }
    }
}
