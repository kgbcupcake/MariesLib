package dev.marie.framework.tracking.tracker;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.core.IMarieConfig;
import dev.marie.framework.core.MarieContext;
import dev.marie.framework.tracking.TrackingAttachment;
import dev.marie.framework.tracking.TrackingData;
import dev.marie.framework.tracking.tracker.definition.TrackerDefinition;
import dev.marie.framework.tracking.tracker.definition.TrackerHistoryEntry;
import dev.marie.framework.tracking.tracker.definition.TrackerPeriod;
import dev.marie.framework.tracking.tracker.definition.TrackingPeriodState;
import dev.marie.framework.tracking.tracker.network.TrackerNetworking;
import dev.marie.framework.tracking.tracker.registry.TrackerRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;

/**
 * Drives tracker period boundaries and history for the generic tracker/period-history framework.
 * MarieLib has no domain knowledge of what a tracker measures — it only manages accumulation,
 * boundaries, and bounded history.
 */
@ApiStatus.Internal
public final class TrackerManager {

    private TrackerManager() {}

    /**
     * Checks all DAILY/WEEKLY/MONTHLY/REAL_TIME tracker boundaries for {@code player}. Called once
     * per tick from the per-player tick loop. SESSION trackers are opened/closed from the player-join
     * listener; CUSTOM trackers are closed via {@link #fireCustomTrackerReset}.
     */
    @ApiStatus.Internal
    public static void checkTrackers(ServerPlayer player, TrackingData tracking) {
        if (!IMarieConfig.get().trackerSystemEnabled()) {
            return;
        }
        for (TrackerDefinition definition : TrackerRegistry.getAll()) {
            TrackerPeriod period = definition.getPeriod();
            if (period == TrackerPeriod.SESSION || period == TrackerPeriod.CUSTOM) {
                continue;
            }
            processDefinition(player, tracking, definition, clockNow(player, definition));
        }
    }

    /**
     * Opens the SESSION-period trackers for a fresh login, first closing any period left open from
     * a previous session (e.g. an unclean disconnect). Called exactly once per login from the
     * player-join listener.
     */
    @ApiStatus.Internal
    public static void openSessionTrackers(ServerPlayer player, TrackingData tracking) {
        if (!IMarieConfig.get().trackerSystemEnabled()) {
            return;
        }
        for (TrackerDefinition definition : TrackerRegistry.getAll()) {
            if (definition.getPeriod() != TrackerPeriod.SESSION) {
                continue;
            }
            ResourceLocation id = definition.getId();
            TrackingPeriodState state = tracking.trackerPeriodStates.get(id);
            long now = clockNow(player, definition);
            if (state == null) {
                tracking.trackerPeriodStates.put(id, openPeriod(definition, now));
            } else {
                closePeriodAndOpenNext(player, tracking, definition, id, state, now);
            }
        }
    }

    /**
     * Closes and reopens a CUSTOM-period tracker on demand. The consuming mod decides when a
     * CUSTOM period ends and calls this directly.
     */
    @ApiStatus.Internal
    public static void fireCustomTrackerReset(ServerPlayer player, TrackingData tracking, ResourceLocation trackerId) {
        if (!IMarieConfig.get().trackerSystemEnabled()) {
            return;
        }
        TrackerDefinition definition = TrackerRegistry.get(trackerId);
        if (definition == null || definition.getPeriod() != TrackerPeriod.CUSTOM) {
            return;
        }
        long now = clockNow(player, definition);
        TrackingPeriodState state = tracking.trackerPeriodStates.get(trackerId);
        if (state == null) {
            tracking.trackerPeriodStates.put(trackerId, openPeriod(definition, now));
            return;
        }
        closePeriodAndOpenNext(player, tracking, definition, trackerId, state, now);
    }

    /** Clears dirty-sync bookkeeping for a player, e.g. on logout. */
    @ApiStatus.Internal
    public static void clearDirtySyncState(UUID playerId) {
        TrackerDirtyState.clearPlayer(playerId);
    }

    /**
     * Throttled dirty-value push: if {@code player} has trackers marked dirty by
     * {@link MarieTracking#incrementTracker} and at least {@code IMarieConfig#trackerSyncIntervalTicks()}
     * have passed since their last sync, sends just those trackers' current values and clears
     * the dirty set. Called once per player tick, piggybacked on the same loop as
     * {@link #checkTrackers}.
     */
    @ApiStatus.Internal
    public static void sweepDirtySync(ServerPlayer player) {
        if (!IMarieConfig.get().trackerSystemEnabled()) {
            return;
        }
        UUID playerId = player.getUUID();
        if (!TrackerDirtyState.hasDirty(playerId)) {
            return;
        }
        long nowGameTime = player.level().getGameTime();
        int interval = Math.max(1, IMarieConfig.get().trackerSyncIntervalTicks());
        if (nowGameTime - TrackerDirtyState.lastSyncTick(playerId) < interval) {
            return;
        }
        Set<ResourceLocation> dirty = TrackerDirtyState.drainDirty(playerId);
        if (dirty.isEmpty()) {
            return;
        }
        TrackingData tracking = TrackingAttachment.getData(player);
        TrackerNetworking.sendLiveValues(player, dirty, tracking);
        TrackerDirtyState.setLastSyncTick(playerId, nowGameTime);
    }

