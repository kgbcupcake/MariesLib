package dev.marie.framework.api.marieapi;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.core.MarieCore;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@ApiStatus.Internal
public final class MarieAPIState {

    public enum Phase {
        MOD_INIT,
        DATAPACK_RELOAD,
        CLOSED
    }

    /**
     * Two independent windows, so a datapack-reload scope can neither reopen nor close the mod-init
     * window: {@code startupOpen} stays true until {@link #close()}, and {@code reloadDepth} counts
     * open {@link DatapackReloadScope}s (nested scopes are fine). Registration is allowed while
     * either is open; {@link #getPhase()} is derived from them.
     */
    private static final AtomicBoolean startupOpen = new AtomicBoolean(true);
    private static final AtomicInteger reloadDepth = new AtomicInteger();

    private MarieAPIState() {}

    public static boolean isRegistrationAllowed() {
        return startupOpen.get() || reloadDepth.get() > 0;
    }

    public static Phase getPhase() {
        if (reloadDepth.get() > 0) {
            return Phase.DATAPACK_RELOAD;
        }
        return startupOpen.get() ? Phase.MOD_INIT : Phase.CLOSED;
    }

    /**
     * Asserts registration is currently allowed.
     */
    public static void assertRegistrationAllowed(String context) {
        if (!isRegistrationAllowed()) {
            throw new IllegalStateException(
                    "[MarieAPI] Registration closed — " + context +
                    " must be called during mod initialization or datapack reload.");
        }
    }

    /**
     * Ends the mod-init registration window. Idempotent, and independent of any open
     * {@link DatapackReloadScope}: an open scope keeps registration allowed until it closes.
     */
    @ApiStatus.Internal
    public static void close() {
        if (startupOpen.getAndSet(false)) {
            MarieCore.LOGGER.info("[MarieLib] Registration phase: CLOSED");
        }
    }

    @ApiStatus.Internal
    public static DatapackReloadScope openForDatapackReload() {
        reloadDepth.incrementAndGet();
        MarieCore.LOGGER.info("[MarieLib] Registration phase: DATAPACK_RELOAD");
        return new DatapackReloadScope();
    }

    /** Test hook: back to a fresh MOD_INIT state. */
    static void resetForTests() {
        startupOpen.set(true);
        reloadDepth.set(0);
    }

    @ApiStatus.Experimental
    public static final class DatapackReloadScope implements AutoCloseable {
        private final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                reloadDepth.decrementAndGet();
                MarieCore.LOGGER.info("[MarieLib] Registration phase: {}", getPhase());
            }
        }
    }
}
