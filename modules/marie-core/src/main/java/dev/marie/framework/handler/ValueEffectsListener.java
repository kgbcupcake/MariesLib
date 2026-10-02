package dev.marie.framework.handler;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.api.marieapi.MarieAPI;
import dev.marie.framework.api.registry.EffectRegistry;
import dev.marie.framework.api.value.ValueSourceTrigger;
import dev.marie.framework.config.FeatureFlagCache;
import dev.marie.framework.core.MarieContext;
import dev.marie.framework.core.MarieCore;
import dev.marie.framework.core.MarieModRegistry;
import dev.marie.framework.tracking.TrackingAttachment;
import dev.marie.framework.tracking.TrackingData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@ApiStatus.Internal
public class ValueEffectsListener {

    private static final int APPLY_INTERVAL_TICKS = 40;

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().getGameTime() % APPLY_INTERVAL_TICKS != 0) return;
        if (ReloadGuardListener.isReloadInProgress()) return;
        if (!TrackingAttachment.isRegistered()) return;
        if (!MarieContext.isRegistered()) return;

        fireStateTicks(player);

        if (FeatureFlagCache.enableEffects() && !player.isCreative() && !player.isSpectator()) {
            TrackingData data = TrackingAttachment.getData(player);
            MarieModRegistry.forEach(modCtx -> modCtx.effectApplier().accept(player, data));
            for (String oldId : EffectRegistry.legacyCleanupEffectIds()) {
                if (oldId == null || oldId.isBlank()) {
                    continue;
                }
                if (!EffectRegistry.isRegistered(oldId)) {
                    ResourceLocation effectId = ResourceLocation.tryParse(oldId);
                    if (effectId == null) {
                        continue;
                    }
                    BuiltInRegistries.MOB_EFFECT
                            .getHolder(effectId)
                            .ifPresent(player::removeEffect);
                }
            }
        } else {
            MarieModRegistry.forEach(modCtx -> modCtx.effectClearer().accept(player));
        }
    }

    private void fireStateTicks(ServerPlayer player) {
        if (player.isSprinting()) {
            if (FeatureFlagCache.enableDebugLogging()) {
                MarieCore.LOGGER.debug("[ValueEffectsListener] TICK fired: sprint player={}",
                        player.getName().getString());
            }
            MarieAPI.fireSourceTrigger(player, ValueSourceTrigger.tick("sprint"), ItemStack.EMPTY);
        }
        if (player.isSwimming() && player.isInWater()) {
            if (FeatureFlagCache.enableDebugLogging()) {
                MarieCore.LOGGER.debug("[ValueEffectsListener] TICK fired: swim player={}",
                        player.getName().getString());
            }
            MarieAPI.fireSourceTrigger(player, ValueSourceTrigger.tick("swim"), ItemStack.EMPTY);
        }
    }
}
