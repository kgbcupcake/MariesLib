package dev.marie.framework.tracking.tracker.definition;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;

import dev.marie.framework.api.ApiStatus;

/**
 * Open-period bookkeeping for a single tracker on a single player. Boundaries are in the time
 * domain of the owning {@link TrackerDefinition}'s {@link TrackerPeriod}: world day time ticks for
 * DAILY/WEEKLY/MONTHLY (so sleeping and {@code /time} move them), game time ticks for
 * SESSION/CUSTOM, epoch ms for REAL_TIME.
 *
 * @param periodStart when the currently open period began
 * @param periodEnd   when the currently open period is due to close
 * @param dayClock    true when the boundaries are day time ticks; false for states saved before
 *                    day-time periods existed (their DAILY/WEEKLY/MONTHLY boundaries are game
 *                    time ticks and get re-based once on load)
 */
@ApiStatus.Internal
public record TrackingPeriodState(long periodStart, long periodEnd, boolean dayClock) {

    public TrackingPeriodState(long periodStart, long periodEnd) {
        this(periodStart, periodEnd, false);
    }

    public static final Codec<TrackingPeriodState> CODEC = Codec.of(
            TrackingPeriodState::encode,
            TrackingPeriodState::decode
    );

    private static <T> DataResult<T> encode(TrackingPeriodState state, DynamicOps<T> ops, T prefix) {
        RecordBuilder<T> builder = ops.mapBuilder();
        builder.add("period_start", Codec.LONG.encodeStart(ops, state.periodStart));
        builder.add("period_end", Codec.LONG.encodeStart(ops, state.periodEnd));
        builder.add("day_clock", Codec.BOOL.encodeStart(ops, state.dayClock));
        return builder.build(prefix);
    }

    private static <T> DataResult<Pair<TrackingPeriodState, T>> decode(DynamicOps<T> ops, T input) {
        return ops.getMap(input).flatMap(map -> {
            long periodStart = decode(ops, map, "period_start", 0L);
            long periodEnd = decode(ops, map, "period_end", 0L);
            T dayClockVal = map.get("day_clock");
            boolean dayClock = dayClockVal != null && Codec.BOOL.parse(ops, dayClockVal).result().orElse(false);
            return DataResult.success(Pair.of(new TrackingPeriodState(periodStart, periodEnd, dayClock), input));
        });
    }

    private static <T> long decode(DynamicOps<T> ops, MapLike<T> map, String field, long fallback) {
        T val = map.get(field);
        if (val == null) return fallback;
        return Codec.LONG.parse(ops, val).result().orElse(fallback);
    }
}
