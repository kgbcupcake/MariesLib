package dev.marie.framework.config;

import dev.marie.framework.api.ApiStatus;

import javax.annotation.Nullable;
import java.util.function.LongConsumer;

/**
 * Cached feature flag values for hot-path runtime reads (render/tick/effect loops).
 * Consuming mods call {@link #sync(MarieModFeatureFlags)} after config changes.
 */
@ApiStatus.Internal
public final class FeatureFlagCache {

    private static volatile MarieModFeatureFlags current = MarieModFeatureFlags.disabled();
    private static volatile boolean initialized;
    private static volatile int diminishingReturnsOffCount;
    private static volatile long diminishingReturnsLastOffMillis;
    @Nullable
    private static volatile LongConsumer diminishingReturnsOffListener;

    private FeatureFlagCache() {}

    public static boolean isInitialized() {
        return initialized;
    }

    public static void sync(MarieModFeatureFlags flags) {
        if (current.enableDiminishingReturns() && !flags.enableDiminishingReturns()) {
            diminishingReturnsOffCount++;
            long now = System.currentTimeMillis();
            diminishingReturnsLastOffMillis = now;
            LongConsumer listener = diminishingReturnsOffListener;
            if (listener != null) {
                listener.accept(now);
            }
        }
        current = flags;
        initialized = true;
    }

    /**
     * How many times {@link #sync} has switched diminishing returns off. Tracking data compares it to
     * the value it last saw to notice an off period even when nothing was eaten during it.
     */
    public static int diminishingReturnsOffCount() {
        return diminishingReturnsOffCount;
    }

    /**
     * Wall-clock time ({@link System#currentTimeMillis()}) of the latest {@link #sync} that switched
     * diminishing returns off, or 0 if none has. Saved tracking data compares it to when its memory was
     * written, so a player who was offline through an off period still starts fresh.
     */
    public static long diminishingReturnsLastOffMillis() {
        return diminishingReturnsLastOffMillis;
    }

    /**
     * Replaces the switch-off time with the one saved for the world being played (0 once it stops), so it
     * survives a restart and one world's toggles never reset another world's memory.
     */
    public static void setDiminishingReturnsLastOffMillis(long millis) {
        diminishingReturnsLastOffMillis = millis;
    }

    /** Told the time of every switch-off from {@link #sync}, so the running world can save it; {@code null} for none. */
    public static void setDiminishingReturnsOffListener(@Nullable LongConsumer listener) {
        diminishingReturnsOffListener = listener;
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
