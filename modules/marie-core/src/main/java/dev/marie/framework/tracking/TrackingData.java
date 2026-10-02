package dev.marie.framework.tracking;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;

import dev.marie.framework.api.ApiStatus;


import dev.marie.framework.core.IMarieConfig;
import dev.marie.framework.core.MarieCore;
import dev.marie.framework.core.MarieContext;
import dev.marie.framework.tracking.tracker.definition.TrackerHistoryEntry;
import dev.marie.framework.tracking.tracker.definition.TrackingPeriodState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tracks daily-style value totals. Value keys are driven by {@link MarieContext#valueKeys()}.
 * <p>
 * SCHEMA CHANGE: legacy attachment removed in this version. Existing player saves will reset tracking data.
 * Acceptable for alpha.
 */
@ApiStatus.Experimental
public class TrackingData {

    private static java.util.function.Supplier<? extends TrackingData> instanceFactory = TrackingData::new;

    /** Configure instance factory before attachment registration (consuming mod subclass support). */
    @ApiStatus.Experimental
    public static void setInstanceFactory(java.util.function.Supplier<? extends TrackingData> factory) {
        instanceFactory = factory;
    }

    /** Creates a new instance via the configured factory. */
    @ApiStatus.Experimental
    public static TrackingData createNew() {
        return instanceFactory.get();
    }

    private static final String VALUES_CODEC_PREFIX = "values.";
    private static final String LAST_VALUE_CODEC_PREFIX = "last_";
    private static final Set<String> NON_VALUE_CODEC_FIELDS = Set.of(
            "total",
            "max_total",
            "source_memory",
            "category_memory",
            "family_memory",
            "recent_ids",
            "last_tick_time",
            "tracker_accumulators",
            "tracker_history",
            "tracker_period_states"
    );

    /** Display / bar order — delegates to the registry so it stays in sync. */
    @ApiStatus.Experimental
    public static List<String> barOrder() {
        return MarieContext.isRegistered() ? MarieContext.get().valueKeys() : List.of();
    }

    private static boolean isValueBeneficial(String key) {
        return MarieContext.isRegistered() && MarieContext.isValueBeneficial(key);
    }

    private static int configuredMemoryWindowCount() {
        return IMarieConfig.get().memoryWindowCount();
    }

    private static long configuredStreakWindowMs() {
        return IMarieConfig.get().streakWindowMs();
    }

    private static float configuredStreakWeight() {
        return IMarieConfig.get().streakWeight();
    }

    private static boolean configuredDebugMemoryLogging() {
        return IMarieConfig.get().debugMemoryLogging();
    }

    private static float configuredDebtThreshold() {
        return IMarieConfig.get().debtThreshold();
    }

    private static float configuredDebtDecayRate() {
        return IMarieConfig.get().debtDecayRate();
    }

    private static double configuredDiminishingSteepness() {
        return IMarieConfig.get().diminishingSteepness();
    }

    private static double configuredDiminishingMidpoint() {
        return IMarieConfig.get().diminishingMidpoint();
    }

    // ── Codec ─────────────────────────────────────────────────────────────────

    private static final Decoder<TrackingData> DECODER = new Decoder<>() {
        @Override
        @ApiStatus.Experimental
        public <T> DataResult<Pair<TrackingData, T>> decode(DynamicOps<T> ops, T input) {
            return decodeData(ops, input).map(d -> Pair.of(d, input));
        }
    };

    @ApiStatus.Experimental
    public static final Codec<TrackingData> CODEC = Codec.of(TrackingData::encode, DECODER);

    private static <T> DataResult<T> encode(TrackingData data, DynamicOps<T> ops, T prefix) {
        RecordBuilder<T> map = ops.mapBuilder();
        map.add("total",     Codec.FLOAT.encodeStart(ops, data.total));
        map.add("max_total", Codec.FLOAT.encodeStart(ops, data.maxTotal));
        for (String key : barOrder()) {
            map.add(getValueCodecKey(key), Codec.FLOAT.encodeStart(ops, data.values.getOrDefault(key, 0f)));
            map.add("last_" + key, Codec.FLOAT.encodeStart(ops, data.lastValues.getOrDefault(key, 0f)));
        }
        map.add("source_memory", Codec.unboundedMap(Codec.STRING, SourceMemoryEntry.CODEC)
                .encodeStart(ops, data.sourceMemory));
        map.add("category_memory", Codec.unboundedMap(Codec.STRING, SourceMemoryEntry.CODEC)
                .encodeStart(ops, data.categoryMemory));
        map.add("family_memory", Codec.unboundedMap(Codec.STRING, SourceMemoryEntry.CODEC)
                .encodeStart(ops, data.familyMemory));
        map.add("recent_ids", Codec.list(Codec.STRING).encodeStart(ops, data.recentIds));
        map.add("last_tick_time", Codec.LONG.encodeStart(ops, data.lastTickTime));
        map.add("tracker_accumulators", Codec.unboundedMap(ResourceLocation.CODEC, Codec.FLOAT)
                .encodeStart(ops, data.trackingAccumulators));
        map.add("tracker_history", Codec.unboundedMap(ResourceLocation.CODEC, Codec.list(TrackerHistoryEntry.CODEC))
                .encodeStart(ops, data.trackingHistory));
        map.add("tracker_period_states", Codec.unboundedMap(ResourceLocation.CODEC, TrackingPeriodState.CODEC)
                .encodeStart(ops, data.trackerPeriodStates));
        return map.build(prefix);
    }

    private static <T> DataResult<TrackingData> decodeData(DynamicOps<T> ops, T input) {
        MapLike<T> map = ops.getMap(input).getOrThrow();

        TrackingData data = instanceFactory.get();
        data.total    = decodeFloat(ops, map, "total",     0f);
        data.maxTotal = decodeFloat(ops, map, "max_total", 2000f);

        List<String> valueKeys = barOrder();
        Set<String> registeredValueKeys = Set.copyOf(valueKeys);
        logDroppedUnknownValues(ops, map, registeredValueKeys);

        for (String key : valueKeys) {
            float valueAmount = decodeFloatWithFallback(ops, map, getValueCodecKey(key), key, 0f);
            data.values.put(key, valueAmount);
            data.lastValues.put(key, decodeFloat(ops, map, "last_" + key, 0f));
        }

        T sourceMemoryVal = map.get("source_memory");
        if (sourceMemoryVal != null) {
            Codec.unboundedMap(Codec.STRING, SourceMemoryEntry.CODEC)
                    .parse(ops, sourceMemoryVal)
                    .result()
                    .ifPresent(m -> {
                        if (m.size() > 512) {
                            MarieCore.LOGGER.warn("[TrackingData] source_memory exceeded cap ({} entries) — truncating to 512", m.size());
                        }
                        m.entrySet().stream().limit(512).forEach(e -> data.sourceMemory.put(e.getKey(), e.getValue()));
                    });
        }

        // New maps with empty fallback for old saves
        T categoryMemoryVal = map.get("category_memory");
        if (categoryMemoryVal != null) {
            Codec.unboundedMap(Codec.STRING, SourceMemoryEntry.CODEC)
                    .parse(ops, categoryMemoryVal)
                    .result()
                    .ifPresent(m -> {
                        if (m.size() > 256) {
                            MarieCore.LOGGER.warn("[TrackingData] category_memory exceeded cap ({} entries) — truncating to 256", m.size());
                        }
                        m.entrySet().stream().limit(256).forEach(e -> data.categoryMemory.put(e.getKey(), e.getValue()));
                    });
        }

        T familyMemoryVal = map.get("family_memory");
        if (familyMemoryVal != null) {
            Codec.unboundedMap(Codec.STRING, SourceMemoryEntry.CODEC)
                    .parse(ops, familyMemoryVal)
                    .result()
                    .ifPresent(m -> {
                        if (m.size() > 256) {
                            MarieCore.LOGGER.warn("[TrackingData] family_memory exceeded cap ({} entries) — truncating to 256", m.size());
                        }
                        m.entrySet().stream().limit(256).forEach(e -> data.familyMemory.put(e.getKey(), e.getValue()));
                    });
        }

        T recentIdsVal = map.get("recent_ids");
        if (recentIdsVal != null) {
            Codec.list(Codec.STRING).parse(ops, recentIdsVal).result()
                    .ifPresent(data.recentIds::addAll);
        }

        data.lastTickTime = decodeLong(ops, map, "last_tick_time", 0L);

        T trackerAccumulatorsVal = map.get("tracker_accumulators");
        if (trackerAccumulatorsVal != null) {
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.FLOAT)
                    .parse(ops, trackerAccumulatorsVal)
                    .result()
                    .ifPresent(m -> data.trackingAccumulators.putAll(m));
        }

        T trackerHistoryVal = map.get("tracker_history");
        if (trackerHistoryVal != null) {
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.list(TrackerHistoryEntry.CODEC))
                    .parse(ops, trackerHistoryVal)
                    .result()
                    .ifPresent(m -> m.forEach((id, list) -> data.trackingHistory.put(id, new ArrayList<>(list))));
        }

        T trackerPeriodStatesVal = map.get("tracker_period_states");
        if (trackerPeriodStatesVal != null) {
            Codec.unboundedMap(ResourceLocation.CODEC, TrackingPeriodState.CODEC)
                    .parse(ops, trackerPeriodStatesVal)
                    .result()
                    .ifPresent(m -> data.trackerPeriodStates.putAll(m));
        }

        return DataResult.success(data);
    }

    private static <T> long decodeLong(DynamicOps<T> ops, MapLike<T> map, String field, long fallback) {
        T val = map.get(field);
        if (val == null) return fallback;
        return Codec.LONG.parse(ops, val).result().orElse(fallback);
    }

    private static <T> float decodeFloat(DynamicOps<T> ops, MapLike<T> map, String field, float fallback) {
        T val = map.get(field);
        if (val == null) return fallback;
        return Codec.FLOAT.parse(ops, val).result().orElse(fallback);
    }

    private static <T> float decodeFloatWithFallback(DynamicOps<T> ops, MapLike<T> map, String primaryField, String fallbackField, float fallback) {
        T primary = map.get(primaryField);
        if (primary != null) {
            return Codec.FLOAT.parse(ops, primary).result().orElse(fallback);
        }
        return decodeFloat(ops, map, fallbackField, fallback);
    }

    private static <T> void logDroppedUnknownValues(DynamicOps<T> ops, MapLike<T> map, Set<String> registeredValueKeys) {
        Set<String> loggedUnknownKeys = new HashSet<>();
        map.entries().forEach(entry ->
                ops.getStringValue(entry.getFirst()).result()
                        .map(TrackingData::extractSavedValueKey)
                        .filter(key -> key != null && !registeredValueKeys.contains(key))
                        .filter(loggedUnknownKeys::add)
                        .ifPresent(key -> MarieCore.LOGGER.debug(
                                "[TrackingData] Dropping unknown value key from saved data: {}",
                                key
                        ))
        );
    }

    private static String extractSavedValueKey(String field) {
        if (field.startsWith(VALUES_CODEC_PREFIX)) {
            String key = field.substring(VALUES_CODEC_PREFIX.length());
            return key.isBlank() ? null : key;
        }
        if (field.startsWith(LAST_VALUE_CODEC_PREFIX)) {
            String key = field.substring(LAST_VALUE_CODEC_PREFIX.length());
            return key.isBlank() ? null : key;
        }
        if (NON_VALUE_CODEC_FIELDS.contains(field)) {
            return null;
        }
        return field.isBlank() ? null : field;
    }

    private static String getValueCodecKey(String valueKey) {
        return VALUES_CODEC_PREFIX + valueKey;
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    @ApiStatus.Experimental
    public float total;
    @ApiStatus.Experimental
    public float maxTotal = 2000f;
    @ApiStatus.Experimental
    public final LinkedHashMap<String, Float> values     = new LinkedHashMap<>();
    @ApiStatus.Experimental
    public final LinkedHashMap<String, Float> lastValues = new LinkedHashMap<>();

    // Source memory maps
    @ApiStatus.Experimental
    public final LinkedHashMap<String, SourceMemoryEntry> sourceMemory = new LinkedHashMap<>();

    /** Recent source ids as supplied by the consuming mod; relayed verbatim in full sync. */
    @ApiStatus.Experimental
    public final List<String> recentIds = new ArrayList<>();

    /** Server/client-injected config at runtime. Never serialized. */
    private DiminishingReturnsConfig memoryConfig = null;

    @ApiStatus.Experimental
    public final HashMap<String, SourceMemoryEntry> categoryMemory = new HashMap<>();
    @ApiStatus.Experimental
    public final HashMap<String, SourceMemoryEntry> familyMemory = new HashMap<>();

    /** Last known game time in ms, used for decay calculations */
    @ApiStatus.Experimental
    public long lastTickTime = 0L;

    // Tracker system state — separate from values/total (value bars), which stay untouched.
    // MarieLib has no domain knowledge of what a tracker measures.
    @ApiStatus.Experimental
    public final Map<ResourceLocation, Float> trackingAccumulators = new LinkedHashMap<>();
    @ApiStatus.Experimental
    public final Map<ResourceLocation, List<TrackerHistoryEntry>> trackingHistory = new LinkedHashMap<>();
    @ApiStatus.Experimental
    public final Map<ResourceLocation, TrackingPeriodState> trackerPeriodStates = new LinkedHashMap<>();

    @ApiStatus.Experimental
    public TrackingData() {
        float start = startValueFill();
        for (String key : barOrder()) {
            values.put(key, start);
            lastValues.put(key, start);
        }
    }

    /**
     * Deep-copies mutable maps and scalars for thread-safe handoff (e.g. client cache publish).
     */
    @ApiStatus.Experimental
    public static TrackingData copySnapshot(TrackingData src) {
        TrackingData d = instanceFactory.get();
        d.total = src.total;
        d.maxTotal = src.maxTotal;
        d.values.clear();
        d.values.putAll(src.values);
        d.lastValues.clear();
        d.lastValues.putAll(src.lastValues);
        d.sourceMemory.clear();
        d.sourceMemory.putAll(src.sourceMemory);
        d.recentIds.clear();
        d.recentIds.addAll(src.recentIds);
        d.categoryMemory.clear();
        d.categoryMemory.putAll(src.categoryMemory);
        d.familyMemory.clear();
        d.familyMemory.putAll(src.familyMemory);
        d.lastTickTime = src.lastTickTime;
        d.trackingAccumulators.clear();
        d.trackingAccumulators.putAll(src.trackingAccumulators);
        d.trackingHistory.clear();
        for (Map.Entry<ResourceLocation, List<TrackerHistoryEntry>> entry : src.trackingHistory.entrySet()) {
            d.trackingHistory.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        d.trackerPeriodStates.clear();
        d.trackerPeriodStates.putAll(src.trackerPeriodStates);
        if (src.memoryConfig != null) {
            d.memoryConfig = src.memoryConfig;
        }
        return d;
    }

    /**
     * Baseline for every value bar on new {@link TrackingData} (new players / fresh attachment).
     * Uses {@link DiminishingReturnsConfig#startingValueFill()} (default {@code 0.5} = 50%) so typical
     * {@code below}-threshold debuffs do not apply until decay or poor tracking pulls bars down.
     */
    private float startValueFill() {
        return TrackingResetSupport.resolveInitialBarFill();
    }

    // ── Mutation ──────────────────────────────────────────────────────────────

    /**
     * Snapshot current values into {@link #lastValues}. Call on the server before applying
     * source deltas so trends compare the previous application state to the new state.
     */
    @ApiStatus.Experimental
    public void tick() {
        lastValues.clear();
        lastValues.putAll(values);
    }

    /**
     * Updates the last known game time anchor.
     * Call on every source application with
     * {@code player.level().getGameTime() * 50L} to keep time
     * anchored to game ticks rather than wall-clock time.
     */
    @ApiStatus.Experimental
    public void tickTime(long gameTimeMs) {
        this.lastTickTime = gameTimeMs;
    }

    /**
     * Prepends {@code entry} to the tracker's history (most recent first) and trims the list to
     * {@code retention} entries. Capped on append so the history map never grows unbounded.
     */
    @ApiStatus.Experimental
    public void appendTrackerHistory(ResourceLocation trackerId, TrackerHistoryEntry entry, int retention) {
        List<TrackerHistoryEntry> list = trackingHistory.computeIfAbsent(trackerId, k -> new ArrayList<>());
        list.add(0, entry);
        while (list.size() > retention) {
            list.remove(list.size() - 1);
        }
    }

    @ApiStatus.Experimental
    public void addTotal(float amount) {
        total = Math.max(0f, total + amount);
    }

    @ApiStatus.Experimental
    public void addValue(String key, float amount) {
        if (!values.containsKey(key)) return;
        float newValue = Mth.clamp(values.get(key) + amount, 0f, 1f);
        if (Float.isNaN(newValue)) {
            MarieCore.LOGGER.error("[MarieLib] NaN value detected for key={} — resetting to 0", key);
            newValue = 0f;
        }
        values.put(key, newValue);
    }

    /** Balance score in [0, 1]: higher when all bars are closer to each other. */
    @ApiStatus.Experimental
    public float getBalanceScore() {
        List<String> keys = barOrder();
        float sum = 0f;
        for (String k : keys) sum += effectiveWellnessValue(k, values.getOrDefault(k, 0f));
        float avg = sum / keys.size();
        if (avg <= 0.0001f) return 1f;
        float dev = 0f;
        for (String k : keys) dev += Math.abs(effectiveWellnessValue(k, values.getOrDefault(k, 0f)) - avg);
        return 1f - Math.min(1f, dev / (avg * keys.size() * 2f));
    }

    private static float effectiveWellnessValue(String key, float value) {
        return isValueBeneficial(key) ? value : 1f - value;
    }

    /** Override in consuming mod to produce network sync payload. */
    @ApiStatus.Experimental
    public Object toDeltaPayload() {
        throw new UnsupportedOperationException("Override in consuming mod");
    }

    // ── Source Memory ────────────────────────────────────────────────────────────

    /**
     * Records a source apply with full blended multiplier system.
     * <p>
     * This method:
     * <ol>
     *   <li>Performs single cleanup pass on all three memory maps</li>
     *   <li>Updates source, category, and family memories with appropriate weights</li>
     *   <li>Applies value debt if threshold exceeded</li>
     *   <li>Returns blended multiplier for the source</li>
     * </ol>
     *
     * @param sourceKey        the specific source key
     * @param dominantCategory the primary value category
     * @param familyKey        the source family, nullable
     * @param gameTimeMs       current game time in milliseconds
     * @return blended multiplier to apply to tracking deltas
     */
    @ApiStatus.Experimental
    public float recordSource(String sourceKey, String dominantCategory, String familyKey, long gameTimeMs) {
        
        int maxCount = configuredMemoryWindowCount();
        long streakWindowMs = configuredStreakWindowMs();
        float streakWeight = configuredStreakWeight();
        long currentTime = resolveCurrentTime(gameTimeMs);

        // Single cleanup pass for all three maps (remove entries decayed below threshold)
        cleanupAllMemories(currentTime);

        // Enforce count cap on source memory (evict oldest)
        if (!sourceMemory.containsKey(sourceKey) && sourceMemory.size() >= maxCount) {
            String oldest = sourceMemory.entrySet().stream()
                    .min(Comparator.comparingLong(e -> e.getValue().lastAppliedTick()))
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (oldest != null) sourceMemory.remove(oldest);
        }

        // Update all three memories with appropriate weights
        updateMemory(sourceMemory, sourceKey, currentTime, 1.0f, streakWindowMs, streakWeight);
        if (dominantCategory != null) {
            updateMemory(categoryMemory, dominantCategory, currentTime, 0.3f, streakWindowMs, streakWeight);
        }
        if (familyKey != null) {
            updateMemory(familyMemory, familyKey, currentTime, 0.2f, streakWindowMs, streakWeight);
        }

        // Update tick time
        this.lastTickTime = currentTime;

        // Apply value debt (implemented in Phase 4)
        if (dominantCategory != null) {
            applyValueDebt(dominantCategory, currentTime);
        }

        float result = computeBlendedMultiplier(sourceKey, dominantCategory, familyKey, currentTime);

        // Debug logging when enabled
        if (configuredDebugMemoryLogging()) {
            MultiplierBreakdown breakdown = getMultiplierBreakdown(sourceKey, dominantCategory, familyKey, currentTime);
            MarieCore.LOGGER.debug(
                    "Memory breakdown for {}: item={} (w={}) cat={} (w={}) family={} (w={}) novelty={} => final={}",
                    sourceKey,
                    breakdown.itemContribution(), breakdown.itemWeight(),
                    breakdown.categoryContribution(), breakdown.categoryWeight(),
                    breakdown.familyContribution(), breakdown.familyWeight(),
                    breakdown.noveltyContribution(),
                    breakdown.finalMultiplier()
            );
        }

        return result;
    }

    /**
     * Helper to update a memory map with streak-aware increment.
     */
    private void updateMemory(Map<String, SourceMemoryEntry> memory, String key, long gameTimeMs,
                              float baseIncrement, long streakWindowMs, float streakWeight) {
        SourceMemoryEntry entry = memory.getOrDefault(key, new SourceMemoryEntry(0f, gameTimeMs));
        long elapsed = gameTimeMs - entry.lastAppliedTick();
        boolean inStreak = elapsed <= streakWindowMs && elapsed >= 0;
        // The free bites (the curve's midpoint) count as plain eats; streak weighting starts after them.
        boolean pastFreeBites = entry.applicationCount() >= configuredDiminishingMidpoint() * baseIncrement;
        float increment = baseIncrement * (inStreak && pastFreeBites ? streakWeight : 1.0f);
        entry = new SourceMemoryEntry(entry.applicationCount() + increment, gameTimeMs);
        memory.put(key, entry);
    }

    /**
     * Backward-compatible recordSource that guesses category and uses system time.
     * <p>
     * Preserved for existing callers. For new code, prefer
     * {@link #recordSource(String, String, String, long)}.
     *
     * @param sourceKey the source key
     * @return multiplier to apply
     */
    @ApiStatus.Experimental
    public float recordSource(String sourceKey) {
        return recordSource(sourceKey, null, null, resolveCurrentTime(0L));
    }

    /**
     * Peek the multiplier that would apply if this source were applied next next.
     * Used for tooltip display.
     */
    @ApiStatus.Experimental
    public float peekMultiplier(String sourceKey) {
        long halfLifeMs = config().memoryWindowMinutes() * 60_000L;
        long gameTimeMs = resolveCurrentTime(0L);
        SourceMemoryEntry entry = sourceMemory.get(sourceKey);
        if (entry == null || entry.isEffectivelyExpired(halfLifeMs, gameTimeMs, 0.1f)) {
            return (float) config().noveltyBonus();
        }
        // Simulate one more application for preview
        float nextDecayed = entry.decayedApplicationCount(halfLifeMs, gameTimeMs) + 1f;
        return resolveMultiplier(nextDecayed);
    }

    /**
     * Applies soft value debt when a category is overconsumed.
     * <p>
     * When a category's decayed application count exceeds the debt threshold, this method
     * finds the most neglected value category (lowest bar value, excluding the current)
     * and applies a small decay to it. This encourages source variety.
     *
     * @param dominantCategory the category being applied
     * @param gameTimeMs       current game time in milliseconds
     */
    private void applyValueDebt(String dominantCategory, long gameTimeMs) {
        DiminishingReturnsConfig memCfg = config();
        long halfLifeMs = memCfg.memoryWindowMinutes() * 60_000L;
        
        float debtThreshold = configuredDebtThreshold();
        float debtRate = configuredDebtDecayRate();

        // Check if category memory exceeds debt threshold
        SourceMemoryEntry catEntry = categoryMemory.get(dominantCategory);
        if (catEntry == null) return;

        float decayedCatCount = catEntry.decayedApplicationCount(halfLifeMs, gameTimeMs);
        if (decayedCatCount < debtThreshold) return;

        // Find the most neglected category (lowest bar value, excluding current)
        String mostNeglected = null;
        float lowestValue = Float.MAX_VALUE;

        for (Map.Entry<String, Float> entry : values.entrySet()) {
            String key = entry.getKey();
            if (key.equals(dominantCategory)) continue;
            float value = entry.getValue();
            if (value < lowestValue) {
                lowestValue = value;
                mostNeglected = key;
            }
        }

        if (mostNeglected == null) return;

        // Don't decay below a minimum floor (0.05)
        if (lowestValue <= 0.05f) return;

        // Apply soft decay
        float newValue = Math.max(0.05f, lowestValue - debtRate);
        values.put(mostNeglected, newValue);
    }

    /**
     * Resolves diminishing multiplier using a logistic saturation curve.
     * <p>
     * Formula: multiplier = 1 - (sigmoid(decayedCount) - 0.5) * 2 * (1 - floor)
     * <p>
     * This produces smooth diminishing returns:
     * - decayedCount = 0 → noveltyBonus (for completely fresh sources)
     * - decayedCount = midpoint → ~0.5 multiplier
     * - decayedCount >> midpoint → approaches floor
     *
     * @param decayedCount the exponentially decayed application count
     * @return multiplier in [floor, noveltyBonus]
     */
    private float resolveMultiplier(float decayedCount) {
        DiminishingReturnsConfig memCfg = config();
        double floor = memCfg.diminishingFloor();
        double steepness = configuredDiminishingSteepness();
        double midpoint = configuredDiminishingMidpoint();
        double noveltyBonus = memCfg.noveltyBonus();

        if (decayedCount <= 0f) {
            return (float) noveltyBonus;
        }

        // Logistic sigmoid: 1 / (1 + e^(-k*(x - m)))
        double sigmoid = 1.0 / (1.0 + Math.exp(-steepness * (decayedCount - midpoint)));

        // Map sigmoid [0.5, 1] to multiplier [1, floor]
        // sigmoid at midpoint = 0.5, so penalty = 0
        // sigmoid as x→∞ = 1, so penalty = (1-floor)
        double penalty = (sigmoid - 0.5) * 2.0 * (1.0 - floor);
        double multiplier = 1.0 - penalty;

        return (float) Math.max(floor, Math.min(noveltyBonus, multiplier));
    }

    /**
     * Computes a novelty score for a source based on how recently/frequently it was applied.
     * <p>
     * Returns a value in [0, 1]:
     * - 1.0 = completely novel (never applied or fully decayed)
     * - 0.0 = heavily saturated (at or above noveltyDecayCap)
     * <p>
     * This score is used to lerp between 1.0 and noveltyBonus as a final multiplier.
     *
     * @param itemId       the source key
     * @param halfLifeMs   memory half-life in milliseconds
     * @param currentTimeMs current game time in ms
     * @return novelty score in [0, 1]
     */
    @ApiStatus.Experimental
    public float resolveNoveltyScore(String itemId, long halfLifeMs, long currentTimeMs) {
        SourceMemoryEntry entry = sourceMemory.get(itemId);
        if (entry == null) {
            return 1.0f; // Never applied = fully novel
        }

        float decayed = entry.decayedApplicationCount(halfLifeMs, currentTimeMs);
        if (decayed < 0.1f) {
            return 1.0f; // Effectively forgotten = novel
        }

        double noveltyDecayCap = config().noveltyDecayCap();
        return Math.max(0f, 1.0f - (decayed / (float) noveltyDecayCap));
    }

    /**
     * Computes the blended multiplier combining source, category, and family signals
     * with dynamic weighting based on signal strength.
     * <p>
     * The blending formula:
     * <ol>
     *   <li>Get decayed counts for source, category, and family</li>
     *   <li>Resolve individual multipliers via logistic curve</li>
     *   <li>Compute dynamic weights based on signal strength</li>
     *   <li>Blend weighted contributions</li>
     *   <li>Apply novelty as final multiplicative layer</li>
     * </ol>
     *
     * @param itemId           the specific source key
     * @param dominantCategory the primary value category
     * @param familyKey        the source family, nullable
     * @param gameTimeMs       current game time in milliseconds
     * @return blended multiplier in [floor, noveltyBonus]
     */
    @ApiStatus.Experimental
    public float computeBlendedMultiplier(String itemId, String dominantCategory, String familyKey, long gameTimeMs) {
        DiminishingReturnsConfig memCfg = config();
        long halfLifeMs = memCfg.memoryWindowMinutes() * 60_000L;
        double floor = memCfg.diminishingFloor();
        double noveltyBonus = memCfg.noveltyBonus();

        // Get decayed counts for all three signals
        float itemDecayed = 0f;
        SourceMemoryEntry itemEntry = sourceMemory.get(itemId);
        if (itemEntry != null) {
            itemDecayed = itemEntry.decayedApplicationCount(halfLifeMs, gameTimeMs);
        }

        float categoryDecayed = 0f;
        if (dominantCategory != null) {
            SourceMemoryEntry catEntry = categoryMemory.get(dominantCategory);
            if (catEntry != null) {
                categoryDecayed = catEntry.decayedApplicationCount(halfLifeMs, gameTimeMs);
            }
        }

        float familyDecayed = 0f;
        if (familyKey != null) {
            SourceMemoryEntry famEntry = familyMemory.get(familyKey);
            if (famEntry != null) {
                familyDecayed = famEntry.decayedApplicationCount(halfLifeMs, gameTimeMs);
            }
        }

        // Resolve individual multipliers
        float itemMultiplier = resolveMultiplier(itemDecayed);
        float categoryMultiplier = resolveMultiplier(categoryDecayed);
        float familyMultiplier = familyKey != null ? resolveMultiplier(familyDecayed) : 1.0f;

        // Dynamic weight calculation based on signal strength
        // Family gets weight proportional to how much it's been applied
        float familyWeight = familyKey != null ? Math.min(0.2f, familyDecayed * 0.04f) : 0f;
        // Category gets weight proportional to category saturation
        float categoryWeight = Math.min(0.25f, categoryDecayed * 0.05f);
        // Source gets the remaining weight
        float itemWeight = 1.0f - categoryWeight - familyWeight;

        // Blend contributions
        // Apply novelty only to source freshness contribution, not category/family fatigue.
        float noveltyScore = resolveNoveltyScore(itemId, halfLifeMs, gameTimeMs);
        float noveltyFactor = 1.0f + (noveltyScore * ((float) noveltyBonus - 1.0f));
        float itemContribution = itemMultiplier * itemWeight * noveltyFactor;
        float categoryContribution = categoryMultiplier * categoryWeight;
        float familyContribution = familyMultiplier * familyWeight;
        float blendedMultiplier = itemContribution + categoryContribution + familyContribution;

        // Clamp to valid range
        return (float) Math.max(floor, Math.min(noveltyBonus, blendedMultiplier));
    }

    @ApiStatus.Experimental
    public SourceMemoryEntry getMemoryEntry(String itemId) {
        return sourceMemory.get(itemId);
    }

    // ── Visualization Accessors (Render-Thread Safe) ─────────────────────────────

    /**
     * Effective multiplier for tooltip display with full category/family context.
     * Pure read, no mutations - safe for render thread.
     *
     * @param sourceKey        the source key
     * @param dominantCategory the primary value category
     * @param familyKey        the source family (nullable)
     * @param gameTimeMs       current game time in ms
     * @return the multiplier that would apply
     */
    @ApiStatus.Experimental
    public float peekMultiplier(String sourceKey, String dominantCategory, String familyKey, long gameTimeMs) {
        return computeBlendedMultiplier(sourceKey, dominantCategory, familyKey, gameTimeMs);
    }

    /**
     * Returns category fatigue in [0, 1] where 0 = fully saturated, 1 = completely fresh.
     *
     * @param categoryKey the value category
     * @param gameTimeMs  current game time in ms
     * @return fatigue level
     */
    @ApiStatus.Experimental
    public float getCategoryFatigue(String categoryKey, long gameTimeMs) {
        SourceMemoryEntry entry = categoryMemory.get(categoryKey);
        if (entry == null) return 1.0f;
        DiminishingReturnsConfig memCfg = config();
        long halfLifeMs = memCfg.memoryWindowMinutes() * 60_000L;
        float decayed = entry.decayedApplicationCount(halfLifeMs, gameTimeMs);
        // Use same logistic curve as resolveMultiplier — invert so 1.0 = fresh, 0.0 = saturated
        float multiplier = resolveMultiplier(decayed);
        double floor = memCfg.diminishingFloor();
        double noveltyBonus = memCfg.noveltyBonus();
        // Normalize multiplier from [floor, noveltyBonus] to [0, 1]
        return (float) ((multiplier - floor) / (noveltyBonus - floor));
    }

    /**
     * Gets novelty score for a specific source.
     *
     * @param itemId     the source key
     * @param gameTimeMs current game time in ms
     * @return novelty score in [0, 1]
     */
    @ApiStatus.Experimental
    public float getNoveltyScore(String itemId, long gameTimeMs) {
        long halfLifeMs = config().memoryWindowMinutes() * 60_000L;
        return resolveNoveltyScore(itemId, halfLifeMs, gameTimeMs);
    }

    /**
     * Returns the top N most fatigued source families (lowest multiplier).
     *
     * @param n          number of families to return
     * @param gameTimeMs current game time in ms
     * @return list of family keys sorted by fatigue (most fatigued first)
     */
    @ApiStatus.Experimental
    public List<String> getMostFatiguedFamilies(int n, long gameTimeMs) {
        long halfLifeMs = config().memoryWindowMinutes() * 60_000L;

        return familyMemory.entrySet().stream()
                .filter(e -> !e.getValue().isEffectivelyExpired(halfLifeMs, gameTimeMs, 0.1f))
                .sorted((a, b) -> Float.compare(
                        b.getValue().decayedApplicationCount(halfLifeMs, gameTimeMs),
                        a.getValue().decayedApplicationCount(halfLifeMs, gameTimeMs)))
                .limit(n)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Returns the top N most neglected value categories (lowest bar values).
     *
     * @param n number of categories to return
     * @return list of category keys sorted by bar value (lowest first)
     */
    @ApiStatus.Experimental
    public List<String> getMostNeglectedCategories(int n) {
        return values.entrySet().stream()
                .sorted(Comparator.comparingDouble(e -> effectiveWellnessValue(e.getKey(), e.getValue())))
                .limit(n)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Full multiplier breakdown for debug display or advanced tooltips.
     *
     * @param sourceKey        the source key
     * @param dominantCategory the primary value category
     * @param familyKey        the source family (nullable)
     * @param gameTimeMs       current game time in ms
     * @return detailed breakdown record
     */
    @ApiStatus.Experimental
    public MultiplierBreakdown getMultiplierBreakdown(String sourceKey, String dominantCategory, String familyKey, long gameTimeMs) {
        DiminishingReturnsConfig memCfg = config();
        long halfLifeMs = memCfg.memoryWindowMinutes() * 60_000L;
        double noveltyBonus = memCfg.noveltyBonus();

        // Get decayed counts
        float itemDecayed = 0f;
        SourceMemoryEntry itemEntry = sourceMemory.get(sourceKey);
        if (itemEntry != null) {
            itemDecayed = itemEntry.decayedApplicationCount(halfLifeMs, gameTimeMs);
        }

        float categoryDecayed = 0f;
        if (dominantCategory != null) {
            SourceMemoryEntry catEntry = categoryMemory.get(dominantCategory);
            if (catEntry != null) {
                categoryDecayed = catEntry.decayedApplicationCount(halfLifeMs, gameTimeMs);
            }
        }

        float familyDecayed = 0f;
        if (familyKey != null) {
            SourceMemoryEntry famEntry = familyMemory.get(familyKey);
            if (famEntry != null) {
                familyDecayed = famEntry.decayedApplicationCount(halfLifeMs, gameTimeMs);
            }
        }

        // Calculate multipliers
        float itemMultiplier = resolveMultiplier(itemDecayed);
        float categoryMultiplier = resolveMultiplier(categoryDecayed);
        float familyMultiplier = familyKey != null ? resolveMultiplier(familyDecayed) : 1.0f;

        // Calculate weights
        float familyWeight = familyKey != null ? Math.min(0.2f, familyDecayed * 0.04f) : 0f;
        float categoryWeight = Math.min(0.25f, categoryDecayed * 0.05f);
        float itemWeight = 1.0f - categoryWeight - familyWeight;

        // Calculate contributions
        float itemContribution = itemMultiplier * itemWeight;
        float categoryContribution = categoryMultiplier * categoryWeight;
        float familyContribution = familyMultiplier * familyWeight;

        // Novelty
        float noveltyScore = resolveNoveltyScore(sourceKey, halfLifeMs, gameTimeMs);
        float noveltyContribution = 1.0f + (noveltyScore * ((float) noveltyBonus - 1.0f));

        itemContribution *= noveltyContribution;
        float finalMultiplier = itemContribution + categoryContribution + familyContribution;
        finalMultiplier = (float) Math.max(memCfg.diminishingFloor(), Math.min(noveltyBonus, finalMultiplier));

        return new MultiplierBreakdown(
                itemContribution, categoryContribution, familyContribution,
                noveltyContribution, finalMultiplier,
                itemWeight, categoryWeight, familyWeight
        );
    }

    /**
     * Set the memory config at a sync boundary (server-side) or on delta apply (client-side).
     * Called once per application/tick/sync — not on every method.
     */
    @ApiStatus.Experimental
    public void setMemoryConfig(DiminishingReturnsConfig cfg) {
        this.memoryConfig = cfg;
    }

    /** Sets the recent source ids to relay in the next full sync. MarieLib has no knowledge of what an id means. */
    @ApiStatus.Experimental
    public void setRecentIds(List<String> ids) {
        recentIds.clear();
        recentIds.addAll(ids);
    }

    /** Returns injected memory config. Must be set at a sync boundary before use. */
    private DiminishingReturnsConfig config() {
        if (memoryConfig == null) {
            throw new IllegalStateException(
                    "[MarieLib] DiminishingReturnsConfig not injected. This indicates a missed injection site.");
        }
        return memoryConfig;
    }

    /**
     * Detailed breakdown of how the multiplier was calculated.
     * Useful for debugging and advanced tooltips.
     */
    @ApiStatus.Experimental
    public record MultiplierBreakdown(
            float itemContribution,
            float categoryContribution,
            float familyContribution,
            float noveltyContribution,
            float finalMultiplier,
            float itemWeight,
            float categoryWeight,
            float familyWeight
    ) {}

    private long resolveCurrentTime(long tickTime) {
        if (tickTime > 0L) {
            return tickTime;
        }
        if (lastTickTime > 0L) {
            return lastTickTime;
        }
        return System.currentTimeMillis();
    }

    private void cleanupAllMemories(long currentTime) {
        long halfLifeMs = config().memoryWindowMinutes() * 60_000L;
        float threshold = 0.1f;
        removeExpiredEntries(sourceMemory, halfLifeMs, currentTime, threshold);
        removeExpiredEntries(categoryMemory, halfLifeMs, currentTime, threshold);
        removeExpiredEntries(familyMemory, halfLifeMs, currentTime, threshold);
    }

    private void removeExpiredEntries(Map<String, SourceMemoryEntry> memory, long halfLifeMs, long currentTime, float threshold) {
        Iterator<Map.Entry<String, SourceMemoryEntry>> iterator = memory.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, SourceMemoryEntry> entry = iterator.next();
            if (entry.getValue().isEffectivelyExpired(halfLifeMs, currentTime, threshold)) {
                iterator.remove();
            }
        }
    }

}