    /**
     * The clock a definition's boundaries are measured on. DAILY/WEEKLY/MONTHLY use the world's day
     * time so sleeping through the night and {@code /time} move the day; everything else keeps the
     * monotonic game time (REAL_TIME ignores it and reads the wall clock).
     */
    private static long clockNow(ServerPlayer player, TrackerDefinition definition) {
        return isDayPeriod(definition.getPeriod()) ? player.level().getDayTime() : player.level().getGameTime();
    }

    private static boolean isDayPeriod(TrackerPeriod period) {
        return period == TrackerPeriod.DAILY || period == TrackerPeriod.WEEKLY || period == TrackerPeriod.MONTHLY;
    }

    private static void processDefinition(ServerPlayer player, TrackingData tracking,
            TrackerDefinition definition, long now) {
        ResourceLocation id = definition.getId();
        TrackingPeriodState state = tracking.trackerPeriodStates.get(id);
        if (state == null) {
            // First-period initialization: open only, never backfill/synthesize a history entry.
            tracking.trackerPeriodStates.put(id, openPeriod(definition, now));
            return;
        }
        if (isDayPeriod(definition.getPeriod()) && !state.dayClock()) {
            // Saved before day-time periods: its boundaries are game ticks. Re-base onto the day
            // clock once, keeping the accumulator, so the value in progress isn't lost or split.
            tracking.trackerPeriodStates.put(id, openPeriod(definition, now));
            return;
        }
        if (isBoundaryDue(definition, state, now)) {
            closePeriodAndOpenNext(player, tracking, definition, id, state, now);
        }
    }

    /** The seam for later optimization (e.g. batching boundary checks instead of per-tracker per-tick). */
    private static boolean isBoundaryDue(TrackerDefinition definition, TrackingPeriodState state, long now) {
        return switch (definition.getPeriod()) {
            case DAILY, WEEKLY, MONTHLY -> isDayBoundaryDue(state, now);
            case REAL_TIME -> System.currentTimeMillis() >= state.periodEnd();
            case SESSION, CUSTOM -> false;
        };
    }

    /**
     * Due once the day clock reaches the period's end (also across a multi-day skip, which closes
     * a single period), or when {@code /time set} has moved it back before the period's start — a
     * backwards jump must not leave the period stuck until the clock catches up.
     */
    static boolean isDayBoundaryDue(TrackingPeriodState state, long dayTime) {
        return dayTime >= state.periodEnd() || dayTime < state.periodStart();
    }

    /** First tick of the world day {@code dayTime} falls in. */
    static long dayStart(long dayTime) {
        return Math.floorDiv(dayTime, 24000L) * 24000L;
    }

    private static void closePeriodAndOpenNext(ServerPlayer player, TrackingData tracking,
            TrackerDefinition definition, ResourceLocation id, TrackingPeriodState state, long now) {
        float value = tracking.trackingAccumulators.getOrDefault(id, 0f);
        long periodEnd = switch (definition.getPeriod()) {
            case SESSION, CUSTOM -> now;
            case DAILY, WEEKLY, MONTHLY, REAL_TIME -> state.periodEnd();
        };
        TrackerHistoryEntry entry = new TrackerHistoryEntry(
                id, definition.getPeriod().configId(), state.periodStart(), periodEnd, value);
        tracking.appendTrackerHistory(id, entry, definition.getRetention());
        tracking.trackingAccumulators.put(id, 0f);
        tracking.trackerPeriodStates.put(id, openPeriod(definition, now));
        TrackerNetworking.sendPeriodResync(player, id, tracking);
        if (MarieContext.isRegistered()) {
            MarieContext.get().onTrackerPeriodCompletedHook().accept(player, entry);
        }
    }

    /** {@code now} is on the definition's own clock (see {@link #clockNow}). */
    private static TrackingPeriodState openPeriod(TrackerDefinition definition, long now) {
        return switch (definition.getPeriod()) {
            case DAILY -> {
                long start = dayStart(now);
                yield new TrackingPeriodState(start, start + 24000L, true);
            }
            case WEEKLY -> {
                long start = dayStart(now);
                yield new TrackingPeriodState(start, start + IMarieConfig.get().trackerWeeklyPeriodDays() * 24000L, true);
            }
            case MONTHLY -> {
                long start = dayStart(now);
                yield new TrackingPeriodState(start, start + (long) IMarieConfig.get().trackerMonthlyPeriodDays() * 24000L, true);
            }
            case REAL_TIME -> {
                long nowMs = System.currentTimeMillis();
                yield new TrackingPeriodState(nowMs, nowMs + definition.getRealTimeDurationMs());
            }
            case SESSION, CUSTOM -> new TrackingPeriodState(now, now);
        };
    }
}
