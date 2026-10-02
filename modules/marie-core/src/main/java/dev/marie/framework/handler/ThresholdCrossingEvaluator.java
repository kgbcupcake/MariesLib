package dev.marie.framework.handler;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.api.marie.MarieEvents;
import dev.marie.framework.api.value.ValueDefinition;
import dev.marie.framework.core.MarieContext;
import dev.marie.framework.tracking.TrackingData;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Fires critical/excess threshold-crossing events for {@link SourceApplicationPipeline}, extracted
 * to keep the pipeline's write paths free of threshold detail. Owned exclusively by
 * {@link SourceApplicationPipeline} via composition — not intended to be shared or held elsewhere.
 */
@ApiStatus.Internal
final class ThresholdCrossingEvaluator {

    private ThresholdCrossingEvaluator() {}

    static void checkThresholdCrossings(ServerPlayer player, TrackingData tracking) {
        for (String key : MarieContext.get().valueKeys()) {
            float current = tracking.values.getOrDefault(key, 0f);
            float previous = tracking.lastValues.getOrDefault(key, 0f);
            boolean beneficial = MarieContext.isValueBeneficial(key);
            MarieContext keyCtx = MarieContext.forValue(key);
            ValueDefinition def = keyCtx.valueDefinitionFor(key);
            float criticalThreshold = keyCtx.criticalThresholdFor(key);
            float excessThreshold = def != null ? def.getExcessThreshold() : keyCtx.excessThreshold();

            if (beneficial) {
                if (current <= criticalThreshold && previous > criticalThreshold) {
                    NeoForge.EVENT_BUS.post(new MarieEvents.ValueCriticalEvent(player, key));
                }
                if (current >= excessThreshold && previous < excessThreshold) {
                    NeoForge.EVENT_BUS.post(new MarieEvents.ValueExcessEvent(player, key));
                }
            } else {
                if (current >= excessThreshold && previous < excessThreshold) {
                    NeoForge.EVENT_BUS.post(new MarieEvents.ValueCriticalEvent(player, key));
                }
            }
        }
    }
}
