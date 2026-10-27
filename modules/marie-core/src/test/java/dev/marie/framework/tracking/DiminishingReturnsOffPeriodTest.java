package dev.marie.framework.tracking;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.marie.framework.config.FeatureFlagCache;
import dev.marie.framework.config.MarieModFeatureFlags;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    private static JsonElement save(TrackingData d) {
        return TrackingData.CODEC.encodeStart(JsonOps.INSTANCE, d).getOrThrow();
    }

    private static TrackingData load(JsonElement saved) {
        return TrackingData.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
    }

    @Test
    void offlineThroughOffPeriodStartsFresh() throws InterruptedException {
        eat();
        eat();
        JsonElement saved = save(data);
        Thread.sleep(2);

        syncDiminishingReturns(false);
        syncDiminishingReturns(true);

        assertNull(load(saved).getMemoryEntry(ITEM));
    }

    @Test
    void savedWhileOffDropsMemory() {
        eat();
        eat();
        syncDiminishingReturns(false);
        JsonElement saved = save(data);
        syncDiminishingReturns(true);

        assertNull(load(saved).getMemoryEntry(ITEM));
    }

    @Test
    void savedAfterOffPeriodKeepsMemory() throws InterruptedException {
        syncDiminishingReturns(false);
        syncDiminishingReturns(true);
        Thread.sleep(2);
        eat();
        eat();

        TrackingData restored = load(save(data));

        assertEquals(2.0f, restored.getMemoryEntry(ITEM).applicationCount(), 1e-4f);
    }

    @Test
    void autosaveAfterOffPeriodBeforeEatingDropsMemory() {
        eat();
        eat();
        syncDiminishingReturns(false);
        syncDiminishingReturns(true);

        assertNull(load(save(data)).getMemoryEntry(ITEM));
    }
}
