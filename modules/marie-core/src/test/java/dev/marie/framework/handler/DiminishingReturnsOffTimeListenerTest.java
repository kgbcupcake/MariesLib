package dev.marie.framework.handler;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.marie.framework.config.FeatureFlagCache;
import dev.marie.framework.config.MarieModFeatureFlags;
import dev.marie.framework.tracking.DiminishingReturnsConfig;
import dev.marie.framework.tracking.TrackingData;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The switch-off time is kept with the world, so it still applies to players who load after a restart. */
class DiminishingReturnsOffTimeListenerTest {

    private static final String ITEM = "minecraft:cooked_beef";

    private static void syncDiminishingReturns(boolean enabled) {
        FeatureFlagCache.sync(new MarieModFeatureFlags(
                true, true, true, true, true,
                true, true, true, true, true,
                true, true, true, true, true,
                true, true, true, enabled
        ));
    }

    @AfterEach
    void resetCache() {
        FeatureFlagCache.setDiminishingReturnsOffListener(null);
        FeatureFlagCache.setDiminishingReturnsLastOffMillis(0L);
        syncDiminishingReturns(true);
    }

    @Test
    void offTimeRoundTripsAndOnlyMovesForward() {
        DiminishingReturnsOffTimeListener.OffTime offTime = new DiminishingReturnsOffTimeListener.OffTime();
        offTime.record(500L);
        offTime.record(200L);

        CompoundTag tag = offTime.save(new CompoundTag(), null);
        assertEquals(500L, DiminishingReturnsOffTimeListener.OffTime.load(tag, null).lastOffMillis());
    }

    @Test
    void playerSavedBeforeAnOffPeriodStartsFreshAfterARestart() throws InterruptedException {
        syncDiminishingReturns(true);
        DiminishingReturnsOffTimeListener.OffTime world = new DiminishingReturnsOffTimeListener.OffTime();
        FeatureFlagCache.setDiminishingReturnsOffListener(world::record);

        TrackingData data = new TrackingData();
        data.setMemoryConfig(new DiminishingReturnsConfig(60L, 1.25, 5.0, 0.15, 0.5));
        data.recordSource(ITEM, "proteins", "meat", 1_000_000L);
        JsonElement saved = TrackingData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();

        Thread.sleep(5L);
        syncDiminishingReturns(false);
        syncDiminishingReturns(true);

        // Server stops, then starts again: only what the world saved is left.
        FeatureFlagCache.setDiminishingReturnsLastOffMillis(0L);
        FeatureFlagCache.setDiminishingReturnsLastOffMillis(world.lastOffMillis());

        TrackingData loaded = TrackingData.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        assertNull(loaded.getMemoryEntry(ITEM));
    }

    @Test
    void playerSavedWithNoOffPeriodKeepsMemoryAfterARestart() {
        syncDiminishingReturns(true);
        DiminishingReturnsOffTimeListener.OffTime world = new DiminishingReturnsOffTimeListener.OffTime();

        TrackingData data = new TrackingData();
        data.setMemoryConfig(new DiminishingReturnsConfig(60L, 1.25, 5.0, 0.15, 0.5));
        data.recordSource(ITEM, "proteins", "meat", 1_000_000L);
        JsonElement saved = TrackingData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();

        FeatureFlagCache.setDiminishingReturnsLastOffMillis(world.lastOffMillis());

        TrackingData loaded = TrackingData.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        assertNotNull(loaded.getMemoryEntry(ITEM));
    }
}
