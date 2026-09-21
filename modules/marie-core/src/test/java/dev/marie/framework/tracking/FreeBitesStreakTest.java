package dev.marie.framework.tracking;

import dev.marie.framework.core.IMarieConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Runs on the library's fallback config: midpoint 3.0 (three free bites), streak weight 1.5. */
class FreeBitesStreakTest {

    private static final String ITEM = "minecraft:cooked_beef";

    private TrackingData data;
    private long now;

    @BeforeEach
    void setUp() {
        data = new TrackingData();
        data.setMemoryConfig(new DiminishingReturnsConfig(60L, 1.25, 5.0, 0.15, 0.5));
        now = 1_000_000L;
    }

    private void eat() {
        data.recordSource(ITEM, "proteins", "meat", now);
        now += 5_000L;
    }

    @Test
    void freeBitesCountAsPlainEatsEvenInAStreak() {
        assertEquals(3.0f, IMarieConfig.get().diminishingMidpoint());

        eat();
        eat();
        eat();

        assertEquals(3.0f, data.getMemoryEntry(ITEM).applicationCount(), 1e-4f);
    }

    @Test
    void streakWeightingStartsAfterTheFreeBites() {
        for (int i = 0; i < 4; i++) {
            eat();
        }

        assertEquals(3.0f + 1.5f, data.getMemoryEntry(ITEM).applicationCount(), 1e-4f);
    }
}
