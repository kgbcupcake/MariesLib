package dev.marie.framework.config;

import dev.marie.framework.api.ApiStatus;

/**
 * Immutable snapshot of feature flags supplied by the consuming Marie mod.
 * MarieCore does not define defaults for these — consuming mods must provide values.
 */
@ApiStatus.Stable
public record MarieModFeatureFlags(
        boolean enableDecay,
        boolean enableSourceApplication,
        boolean enableBlockHeavySources,
        boolean enableBlockLightSource,
        boolean enableEffects,
        boolean enableHUD,
        boolean enableToasts,
        boolean enableSourceTooltips,
        boolean enableTotalTracking,
        boolean enableTrackingScreen,
        boolean enableCriticalToasts,
        boolean enableSleepBonus,
        boolean enableSynergies,
        boolean enableMilestones,
        boolean enableSeasonHooks,
        boolean enableAbsorptionModifiers,
        boolean enableDebugLogging,
        boolean enableCalorieHistory,
        boolean enableDiminishingReturns
) {
    /**
     * Conservative defaults: all pipelines disabled.
     * Used when no consuming mod has registered.
     */
    public static MarieModFeatureFlags disabled() {
        return new MarieModFeatureFlags(
                false, false, false, false, false,
                false, false, false, false, false,
                false, false, false, false, false,
                false, false, false, false
        );
    }
}
