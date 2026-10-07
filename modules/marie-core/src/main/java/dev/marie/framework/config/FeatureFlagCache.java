package dev.marie.framework.config;

import dev.marie.framework.api.ApiStatus;

/**
 * Cached feature flag values for hot-path runtime reads (render/tick/effect loops).
 * Consuming mods call {@link #sync(MarieModFeatureFlags)} after config changes.
 */
@ApiStatus.Internal
public final class FeatureFlagCache {

    private static volatile MarieModFeatureFlags current = MarieModFeatureFlags.disabled();
    private static volatile boolean initialized;

    private FeatureFlagCache() {}

    public static boolean isInitialized() {
        return initialized;
    }

    public static void sync(MarieModFeatureFlags flags) {
        current = flags;
        initialized = true;
    }

    public static MarieModFeatureFlags get() {
        return current;
    }

    public static boolean enableDecay() { return current.enableDecay(); }
    public static boolean enableSourceApplication() { return current.enableSourceApplication(); }
    public static boolean enableBlockHeavySources() { return current.enableBlockHeavySources(); }
    public static boolean enableBlockLightSource() { return current.enableBlockLightSource(); }
    public static boolean enableEffects() { return current.enableEffects(); }
    public static boolean enableHUD() { return current.enableHUD(); }
    public static boolean enableToasts() { return current.enableToasts(); }
    public static boolean enableSourceTooltips() { return current.enableSourceTooltips(); }
    public static boolean enableTotalTracking() { return current.enableTotalTracking(); }
    public static boolean enableTrackingScreen() { return current.enableTrackingScreen(); }
    public static boolean enableCriticalToasts() { return current.enableCriticalToasts(); }
    public static boolean enableSleepBonus() { return current.enableSleepBonus(); }
    public static boolean enableSynergies() { return current.enableSynergies(); }
    public static boolean enableMilestones() { return current.enableMilestones(); }
    public static boolean enableSeasonHooks() { return current.enableSeasonHooks(); }
    public static boolean enableAbsorptionModifiers() { return current.enableAbsorptionModifiers(); }
    public static boolean enableDebugLogging() { return current.enableDebugLogging(); }
    public static boolean enableCalorieHistory() { return current.enableCalorieHistory(); }
    public static boolean enableDiminishingReturns() { return current.enableDiminishingReturns(); }
}
