package dev.marie.framework.api.marieapi;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.handler.SourceApplicationPipeline;
import dev.marie.framework.tracking.TrackingAttachment;
import dev.marie.framework.tracking.TrackingData;
import dev.marie.framework.api.value.ValueSourceTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

@ApiStatus.Stable
final class SourceTriggerFiringDelegate {

    private SourceTriggerFiringDelegate() {}

    static void fireSourceTrigger(ServerPlayer player, ValueSourceTrigger trigger) {
        fireSourceTrigger(player, trigger, null);
    }

    static void fireSourceTrigger(ServerPlayer player, ValueSourceTrigger trigger, @Nullable ItemStack stack) {
        if (player == null || trigger == null) return;
        if (!TrackingAttachment.isRegistered()) return;
        TrackingData tracking = TrackingAttachment.getData(player);
        long gameTimeMs = player.level().getGameTime() * 50L;
        tracking.tickTime(gameTimeMs);
        tracking.tick();
        SourceApplicationPipeline.process(player, trigger, stack, tracking, gameTimeMs);
    }
}
