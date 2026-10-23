package dev.marie.framework.core;

import com.google.gson.JsonObject;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.config.PresetRegistry;
import dev.marie.framework.tracking.DiminishingReturnsConfig;

/**
 * @deprecated Use {@link MarieLibSettings} for scanner/debug settings,
 * or {@link MarieContext#get()} for gameplay methods.
 */
@Deprecated
@ApiStatus.Internal
public interface IMarieConfig extends MarieLibSettings {

    @ApiStatus.Internal
    static IMarieConfig get() {
        if (MarieContext.isRegistered()) {
            return MarieContext.get();
        }
        return FallbackConfig.INSTANCE;
    }

    long memoryWindowMinutes();

    int memoryWindowCount();

    long streakWindowMs();

    float streakWeight();

    float debtThreshold();

    float debtDecayRate();

    float diminishingSteepness();

    float diminishingMidpoint();

    boolean debugMemoryLogging();

    float excessThreshold();

    float lowThreshold();

    float criticalThreshold();

    float criticalThresholdFor(String valueKey);

    int decayIntervalTicks();

    float decayRateFor(String valueKey);

    boolean showJoinMessage();

    DiminishingReturnsConfig trackingMemoryConfig();

    /**
     * Source-aware variant of {@link #trackingMemoryConfig()}, letting a consuming mod return a
     * different curve for a specific source item/trigger (e.g. an item flagged to bypass
     * diminishing returns entirely). {@code sourceKey} is the trigger's {@code sourceId()} and may
     * be null. Default delegates to {@link #trackingMemoryConfig()} for implementers that have no
     * per-source concept.
     */
    @ApiStatus.Internal
    default DiminishingReturnsConfig trackingMemoryConfig(String sourceKey) {
        return trackingMemoryConfig();
    }

    JsonObject configExporter();

    void configImporter(JsonObject json);

    PresetRegistry.PresetValues currentConfigPresetValues();

    void applyPresetValues(PresetRegistry.PresetValues values);

    boolean trackerSystemEnabled();

    int trackerMaxRetention();

    int trackerWeeklyPeriodDays();

    int trackerMonthlyPeriodDays();

    int trackerSyncIntervalTicks();
}
