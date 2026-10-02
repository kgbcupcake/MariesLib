package dev.marie.framework.handler;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.api.marieapi.MarieAPIState;
import dev.marie.framework.color.ColorDefinitionRegistry;
import dev.marie.framework.core.MarieContext;
import dev.marie.framework.core.MarieCore;
import dev.marie.framework.core.MarieModRegistry;
import dev.marie.framework.data.MarieDataManager;
import dev.marie.framework.network.MarieNetworking;
import dev.marie.framework.registry.RegistryLifecycleManager;
import dev.marie.framework.tracking.tracker.registry.TrackerRegistry;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@ApiStatus.Internal
public class ReloadGuardListener {

    private static volatile boolean reloadInProgress;

    public static boolean isReloadInProgress() {
        return reloadInProgress || RegistryLifecycleManager.isReloadInProgress();
    }

    /**
     * Also invokes {@link #reloadAndBroadcast}: {@code AddReloadListenerEvent} resets
     * {@code TrackerRegistry}/{@code ColorDefinitionRegistry} on every world/server boot (see
     * {@link dev.marie.framework.registry.MarieApiRegistries#onDatapackApplyBegin}), but that
     * boot-time reload pass runs before any {@link MinecraftServer} instance exists (it happens
     * inside {@code WorldLoader.load}, called from {@code Main.main} ahead of server construction)
     * — so the reregistration hook cannot fire from there. {@code ServerStartingEvent} is the first
     * point after boot with both a valid server reference and a guarantee that the initial reload
     * pass has already completed, making it the correct place to close that gap. Explicit
     * {@code /reload} is covered separately by {@link #onDatapackSync}; the two together cover
     * every reset cycle {@code MarieApiRegistries} performs, with no overlap.
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        reloadInProgress = true;
        try {
            ReloadPipeline.reloadAll();
        } finally {
            reloadInProgress = false;
        }
        reloadAndBroadcast(event.getServer());
    }

    /**
     * Invokes {@link MarieContext#reloadBroadcastHook()}, the "reload happened, please
     * re-register your trackers/colors" hook. Called automatically after every resource reload
     * (vanilla {@code /reload} or a mod's own reload command) via {@link #onDatapackSync}, and
     * after every world/server boot via {@link #onServerStarting}; exposed here for callers that
     * trigger a reload through a path this listener cannot observe.
     *
     * <p>{@code TrackerRegistry}/{@code ColorDefinitionRegistry} are frozen by the time this runs
     * (see {@link dev.marie.framework.registry.MarieApiRegistries#onDatapackApplyEnd}), so both are
     * briefly unfrozen for the duration of the hook call to allow {@code registerTracker}/
     * {@code registerColor} re-registration. {@link MarieAPIState}'s registration window is closed
     * again by the same point (its {@code DatapackReloadScope} closes at the end of the datapack
     * apply pass that triggered this), so it is explicitly reopened here too rather than assumed —
     * callers of this hook (e.g. {@code MarieAPI.registerTracker}) assert the window is open and
     * would otherwise throw "Registration closed". Both the registries and the phase are restored
     * to their prior state in a {@code finally} block regardless of whether the hook throws.</p>
     *
     * <p>Both registries are shared across every attached mod, so every mod's hook runs — not just
     * the last-attached one — so each mod gets the chance to re-register its own trackers/colors.</p>
     */
    public static void reloadAndBroadcast(MinecraftServer server) {
        if (MarieContext.isRegistered()) {
            TrackerRegistry.unfreezeInternal();
            ColorDefinitionRegistry.unfreezeInternal();
            try (MarieAPIState.DatapackReloadScope scope = MarieAPIState.openForDatapackReload()) {
                MarieModRegistry.forEach(modCtx ->
                        MarieContext.runAs(modCtx, () -> modCtx.reloadBroadcastHook().accept(server)));
            } finally {
                TrackerRegistry.freezeInternal();
                ColorDefinitionRegistry.freezeInternal();
            }
        }
    }

    /**
     * Fires after every resource reload completes — {@code event.getPlayer() == null} indicates a
     * full reload (vanilla {@code /reload} or a mod's own reload command), as opposed to a single
     * player's join-time datapack sync. This runs strictly after {@link MinecraftServer#reloadResources}'s
     * returned future resolves, which is after {@code MarieApiRegistries}' reload-scoped resets
     * ({@code TrackerRegistry}/{@code ColorDefinitionRegistry} included) have already completed —
     * making this the safe point for consumers to re-register.
     */
    @SubscribeEvent
    public void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            return;
        }
        reloadAndBroadcast(event.getPlayerList().getServer());
    }

    /**
     * Pushes the server's source classifications to remote clients on join and after every reload.
     * LOWEST so it runs after {@link #onDatapackSync} and any consumer's re-registration.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDatapackSyncClassifications(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(MarieNetworking::sendSourceClassifications);
    }

    /**
     * Item tags are bound only after every reload listener's apply() has run, so datapack
     * {@code "tag"} source classifications are queued during apply and expanded here instead.
     * Client-side tag syncs are ignored: the loader never runs on a remote client.
     */
    @SubscribeEvent
    public void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            return;
        }
        MarieDataManager.resolvePendingTagClassifications();
    }

    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        MarieDataManager.registerReloadListener(event);
        event.addListener((preparationBarrier, resourceManager, profilerFiller, profilerFiller2, executor, executor2) ->
                preparationBarrier.wait(net.minecraft.util.Unit.INSTANCE).thenRunAsync(() -> {
                    reloadInProgress = true;
                    try {
                        RegistryLifecycleManager.loadAll(resourceManager);
                        MarieCore.LOGGER.info("[MarieLib] Datapack config reload complete");
                    } finally {
                        reloadInProgress = false;
                    }
                }, executor2)
        );
    }
}
