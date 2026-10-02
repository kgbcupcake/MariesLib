package dev.marie.framework.handler;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.config.FeatureFlagCache;
import dev.marie.framework.core.IMarieConfig;
import dev.marie.framework.core.MarieContext;
import dev.marie.framework.core.MarieModRegistry;
import dev.marie.framework.core.KubeIntegration;
import dev.marie.framework.network.MarieNetworking;
import dev.marie.framework.tracking.DiminishingReturnsConfig;
import dev.marie.framework.tracking.TrackingAttachment;
import dev.marie.framework.tracking.TrackingData;
import dev.marie.framework.tracking.TrackingResetSupport;
import dev.marie.framework.tracking.tracker.TrackerManager;
import dev.marie.framework.tracking.tracker.network.TrackerNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@ApiStatus.Internal
public class PlayerTrackingLifecycle {

    @SubscribeEvent
    public void onPlayerJoin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!TrackingAttachment.isRegistered()) return;
        TrackingData tracking = TrackingAttachment.getData(player);
        tracking.tick();
        TrackingAttachment.setData(player, tracking);
        tracking.setMemoryConfig(DiminishingReturnsSupport.resolveMemoryConfig());
        TrackerManager.openSessionTrackers(player, tracking);
        TrackerNetworking.sendFullSnapshot(player, tracking);
        if (MarieContext.isRegistered()) {
            boolean effectsEnabled = FeatureFlagCache.enableEffects();
            MarieModRegistry.forEach(modCtx -> {
                modCtx.syncOnJoin().accept(player);
                if (effectsEnabled) {
                    modCtx.effectApplier().accept(player, tracking);
                }
                if (modCtx.showJoinMessage()) {
                    player.sendSystemMessage(modCtx.joinMessageLine1());
                    player.sendSystemMessage(modCtx.joinMessageLine2());
                }
            });
            KubeIntegration.firePlayerSynced(player);
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!TrackingAttachment.isRegistered()) return;
        TrackingData tracking = TrackingAttachment.getData(player);
        tracking.tick();
        TrackingAttachment.setData(player, tracking);
        tracking.setMemoryConfig(DiminishingReturnsSupport.resolveMemoryConfig());
        TrackingResetSupport.applyRespawnValueBehavior(player, tracking);
        if (MarieContext.isRegistered()) {
            boolean effectsEnabled = FeatureFlagCache.enableEffects();
            MarieModRegistry.forEach(modCtx -> {
                modCtx.syncOnJoin().accept(player);
                if (effectsEnabled) {
                    modCtx.effectApplier().accept(player, tracking);
                }
            });
            KubeIntegration.firePlayerSynced(player);
        }
    }

    @SubscribeEvent
    public void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        DiminishingReturnsSupport.resetMemoryConfigWarning();
    }

    @SubscribeEvent
    public void onPlayerLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        TrackerManager.clearDirtySyncState(player.getUUID());
        MarieNetworking.clearRateLimitState(player.getUUID());
    }

    @SubscribeEvent
    public void onPlayerChangeDimension(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!TrackingAttachment.isRegistered()) return;
        TrackingData tracking = TrackingAttachment.getData(player);
        if (MarieContext.isRegistered()) {
            boolean effectsEnabled = FeatureFlagCache.enableEffects();
            MarieModRegistry.forEach(modCtx -> {
                modCtx.trackingDeltaSyncer().accept(player, tracking);
                if (effectsEnabled) {
                    modCtx.effectApplier().accept(player, tracking);
                }
            });
            KubeIntegration.firePlayerSynced(player);
        }
    }
}
