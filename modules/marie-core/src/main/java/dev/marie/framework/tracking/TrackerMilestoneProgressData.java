package dev.marie.framework.tracking;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;

import dev.marie.framework.api.ApiStatus;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Per-player lifetime cumulative totals and one-shot completion bookkeeping for tracker
 * milestones. Structurally parallel to {@link MilestoneProgressData} but keyed by tracker id
 * rather than value key, and stored in a wholly separate attachment namespace — no shared
 * storage with the value-based milestone system. Consumed by {@link TrackerMilestoneTracker}.
 */
@ApiStatus.Internal
public final class TrackerMilestoneProgressData {

    private static Supplier<TrackerMilestoneProgressData> instanceFactory = TrackerMilestoneProgressData::new;

    private static final String LIFETIME_CODEC_KEY = "lifetime";
    private static final String COMPLETED_TRACKER_MILESTONE_IDS_CODEC_KEY = "completed_tracker_milestone_ids";

    private static final Decoder<TrackerMilestoneProgressData> DECODER = new Decoder<>() {
        @Override
        public <T> DataResult<Pair<TrackerMilestoneProgressData, T>> decode(DynamicOps<T> ops, T input) {
            return decodeData(ops, input).map(data -> Pair.of(data, input));
        }
    };

    public static final Codec<TrackerMilestoneProgressData> CODEC = Codec.of(TrackerMilestoneProgressData::encode, DECODER);

    private final Map<String, Float> lifetime = new LinkedHashMap<>();
    private final Set<String> completedTrackerMilestoneIds = new HashSet<>();

    public static TrackerMilestoneProgressData createNew() {
        return instanceFactory.get();
    }

    private TrackerMilestoneProgressData() {}

    public float getLifetime(String trackerId) {
        return lifetime.getOrDefault(trackerId, 0f);
    }

    public void addLifetime(String trackerId, float amount) {
        float current = lifetime.getOrDefault(trackerId, 0f);
        lifetime.put(trackerId, current + amount);
    }

    public boolean isCompleted(String milestoneId) {
        return completedTrackerMilestoneIds.contains(milestoneId);
    }

    public void markCompleted(String milestoneId) {
        completedTrackerMilestoneIds.add(milestoneId);
    }

    private static <T> DataResult<T> encode(TrackerMilestoneProgressData data, DynamicOps<T> ops, T prefix) {
        RecordBuilder<T> map = ops.mapBuilder();
        map.add(LIFETIME_CODEC_KEY,
                Codec.unboundedMap(Codec.STRING, Codec.FLOAT).encodeStart(ops, data.lifetime));
        map.add(COMPLETED_TRACKER_MILESTONE_IDS_CODEC_KEY,
                Codec.STRING.listOf().encodeStart(ops, List.copyOf(data.completedTrackerMilestoneIds)));
        return map.build(prefix);
    }

    private static <T> DataResult<TrackerMilestoneProgressData> decodeData(DynamicOps<T> ops, T input) {
        MapLike<T> map = ops.getMap(input).getOrThrow();

        TrackerMilestoneProgressData data = createNew();

        T lifetimeVal = map.get(LIFETIME_CODEC_KEY);
        if (lifetimeVal != null) {
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT)
                    .parse(ops, lifetimeVal)
                    .result()
                    .ifPresent(data.lifetime::putAll);
        }

        T completedVal = map.get(COMPLETED_TRACKER_MILESTONE_IDS_CODEC_KEY);
        if (completedVal != null) {
            Codec.STRING.listOf()
                    .parse(ops, completedVal)
                    .result()
                    .ifPresent(data.completedTrackerMilestoneIds::addAll);
        }

        return DataResult.success(data);
    }
}
