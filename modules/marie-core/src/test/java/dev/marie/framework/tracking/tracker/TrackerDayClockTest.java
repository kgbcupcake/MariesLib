package dev.marie.framework.tracking.tracker;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.marie.framework.tracking.tracker.definition.TrackingPeriodState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackerDayClockTest {

    private static final TrackingPeriodState DAY_ONE = new TrackingPeriodState(24000L, 48000L, true);

    @Test
    void dayStartFloorsToTheWorldDay() {
        assertEquals(0L, TrackerManager.dayStart(0L));
        assertEquals(24000L, TrackerManager.dayStart(24000L));
        assertEquals(24000L, TrackerManager.dayStart(47999L));
    }

    @Test
    void sleepingThroughTheNightCrossesTheBoundary() {
        assertFalse(TrackerManager.isDayBoundaryDue(DAY_ONE, 37000L));
        assertTrue(TrackerManager.isDayBoundaryDue(DAY_ONE, 48000L));
    }

    @Test
    void skippingSeveralDaysIsStillOneDueBoundary() {
        assertTrue(TrackerManager.isDayBoundaryDue(DAY_ONE, 24000L * 10));
    }

    @Test
    void timeSetBackBeforeThePeriodStartIsDue() {
        assertTrue(TrackerManager.isDayBoundaryDue(DAY_ONE, 1000L));
    }

    @Test
    void timeSetBackWithinTheSameDayIsNotDue() {
        assertFalse(TrackerManager.isDayBoundaryDue(DAY_ONE, 24000L));
        assertFalse(TrackerManager.isDayBoundaryDue(DAY_ONE, 30000L));
    }

    @Test
    void stateSavedWithoutTheDayClockFlagDecodesAsLegacy() {
        JsonObject legacy = new JsonObject();
        legacy.addProperty("period_start", 100L);
        legacy.addProperty("period_end", 24000L);

        TrackingPeriodState state = TrackingPeriodState.CODEC.parse(JsonOps.INSTANCE, legacy).result().orElseThrow();

        assertFalse(state.dayClock());
        assertEquals(24000L, state.periodEnd());
    }

    @Test
    void dayClockFlagRoundTrips() {
        var json = TrackingPeriodState.CODEC.encodeStart(JsonOps.INSTANCE, DAY_ONE).result().orElseThrow();

        assertEquals(DAY_ONE, TrackingPeriodState.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow());
    }
}
