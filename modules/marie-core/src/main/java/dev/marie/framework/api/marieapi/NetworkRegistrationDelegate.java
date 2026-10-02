package dev.marie.framework.api.marieapi;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.api.registry.GenericStateSyncHandlerRegistry;
import dev.marie.framework.network.GenericStateSyncPayload;

import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;

@ApiStatus.Stable
final class NetworkRegistrationDelegate {

    private NetworkRegistrationDelegate() {}

    static void registerGenericStateSyncHandler(BiConsumer<ServerPlayer, GenericStateSyncPayload> handler) {
        MarieAPIState.assertRegistrationAllowed("registerGenericStateSyncHandler");
        if (handler == null) {
            throw new IllegalArgumentException("handler cannot be null");
        }
        GenericStateSyncHandlerRegistry.register(handler);
    }
}
