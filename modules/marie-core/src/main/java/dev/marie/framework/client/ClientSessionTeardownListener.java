package dev.marie.framework.client;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.core.MarieCore;
import dev.marie.framework.runtime.SourceRegistry;
import dev.marie.framework.tracking.tracker.ClientTrackerCache;

import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Client-side session teardown: clears transient, per-connection client caches when the local
 * player disconnects from a server (single-player world close included), so a subsequent
 * connection starts from a clean slate instead of briefly showing the previous session's state.
 *
 * <p>Domain-agnostic and lives entirely in marie-core. The two marie-core client caches
 * ({@link ClientTrackerCache}) are cleared directly; downstream modules (e.g. marie-ui) contribute
 * their own teardown steps via {@link #registerAdditionalTeardown(Runnable)} — marie-core cannot
 * reference their types, but it can invoke a {@link Runnable} they hand it.</p>
 *
 * <p>{@link #register()} must be called once, client-side only (this class references the
 * client-only {@link ClientPlayerNetworkEvent}, so it must never be loaded on a dedicated
 * server).</p>
 */
@ApiStatus.Internal
public final class ClientSessionTeardownListener {

    private static final List<Runnable> ADDITIONAL_TEARDOWN = new CopyOnWriteArrayList<>();

    private static volatile boolean registered;

    private ClientSessionTeardownListener() {}

    /**
     * Registers an extra teardown step to run on client disconnect, after the marie-core caches
     * are cleared. Intended for other modules whose client caches marie-core cannot see directly.
     * Safe to call before or after {@link #register()}; duplicate runnables are not de-duplicated.
     *
     * @param teardown the step to run; exceptions it throws are logged and do not block the others
     */
    public static void registerAdditionalTeardown(Runnable teardown) {
        if (teardown != null) {
            ADDITIONAL_TEARDOWN.add(teardown);
        }
    }

    /** Wires the disconnect listener into the client event bus; safe to call more than once. */
    public static void register() {
        if (registered) {
            return;
        }
        synchronized (ClientSessionTeardownListener.class) {
            if (registered) {
                return;
            }
            NeoForge.EVENT_BUS.addListener(ClientSessionTeardownListener::onLoggingOut);
            registered = true;
        }
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientTrackerCache.clear();
        SourceRegistry.discardServerSync();
        for (Runnable teardown : ADDITIONAL_TEARDOWN) {
            try {
                teardown.run();
            } catch (RuntimeException e) {
                MarieCore.LOGGER.warn("[MarieLib] Client session teardown step failed", e);
            }
        }
    }
}
