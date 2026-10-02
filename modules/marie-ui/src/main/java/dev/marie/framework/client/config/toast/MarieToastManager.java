package dev.marie.framework.client.config.toast;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.config.FeatureFlagCache;
import dev.marie.framework.core.MarieContext;
import dev.marie.framework.tracking.TrackingData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks last client value snapshot and queues toasts when a value crosses into the critical band.
 */
@ApiStatus.Internal
public final class MarieToastManager {

    private static final Map<String, Float> lastValues = new HashMap<>();
    private static boolean firstClientSync = true;

    private MarieToastManager() {}

    /**
     * Call when full TrackingData sync applies on the client (login, respawn, dimension change).
     * Seeds state on first sync; then detects {@code before >= critical && after < critical} per value.
     */
    public static void onTrackingUpdated(TrackingData next) {
        processValueUpdate(next.values);
    }

    /**
     * Call when lightweight delta sync applies on the client (every source apply, decay tick).
     */
    public static void onTrackingDelta(Map<String, Float> nextValues) {
        processValueUpdate(nextValues);
    }

    private static void processValueUpdate(Map<String, Float> nextValues) {
        if (!MarieContext.isRegistered()) {
            return;
        }
        List<String> keys = TrackingData.barOrder();
        MarieContext ctx = MarieContext.get();

        if (firstClientSync) {
            firstClientSync = false;
            for (String key : keys) {
                lastValues.put(key, nextValues.getOrDefault(key, 0f));
            }
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (FeatureFlagCache.enableEffects() && FeatureFlagCache.enableToasts() && FeatureFlagCache.enableCriticalToasts() && mc.player != null) {
            float crit = ctx.criticalThreshold();
            float excess = ctx.excessThreshold();
            for (String key : keys) {
                float before = lastValues.getOrDefault(key, 0f);
                float after = nextValues.getOrDefault(key, 0f);
                boolean beneficial = MarieContext.isValueBeneficial(key);

                boolean showToast = false;
                if (beneficial) {
                    if (before >= crit && after < crit) {
                        showToast = true;
                    }
                } else if (before < excess && after >= excess) {
                    showToast = true;
                }

                if (showToast) {
                    ResourceLocation iconId = ResourceLocation.tryParse(ctx.valueIcon(key));
                    if (iconId == null) {
                        continue;
                    }
                    var item = BuiltInRegistries.ITEM.get(iconId);
                    if (item == null) {
                        continue;
                    }
                    ItemStack icon = new ItemStack(item);
                    mc.getToasts().addToast(new CriticalValueToast(key, icon));
                }
            }
        }

        for (String key : keys) {
            lastValues.put(key, nextValues.getOrDefault(key, 0f));
        }
    }
}
