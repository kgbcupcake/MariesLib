package dev.marie.framework.tracking;

import dev.marie.framework.config.FeatureFlagCache;
import dev.marie.framework.config.MarieModFeatureFlags;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Turning diminishing returns off and back on must start fresh, even if nothing was eaten while it was off. */
class DiminishingReturnsOffPeriodTest {

    private static final String ITEM = "minecraft:cooked_beef";

    private TrackingData data;
    private long now;

    private static void syncDiminishingReturns(boolean enabled) {
        FeatureFlagCache.sync(new MarieModFeatureFlags(
                true, true, true, true, true,
                true, true, true, true, true,
                true, true, true, true, true,
                true, true, true, enabled
        ));
    }

    @BeforeEach
    void setUp() {
        syncDiminishingReturns(true);
        data = new TrackingData();
        data.setMemoryConfig(new DiminishingReturnsConfig(60L, 1.25, 5.0, 0.15, 0.5));
        now = 1_000_000L;
    }

    private void eat() {
        data.recordSource(ITEM, "proteins", "meat", now);
        now += 5_000L;
    }

    @Test
    void offPeriodWithNothingEatenStillStartsFresh() {
        eat();
        eat();
        eat();

        syncDiminishingReturns(false);
        syncDiminishingReturns(true);
        eat();

        assertEquals(1.0f, data.getMemoryEntry(ITEM).applicationCount(), 1e-4f);
    }

    @Test
    void stayingOnKeepsMemory() {
        eat();
        eat();

        syncDiminishingReturns(true);
        eat();

        assertEquals(3.0f, data.getMemoryEntry(ITEM).applicationCount(), 1e-4f);
    }
}
