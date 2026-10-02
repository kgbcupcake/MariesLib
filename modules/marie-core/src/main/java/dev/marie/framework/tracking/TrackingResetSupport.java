package dev.marie.framework.tracking;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.api.registry.ValueRegistry;
import dev.marie.framework.core.IMarieConfig;
import dev.marie.framework.core.MarieContext;
import dev.marie.framework.core.MarieModRegistry;
import dev.marie.framework.handler.SourceApplicationPipeline;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

@ApiStatus.Internal
public final class TrackingResetSupport {

    private TrackingResetSupport() {}

    /**
     * Resets every registered value bar to {@code fill}. Does not clear application memory.
     *
     * @return {@code true} if any bar changed
     */
    public static boolean resetAllBarValues(ServerPlayer player, TrackingData tracking, float fill) {
        float clamped = Mth.clamp(fill, 0f, 1f);
        boolean changed = false;
        for (String key : MarieContext.get().valueKeys()) {
            if (SourceApplicationPipeline.writeDirectValue(player, tracking, key, clamped)) {
                changed = true;
            }
        }
        return changed;
    }

    /** Clears source/category/family memory and the accumulated value total. */
    public static void clearApplicationMemory(TrackingData tracking) {
        tracking.sourceMemory.clear();
        tracking.categoryMemory.clear();
        tracking.familyMemory.clear();
        tracking.total = 0f;
        tracking.lastTickTime = 0L;
    }

    public static float resolveStartingFill() {
        DiminishingReturnsConfig cfg = IMarieConfig.get().trackingMemoryConfig();
        if (cfg != null) {
            return Mth.clamp((float) cfg.startingValueFill(), 0f, 1f);
        }
        return 0.5f;
    }

    /**
     * Resets all bars to {@code fill} and clears application memory. Used by reset commands
     * and {@link RespawnValueBehavior#RESET_TO_STARTING} / {@link RespawnValueBehavior#VANILLA_HALF}.
     *
     * @return {@code true} if any bar changed
     */
    public static boolean resetAllValuesAndMemory(ServerPlayer player, TrackingData tracking, float fill) {
        clearApplicationMemory(tracking);
        return resetAllBarValues(player, tracking, fill);
    }

    /**
     * Applies death respawn policy from every attached mod's {@link MarieContext}, since each mod
     * owns its own subset of value keys and may want a different policy. When a mod has registered
     * a custom {@link MarieContext#respawnValueHandler()}, it fully replaces the enum policy for
     * that mod's values; otherwise that mod's {@link MarieContext#respawnValueBehavior()} applies
     * only to the value keys it owns (see {@link ValueRegistry#ownerModId(String)}), so one mod's
     * reset policy can't stomp another mod's values.
     *
     * <p>Application memory and the total aren't partitioned by mod, so they're only cleared when
     * no attached mod's policy is {@link RespawnValueBehavior#PRESERVE} — a resetting mod must not
     * wipe history a preserving mod relies on.</p>
     */
    public static void applyRespawnValueBehavior(ServerPlayer player, TrackingData tracking) {
        if (!MarieContext.isRegistered()) {
            return;
        }
        boolean anyPreserves = false;
        for (MarieContext ctx : MarieModRegistry.getAll()) {
            if (ctx.respawnValueHandler() == null
                    && ctx.respawnValueBehavior().get() == RespawnValueBehavior.PRESERVE) {
                anyPreserves = true;
                break;
            }
        }
        boolean changed = false;
        boolean memoryHandled = anyPreserves;
        for (MarieContext ctx : MarieModRegistry.getAll()) {
            var custom = ctx.respawnValueHandler();
            if (custom != null) {
                custom.accept(player, tracking);
                changed = true;
                continue;
            }

            RespawnValueBehavior behavior = ctx.respawnValueBehavior().get();
            if (behavior == RespawnValueBehavior.PRESERVE) {
                continue;
            }
            if (!memoryHandled) {
                clearApplicationMemory(tracking);
                memoryHandled = true;
            }
            float fill = behavior == RespawnValueBehavior.VANILLA_HALF ? 0.5f : resolveStartingFill();
            float clamped = Mth.clamp(fill, 0f, 1f);
            String modId = ctx.modId();
            for (String key : MarieContext.get().valueKeys()) {
                if (modId.equals(ValueRegistry.ownerModId(key))
                        && SourceApplicationPipeline.writeDirectValue(player, tracking, key, clamped)) {
                    changed = true;
                }
            }
        }
        if (changed) {
            TrackingAttachment.setData(player, tracking);
        }
    }

    /** Baseline fill for brand-new {@link TrackingData} instances. */
    public static float resolveInitialBarFill() {
        if (MarieContext.isRegistered()) {
            return resolveStartingFill();
        }
        try {
            DiminishingReturnsConfig cfg = IMarieConfig.get().trackingMemoryConfig();
            if (cfg != null) {
                return Mth.clamp((float) cfg.startingValueFill(), 0f, 1f);
            }
        } catch (Exception ignored) {
        }
        return 0.5f;
    }
}
