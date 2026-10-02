package dev.marie.framework.network;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.api.registry.GenericStateSyncHandlerRegistry;
import dev.marie.framework.core.MarieCore;
import dev.marie.framework.runtime.SourceRegistry;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Registers MarieLib's own generic sync packet channel and dispatches inbound
 * {@link GenericStateSyncPayload} instances to whatever handlers consuming mods registered via
 * {@link dev.marie.framework.api.marieapi.MarieAPI#registerGenericStateSyncHandler}.
 *
 * <p>Internal — consuming mods never call this directly.</p>
 */
@ApiStatus.Internal
public final class MarieNetworking {

    /** Generous ceiling for this small control-surface payload — not bulk data transfer. */
    private static final int MAX_PAYLOAD_BYTES = 4096;

    /** Package-private accessor so {@link GenericStateSyncPayload}'s decoder can enforce the same ceiling. */
    static int maxPayloadBytes() {
        return MAX_PAYLOAD_BYTES;
    }

    private static final int MAX_PACKETS_PER_SECOND = 20;
    private static final long RATE_LIMIT_WINDOW_MS = 1000L;

    /** Per-player {windowStartMs, countInWindow}, touched only on the main server thread. */
    private static final ConcurrentHashMap<UUID, long[]> RATE_LIMIT_STATE = new ConcurrentHashMap<>();

    private MarieNetworking() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(MarieNetworking::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MarieCore.MOD_ID).versioned("1");
        registrar.playToServer(
                GenericStateSyncPayload.TYPE,
                GenericStateSyncPayload.STREAM_CODEC,
                MarieNetworking::handleServer);
        registrar.playToClient(
                SourceClassificationSyncPayload.TYPE,
                SourceClassificationSyncPayload.STREAM_CODEC,
                MarieNetworking::handleSourceClassificationSync);
    }

    private static void handleSourceClassificationSync(SourceClassificationSyncPayload payload, IPayloadContext context) {
        // Integrated/LAN host shares the server's SourceRegistry statics; replacing them would race the server thread.
        if (context.connection().isMemoryConnection()) {
            return;
        }
        context.enqueueWork(() -> SourceRegistry.replaceFromServerSync(payload.classifications()));
    }

    /** Sends the server's external classifications to a remote player; no-op for the integrated host. */
    public static void sendSourceClassifications(ServerPlayer player) {
        if (player.connection.getConnection().isMemoryConnection()) {
            return;
        }
        PacketDistributor.sendToPlayer(player,
                new SourceClassificationSyncPayload(SourceRegistry.snapshotExternalClassifications()));
    }

    private static void handleServer(GenericStateSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                if (payload.oversized()) {
                    MarieCore.LOGGER.debug(
                            "[MarieLib] Ignoring oversized GenericStateSyncPayload from {} (> {} bytes)",
                            serverPlayer.getGameProfile().getName(), MAX_PAYLOAD_BYTES);
                    return;
                }
                if (!serverPlayer.level().isLoaded(payload.pos())) {
                    MarieCore.LOGGER.debug(
                            "[MarieLib] Ignoring GenericStateSyncPayload from {} for unloaded chunk at {}",
                            serverPlayer.getGameProfile().getName(), payload.pos());
                    return;
                }
                if (!serverPlayer.canInteractWithBlock(payload.pos(), 0.0)) {
                    MarieCore.LOGGER.debug(
                            "[MarieLib] Ignoring GenericStateSyncPayload from {} for out-of-reach position {}",
                            serverPlayer.getGameProfile().getName(), payload.pos());
                    return;
                }
                if (exceedsRateLimit(serverPlayer.getUUID())) {
                    MarieCore.LOGGER.debug(
                            "[MarieLib] Ignoring GenericStateSyncPayload from {} — rate limit exceeded",
                            serverPlayer.getGameProfile().getName());
                    return;
                }
                for (BiConsumer<ServerPlayer, GenericStateSyncPayload> handler
                        : GenericStateSyncHandlerRegistry.getAll()) {
                    handler.accept(serverPlayer, payload);
                }
            }
        });
    }

    private static boolean exceedsRateLimit(UUID playerId) {
        long now = System.currentTimeMillis();
        long[] state = RATE_LIMIT_STATE.computeIfAbsent(playerId, k -> new long[] {now, 0L});
        if (now - state[0] >= RATE_LIMIT_WINDOW_MS) {
            state[0] = now;
            state[1] = 0L;
        }
        state[1]++;
        return state[1] > MAX_PACKETS_PER_SECOND;
    }

    /** Called on player logout to prevent {@link #RATE_LIMIT_STATE} from growing unboundedly. */
    @ApiStatus.Internal
    public static void clearRateLimitState(UUID playerId) {
        RATE_LIMIT_STATE.remove(playerId);
    }
}
