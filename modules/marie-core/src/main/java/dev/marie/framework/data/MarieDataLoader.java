package dev.marie.framework.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.api.progression.MilestoneDefinition;
import dev.marie.framework.api.progression.TrackerMilestoneDefinition;
import dev.marie.framework.api.marieapi.MarieAPIState;
import dev.marie.framework.api.progression.ProfileDefinition;
import dev.marie.framework.api.source.SourcePairSynergy;
import dev.marie.framework.api.effects.SynergyDefinition;
import dev.marie.framework.api.effects.ThresholdEffect;
import dev.marie.framework.api.value.ValueDefinition;
import dev.marie.framework.compat.CompatDefinition;
import dev.marie.framework.core.MarieCore;
import dev.marie.framework.registry.MarieApiRegistries;
import dev.marie.framework.util.MarieRegistryUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Loads MarieLib datapack definitions from {@code data/<namespace>/<modid>/**}.
 */
@ApiStatus.Experimental
public final class MarieDataLoader extends SimpleJsonResourceReloadListener {

    /** Callbacks for delegating registration to the consuming mod. */
    public interface Callbacks {
        default void onApplyBegin() {}
        default void onApplyEnd() {}
        default void registerValue(ValueDefinition def) {}
        default void registerSourceClassification(ResourceLocation itemId, String valueKey, float amount) {}

        /**
         * Registers one per-item food override parsed from
         * {@code data/<namespace>/<modid>/food_overrides/<id>.json}. Directory-scanned sibling of the
         * legacy flat {@code config/food_overrides.json}. The consumer accumulates these between
         * {@link #onApplyBegin()} and {@link #onApplyEnd()} and publishes them as one layer.
         */
        default void registerFoodOverride(ResourceLocation itemId, Map<String, Float> nutrients, int calories, boolean enabled) {}
        default void registerCustomEffect(ThresholdEffect def) {}
        default void registerValueSynergy(SynergyDefinition def) {}
        default void registerSourcePairSynergy(SourcePairSynergy def) {}
        default void registerMilestone(MilestoneDefinition def) {}

        /**
         * Registers a tracker milestone parsed from a datapack file. Sibling to
         * {@link #registerMilestone(MilestoneDefinition)} for the generic MarieLib tracker
         * system — fully decoupled from it, with no shared storage or feature flag.
         *
         * <p><b>Java-side registration call</b> a consumer's callback implementation would make:</p>
         * <pre>{@code
         * @Override
         * public void registerTrackerMilestone(TrackerMilestoneDefinition def) {
         *     TrackerMilestoneRegistry.register(def);
         * }
         * }</pre>
         *
         * <p><b>Equivalent datapack file</b>
         * ({@code data/mymod/<modid>/tracker_milestones/hundred_blocks_mined.json}):</p>
         * <pre>{@code
         * {
         *   "tracker_id": "mymod:blocks_mined",
         *   "goal": 100.0,
         *   "scope": "lifetime"
         * }
         * }</pre>
         *
         * @param def the parsed tracker milestone definition
         */
        default void registerTrackerMilestone(TrackerMilestoneDefinition def) {}
        default void registerTrackingProfile(ProfileDefinition def) {}
        default void registerCompatEntry(CompatDefinition def) {}
        default void replaceSourceFamilies(Map<String, List<String>> families) {}
        default void replaceModuleLocks(Set<String> locked, Set<String> serverOnly) {}

        Callbacks NOOP = new Callbacks() {};
    }

    private static final Gson GSON = new GsonBuilder().create();

    private Callbacks callbacks = Callbacks.NOOP;

    private volatile Set<ResourceLocation> loadedValues = Set.of();
    private volatile Set<ResourceLocation> loadedSourceClassifications = Set.of();
    private volatile Set<ResourceLocation> loadedFoodOverrides = Set.of();
    private volatile Set<ResourceLocation> loadedEffects = Set.of();
    private volatile Set<ResourceLocation> loadedSynergies = Set.of();
    private volatile Set<ResourceLocation> loadedSourcePairSynergies = Set.of();
    private volatile Set<ResourceLocation> loadedMilestones = Set.of();
    private volatile Set<ResourceLocation> loadedTrackerMilestones = Set.of();
    private volatile Set<ResourceLocation> loadedProfiles = Set.of();
    private volatile Set<ResourceLocation> loadedCompatEntries = Set.of();

    /**
     * {@code "tag"} source classifications from the last apply, waiting for tags to bind. Item tags
     * are only bound to the registry after every reload listener's apply() has run, so expanding
     * them inside apply() sees an empty (first load) or stale (/reload) tag. Resolved by
     * {@link #resolvePendingTagClassifications()} from the server-side TagsUpdatedEvent.
     */
    private volatile List<PendingTagClassification> pendingTagClassifications = List.of();

    private record PendingTagClassification(ResourceLocation fileId, String rawTag, String valueKey, float amount) {}

    /** Collects {@link #pendingTagClassifications} during one apply() pass; null outside it. */
    private List<PendingTagClassification> nextPendingTagClassifications;

    public MarieDataLoader() {
        super(GSON, DatapackSchema.root());
    }

    public void setCallbacks(Callbacks callbacks) {
        this.callbacks = callbacks != null ? callbacks : Callbacks.NOOP;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> allJson, net.minecraft.server.packs.resources.ResourceManager resourceManager, ProfilerFiller profiler) {
        try (MarieAPIState.DatapackReloadScope scope = MarieAPIState.openForDatapackReload()) {
            DatapackDiagnostics diagnostics = DatapackDiagnostics.getInstance();
            diagnostics.clear();

            MarieApiRegistries.onDatapackApplyBegin();
            callbacks.onApplyBegin();
            nextPendingTagClassifications = new ArrayList<>();

            Set<ResourceLocation> nextValues = new LinkedHashSet<>();
            Set<ResourceLocation> nextSourceClassifications = new LinkedHashSet<>();
            Set<ResourceLocation> nextFoodOverrides = new LinkedHashSet<>();
            Set<ResourceLocation> nextEffects = new LinkedHashSet<>();
            Set<ResourceLocation> nextSynergies = new LinkedHashSet<>();
            Set<ResourceLocation> nextSourcePairSynergies = new LinkedHashSet<>();
            Set<ResourceLocation> nextMilestones = new LinkedHashSet<>();
            Set<ResourceLocation> nextTrackerMilestones = new LinkedHashSet<>();
            Set<ResourceLocation> nextProfiles = new LinkedHashSet<>();
            Set<ResourceLocation> nextCompatEntries = new LinkedHashSet<>();

            Map<ResourceLocation, JsonObject> values = filterDirectory(allJson, DatapackSchema.VALUES_DIR);
            Map<ResourceLocation, JsonObject> sourceClassifications = filterDirectory(allJson, DatapackSchema.SOURCE_CLASSIFICATIONS_DIR);
            Map<ResourceLocation, JsonObject> foodOverrides = filterDirectory(allJson, DatapackSchema.FOOD_OVERRIDES_DIR);
            Map<ResourceLocation, JsonObject> effects = filterDirectory(allJson, DatapackSchema.EFFECTS_DIR);
            Map<ResourceLocation, JsonObject> synergies = filterDirectory(allJson, DatapackSchema.SYNERGIES_DIR);
            Map<ResourceLocation, JsonObject> sourcePairSynergies = filterDirectory(allJson, DatapackSchema.SOURCE_SYNERGIES_DIR);
            Map<ResourceLocation, JsonObject> milestones = filterDirectory(allJson, DatapackSchema.MILESTONES_DIR);
            Map<ResourceLocation, JsonObject> trackerMilestones = filterDirectory(allJson, DatapackSchema.TRACKER_MILESTONES_DIR);
            Map<ResourceLocation, JsonObject> profiles = filterDirectory(allJson, DatapackSchema.TRACKING_PROFILES_DIR);
            Map<ResourceLocation, JsonObject> compat = filterDirectory(allJson, DatapackSchema.COMPAT_DIR);
            Map<ResourceLocation, JsonObject> sourceFamilies = filterDirectory(allJson, DatapackSchema.SOURCE_FAMILIES_DIR);
            Map<ResourceLocation, JsonObject> moduleLocks = filterDirectory(allJson, DatapackSchema.MODULE_LOCKS_DIR);

        for (Map.Entry<ResourceLocation, JsonObject> entry : values.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            JsonObject json = entry.getValue();
            String filePath = datapackFilePath(DatapackSchema.VALUES_DIR, fileId);
            if (!isValidForRegistration(json, SchemaDefinition.forValue(), filePath, diagnostics)) {
                continue;
            }
            try {
                ValueDefinition def = parseValue(fileId, json);
                callbacks.registerValue(def);
                nextValues.add(fileId);
            } catch (Exception ex) {
                warnMalformed(fileId, ex);
            }
        }

        for (Map.Entry<ResourceLocation, JsonObject> entry : sourceClassifications.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            JsonObject json = entry.getValue();
            String filePath = datapackFilePath(DatapackSchema.SOURCE_CLASSIFICATIONS_DIR, fileId);
            if (!isValidForRegistration(json, SchemaDefinition.forSourceClassification(), filePath, diagnostics)) {
                continue;
            }
            try {
                registerSourceClassification(fileId, json);
                nextSourceClassifications.add(fileId);
            } catch (Exception ex) {
                warnMalformed(fileId, ex);
            }
        }

        for (Map.Entry<ResourceLocation, JsonObject> entry : foodOverrides.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            JsonObject json = entry.getValue();
            String filePath = datapackFilePath(DatapackSchema.FOOD_OVERRIDES_DIR, fileId);
            if (!isValidForRegistration(json, SchemaDefinition.forFoodOverride(), filePath, diagnostics)) {
                continue;
            }
            try {
                registerFoodOverride(fileId, json);
                nextFoodOverrides.add(fileId);
            } catch (Exception ex) {
                warnMalformed(fileId, ex);
            }
        }

        for (Map.Entry<ResourceLocation, JsonObject> entry : effects.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            JsonObject json = entry.getValue();
            String filePath = datapackFilePath(DatapackSchema.EFFECTS_DIR, fileId);
            if (!isValidForRegistration(json, SchemaDefinition.forEffect(), filePath, diagnostics)) {
                continue;
            }
            try {
                ThresholdEffect def = parseEffect(fileId, json);
                callbacks.registerCustomEffect(def);
                nextEffects.add(fileId);
            } catch (Exception ex) {
                warnMalformed(fileId, ex);
            }
        }

        for (Map.Entry<ResourceLocation, JsonObject> entry : synergies.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            JsonObject json = entry.getValue();
            String filePath = datapackFilePath(DatapackSchema.SYNERGIES_DIR, fileId);
            if (!isValidForRegistration(json, SchemaDefinition.forSynergy(), filePath, diagnostics)) {
                continue;
            }
            try {
                SynergyDefinition def = parseSynergy(fileId, json);
                callbacks.registerValueSynergy(def);
                nextSynergies.add(fileId);
            } catch (Exception ex) {
                warnMalformed(fileId, ex);
            }
        }

        for (Map.Entry<ResourceLocation, JsonObject> entry : sourcePairSynergies.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            JsonObject json = entry.getValue();
            String filePath = datapackFilePath(DatapackSchema.SOURCE_SYNERGIES_DIR, fileId);
            if (!isValidForRegistration(json, SchemaDefinition.forSourcePairSynergy(), filePath, diagnostics)) {
                continue;
            }
            try {
                SourcePairSynergy def = parseSourcePairSynergy(fileId, json);
                callbacks.registerSourcePairSynergy(def);
                nextSourcePairSynergies.add(fileId);
            } catch (Exception ex) {
                warnMalformed(fileId, ex);
            }
        }

        for (Map.Entry<ResourceLocation, JsonObject> entry : milestones.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            JsonObject json = entry.getValue();
            String filePath = datapackFilePath(DatapackSchema.MILESTONES_DIR, fileId);
            if (!isValidForRegistration(json, SchemaDefinition.forMilestone(), filePath, diagnostics)) {
                continue;
            }
            try {
                MilestoneDefinition def = parseMilestone(fileId, json);
                callbacks.registerMilestone(def);
                nextMilestones.add(fileId);
            } catch (Exception ex) {
                warnMalformed(fileId, ex);
            }
        }

        for (Map.Entry<ResourceLocation, JsonObject> entry : trackerMilestones.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            JsonObject json = entry.getValue();
            String filePath = datapackFilePath(DatapackSchema.TRACKER_MILESTONES_DIR, fileId);
            if (!isValidForRegistration(json, SchemaDefinition.forTrackerMilestone(), filePath, diagnostics)) {
                continue;
            }
            try {
                TrackerMilestoneDefinition def = parseTrackerMilestone(fileId, json);
                callbacks.registerTrackerMilestone(def);
                nextTrackerMilestones.add(fileId);
            } catch (Exception ex) {
                warnMalformed(fileId, ex);
            }
        }

        for (Map.Entry<ResourceLocation, JsonObject> entry : profiles.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            JsonObject json = entry.getValue();
            String filePath = datapackFilePath(DatapackSchema.TRACKING_PROFILES_DIR, fileId);
            if (!isValidForRegistration(json, SchemaDefinition.forTrackingProfile(), filePath, diagnostics)) {
                continue;
            }
            try {
                ProfileDefinition def = parseTrackingProfile(fileId, json);
                callbacks.registerTrackingProfile(def);
                nextProfiles.add(fileId);
            } catch (Exception ex) {
                warnMalformed(fileId, ex);
            }
        }

        for (Map.Entry<ResourceLocation, JsonObject> entry : compat.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            JsonObject json = entry.getValue();
            String filePath = datapackFilePath(DatapackSchema.COMPAT_DIR, fileId);
            if (!isValidForRegistration(json, SchemaDefinition.forCompat(), filePath, diagnostics)) {
                continue;
            }
            try {
                CompatDefinition def = parseCompat(fileId, json);
                callbacks.registerCompatEntry(def);
                nextCompatEntries.add(fileId);
            } catch (Exception ex) {
                warnMalformed(fileId, ex);
            }
        }

        for (Map.Entry<ResourceLocation, JsonObject> entry : sourceFamilies.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            JsonObject json = entry.getValue();
            String filePath = datapackFilePath(DatapackSchema.SOURCE_FAMILIES_DIR, fileId);
            if (!isValidForRegistration(json, SchemaDefinition.forSourceFamilies(), filePath, diagnostics)) {
                continue;
            }
            try {
                applySourceFamilies(json);
            } catch (Exception ex) {
                warnMalformed(fileId, ex);
            }
        }

        for (Map.Entry<ResourceLocation, JsonObject> entry : moduleLocks.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            JsonObject json = entry.getValue();
            String filePath = datapackFilePath(DatapackSchema.MODULE_LOCKS_DIR, fileId);
            if (!isValidForRegistration(json, SchemaDefinition.forModuleLocks(), filePath, diagnostics)) {
                continue;
            }
            try {
                applyModuleLocks(json);
            } catch (Exception ex) {
                warnMalformed(fileId, ex);
            }
        }

            MarieApiRegistries.onDatapackApplyEnd();
            callbacks.onApplyEnd();

            loadedValues = Collections.unmodifiableSet(nextValues);
            loadedSourceClassifications = Collections.unmodifiableSet(nextSourceClassifications);
            loadedFoodOverrides = Collections.unmodifiableSet(nextFoodOverrides);
            loadedEffects = Collections.unmodifiableSet(nextEffects);
            loadedSynergies = Collections.unmodifiableSet(nextSynergies);
            loadedSourcePairSynergies = Collections.unmodifiableSet(nextSourcePairSynergies);
            loadedMilestones = Collections.unmodifiableSet(nextMilestones);
            loadedTrackerMilestones = Collections.unmodifiableSet(nextTrackerMilestones);
            loadedProfiles = Collections.unmodifiableSet(nextProfiles);
            loadedCompatEntries = Collections.unmodifiableSet(nextCompatEntries);
            pendingTagClassifications = List.copyOf(nextPendingTagClassifications);
            nextPendingTagClassifications = null;
        }
    }

    /**
     * Expands the {@code "tag"} source classifications queued by the last apply now that item tags
     * are bound. Called from the server-side TagsUpdatedEvent; runs inside a datapack-reload scope so
     * the entries are cleared by the next reload like every other datapack classification.
     */
    public void resolvePendingTagClassifications() {
        List<PendingTagClassification> pending = pendingTagClassifications;
        if (pending.isEmpty()) {
            return;
        }
        try (MarieAPIState.DatapackReloadScope scope = MarieAPIState.openForDatapackReload()) {
            for (PendingTagClassification entry : pending) {
                try {
                    String normalized = entry.rawTag().startsWith("#") ? entry.rawTag().substring(1) : entry.rawTag();
                    TagKey<net.minecraft.world.item.Item> key = ItemTags.create(ResourceLocation.parse(normalized));
                    int matched = 0;
                    for (Holder<net.minecraft.world.item.Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(key)) {
                        ResourceLocation itemId = MarieRegistryUtils.itemKey(holder.value());
                        if (itemId != null) {
                            callbacks.registerSourceClassification(itemId, entry.valueKey(), entry.amount());
                            matched++;
                        }
                    }
                    if (matched == 0) {
                        throw new IllegalArgumentException("Tag has no registered items: " + entry.rawTag());
                    }
                } catch (Exception ex) {
                    warnMalformed(entry.fileId(), ex);
                }
            }
        }
    }

    private static boolean isValidForRegistration(JsonObject json, SchemaDefinition schema, String filePath, DatapackDiagnostics diagnostics) {
        List<DatapackDiagnostic> fileDiagnostics = DatapackValidator.validate(json, schema, filePath);
        boolean hasErrors = false;
        for (DatapackDiagnostic diagnostic : fileDiagnostics) {
            diagnostics.record(diagnostic);
            if (diagnostic.severity() == DatapackDiagnostic.Severity.ERROR) {
                hasErrors = true;
            }
        }
        return !hasErrors;
    }

    private static String datapackFilePath(String directory, ResourceLocation fileId) {
        return "data/" + fileId.getNamespace() + "/" + DatapackSchema.root() + "/" + directory + "/" + fileId.getPath() + ".json";
    }

    public Set<ResourceLocation> getLoadedValues() {
        return loadedValues;
    }

    public Set<ResourceLocation> getLoadedSourceClassifications() {
        return loadedSourceClassifications;
    }

    public Set<ResourceLocation> getLoadedFoodOverrides() {
        return loadedFoodOverrides;
    }

    public Set<ResourceLocation> getLoadedEffects() {
        return loadedEffects;
    }

    public Set<ResourceLocation> getLoadedSynergies() {
        return loadedSynergies;
    }

    public Set<ResourceLocation> getLoadedSourcePairSynergies() {
        return loadedSourcePairSynergies;
    }

    public Set<ResourceLocation> getLoadedMilestones() {
        return loadedMilestones;
    }

    public Set<ResourceLocation> getLoadedTrackerMilestones() {
        return loadedTrackerMilestones;
    }

    public Set<ResourceLocation> getLoadedProfiles() {
        return loadedProfiles;
    }

    public Set<ResourceLocation> getLoadedCompatEntries() {
        return loadedCompatEntries;
    }

    private static Map<ResourceLocation, JsonObject> filterDirectory(Map<ResourceLocation, JsonElement> allJson, String directory) {
        String prefix = directory + "/";
        Map<ResourceLocation, JsonObject> result = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : allJson.entrySet()) {
            if (!entry.getKey().getPath().startsWith(prefix)) {
                continue;
            }
            if (!entry.getValue().isJsonObject()) {
                throw new IllegalArgumentException("Expected JSON object");
            }
            String withoutPrefix = entry.getKey().getPath().substring(prefix.length());
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(entry.getKey().getNamespace(), withoutPrefix);
            result.put(id, entry.getValue().getAsJsonObject());
        }
        return result;
    }

    private static ValueDefinition parseValue(ResourceLocation fileId, JsonObject json) {
        ValueDefinition.Builder builder = ValueDefinition.builder(fileId.getPath());
        builder.displayName(getRequiredString(json, DatapackSchema.KEY_DISPLAY_NAME));
        if (json.has("color")) {
            builder.color(json.get("color").getAsInt());
        }
        if (json.has("default_decay_rate")) {
            builder.defaultDecayRate(json.get("default_decay_rate").getAsFloat());
        }
        if (json.has("critical_threshold")) {
            builder.criticalThreshold(json.get("critical_threshold").getAsFloat());
        }
        if (json.has("low_threshold")) {
            builder.lowThreshold(json.get("low_threshold").getAsFloat());
        }
        if (json.has("excess_threshold")) {
            builder.excessThreshold(json.get("excess_threshold").getAsFloat());
        }
        if (json.has("amountScale")) {
            builder.amountScale(json.get("amountScale").getAsDouble());
        }
        return builder.build();
    }

    private void registerSourceClassification(ResourceLocation fileId, JsonObject json) {
        String valueKey = getRequiredString(json, DatapackSchema.KEY_VALUE_KEY);
        float amount = getRequiredFloat(json, DatapackSchema.KEY_AMOUNT);
        MarieRegistryUtils.requireValueKey(valueKey, "MarieDataLoader.registerSourceClassification");

        if (json.has(DatapackSchema.KEY_ITEM)) {
            ResourceLocation itemId = ResourceLocation.parse(getRequiredString(json, DatapackSchema.KEY_ITEM));
            callbacks.registerSourceClassification(itemId, valueKey, amount);
            return;
        }

        if (json.has(DatapackSchema.KEY_TAG)) {
            String rawTag = getRequiredString(json, DatapackSchema.KEY_TAG);
            String normalized = rawTag.startsWith("#") ? rawTag.substring(1) : rawTag;
            ResourceLocation.parse(normalized); // fail fast on a malformed id; expansion waits for tag binding
            nextPendingTagClassifications.add(new PendingTagClassification(fileId, rawTag, valueKey, amount));
            return;
        }

        throw new IllegalArgumentException("Entry must include either 'item' or 'tag'");
    }

    private void registerFoodOverride(ResourceLocation fileId, JsonObject json) {
        ResourceLocation itemId = ResourceLocation.parse(getRequiredString(json, DatapackSchema.KEY_ITEM));
        int calories = getOptionalInt(json, DatapackSchema.KEY_CALORIES, 0);
        boolean enabled = getOptionalBoolean(json, DatapackSchema.KEY_ENABLED, true);
        Map<String, Float> nutrients = new LinkedHashMap<>();
        if (json.has(DatapackSchema.KEY_NUTRIENTS) && json.get(DatapackSchema.KEY_NUTRIENTS).isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject(DatapackSchema.KEY_NUTRIENTS).entrySet()) {
                nutrients.put(e.getKey(), e.getValue().getAsFloat());
            }
        }
        callbacks.registerFoodOverride(itemId, nutrients, calories, enabled);
    }

    private static ThresholdEffect parseEffect(ResourceLocation fileId, JsonObject json) {
        return ThresholdEffect.builder()
                .valueKey(getRequiredString(json, DatapackSchema.KEY_VALUE_KEY))
                .threshold(getRequiredFloat(json, DatapackSchema.KEY_THRESHOLD))
                .thresholdType(ThresholdEffect.ThresholdType.valueOf(
                        getRequiredString(json, DatapackSchema.KEY_THRESHOLD_TYPE).toUpperCase(Locale.ROOT)))
                .effectId(ResourceLocation.parse(getRequiredString(json, DatapackSchema.KEY_EFFECT_ID)))
                .amplifier(getOptionalInt(json, DatapackSchema.KEY_AMPLIFIER, 0))
                .duration(getOptionalInt(json, DatapackSchema.KEY_DURATION, 200))
                .build();
    }

    private static SynergyDefinition parseSynergy(ResourceLocation fileId, JsonObject json) {
        return SynergyDefinition.builder(fileId.getPath())
                .valueA(getRequiredString(json, DatapackSchema.KEY_VALUE_A_KEY),
                        SynergyDefinition.LevelCondition.valueOf(
                                getRequiredString(json, DatapackSchema.KEY_VALUE_A_CONDITION).toUpperCase(Locale.ROOT)))
                .valueB(getRequiredString(json, DatapackSchema.KEY_VALUE_B_KEY),
                        SynergyDefinition.LevelCondition.valueOf(
                                getRequiredString(json, DatapackSchema.KEY_VALUE_B_CONDITION).toUpperCase(Locale.ROOT)))
                .bonusEffect(json.has(DatapackSchema.KEY_BONUS_EFFECT_ID)
                        ? ResourceLocation.parse(getRequiredString(json, DatapackSchema.KEY_BONUS_EFFECT_ID))
                        : null)
                .effectAmplifier(getOptionalInt(json, DatapackSchema.KEY_AMPLIFIER, 0))
                .effectDuration(getOptionalInt(json, DatapackSchema.KEY_EFFECT_DURATION, 200))
                .penalty(json.has(DatapackSchema.KEY_IS_PENALTY) && json.get(DatapackSchema.KEY_IS_PENALTY).getAsBoolean())
                .build();
    }

    private static SourcePairSynergy parseSourcePairSynergy(ResourceLocation fileId, JsonObject json) {
        return SourcePairSynergy.builder(fileId.getPath())
                .sourceA(ResourceLocation.parse(getRequiredString(json, DatapackSchema.KEY_SOURCE_A)))
                .sourceB(ResourceLocation.parse(getRequiredString(json, DatapackSchema.KEY_SOURCE_B)))
                .timeWindowTicks(getOptionalInt(json, DatapackSchema.KEY_TIME_WINDOW_TICKS, 100))
                .bonusValueKey(getRequiredString(json, DatapackSchema.KEY_BONUS_VALUE_KEY))
                .bonusAmount(getRequiredFloat(json, DatapackSchema.KEY_BONUS_AMOUNT))
                .valueModifier(getOptionalFloat(json, DatapackSchema.KEY_VALUE_MODIFIER, 1.0f))
                .modifierDurationTicks(getOptionalInt(json, DatapackSchema.KEY_MODIFIER_DURATION_TICKS, 0))
                .build();
    }

    private static MilestoneDefinition parseMilestone(ResourceLocation fileId, JsonObject json) {
        MilestoneDefinition.Builder builder = MilestoneDefinition.builder(fileId.getPath());
        builder.valueKey(getRequiredString(json, DatapackSchema.KEY_VALUE_KEY));
        builder.cumulativeGoal(getRequiredFloat(json, DatapackSchema.KEY_CUMULATIVE_GOAL));
        if (json.has(DatapackSchema.KEY_REWARD_EFFECT_ID)) {
            builder.rewardEffect(ResourceLocation.parse(
                    json.get(DatapackSchema.KEY_REWARD_EFFECT_ID).getAsString()));
        }
        if (json.has(DatapackSchema.KEY_AMPLIFIER)) {
            builder.rewardAmplifier(json.get(DatapackSchema.KEY_AMPLIFIER).getAsInt());
        }
        if (json.has(DatapackSchema.KEY_REWARD_DURATION)) {
            builder.rewardDuration(json.get(DatapackSchema.KEY_REWARD_DURATION).getAsInt());
        }
        if (json.has(DatapackSchema.KEY_ADVANCEMENT_ID)) {
            builder.advancement(ResourceLocation.parse(
                    json.get(DatapackSchema.KEY_ADVANCEMENT_ID).getAsString()));
        }
        return builder.build();
    }

    private static TrackerMilestoneDefinition parseTrackerMilestone(ResourceLocation fileId, JsonObject json) {
        TrackerMilestoneDefinition.Builder builder = TrackerMilestoneDefinition.builder(fileId.getPath());
        builder.trackerId(ResourceLocation.parse(getRequiredString(json, DatapackSchema.KEY_TRACKER_ID)));
        builder.goal(getRequiredFloat(json, DatapackSchema.KEY_GOAL));
        builder.scope(TrackerMilestoneDefinition.MilestoneScope.valueOf(
                getRequiredString(json, DatapackSchema.KEY_SCOPE).toUpperCase(Locale.ROOT)));
        if (json.has(DatapackSchema.KEY_REWARD_EFFECT_ID)) {
            builder.rewardEffect(ResourceLocation.parse(
                    json.get(DatapackSchema.KEY_REWARD_EFFECT_ID).getAsString()));
        }
        if (json.has(DatapackSchema.KEY_AMPLIFIER)) {
            builder.rewardAmplifier(json.get(DatapackSchema.KEY_AMPLIFIER).getAsInt());
        }
        if (json.has(DatapackSchema.KEY_REWARD_DURATION)) {
            builder.rewardDuration(json.get(DatapackSchema.KEY_REWARD_DURATION).getAsInt());
        }
        if (json.has(DatapackSchema.KEY_ADVANCEMENT_ID)) {
            builder.advancement(ResourceLocation.parse(
                    json.get(DatapackSchema.KEY_ADVANCEMENT_ID).getAsString()));
        }
        return builder.build();
    }

    private static ProfileDefinition parseTrackingProfile(ResourceLocation fileId, JsonObject json) {
        ProfileDefinition.Builder builder = ProfileDefinition.builder(fileId.getPath());
        builder.displayName(getRequiredString(json, DatapackSchema.KEY_DISPLAY_NAME));
        if (json.has(DatapackSchema.KEY_DESCRIPTION)) {
            builder.description(json.get(DatapackSchema.KEY_DESCRIPTION).getAsString());
        }
        if (json.has(DatapackSchema.KEY_CUSTOM_THRESHOLDS) && json.get(DatapackSchema.KEY_CUSTOM_THRESHOLDS).isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject(DatapackSchema.KEY_CUSTOM_THRESHOLDS).entrySet()) {
                builder.customThreshold(e.getKey(), e.getValue().getAsFloat());
            }
        }
        if (json.has(DatapackSchema.KEY_CUSTOM_DECAY_RATES) && json.get(DatapackSchema.KEY_CUSTOM_DECAY_RATES).isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject(DatapackSchema.KEY_CUSTOM_DECAY_RATES).entrySet()) {
                builder.customDecayRate(e.getKey(), e.getValue().getAsFloat());
            }
        }
        if (json.has(DatapackSchema.KEY_BONUS_EFFECTS) && json.get(DatapackSchema.KEY_BONUS_EFFECTS).isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray(DatapackSchema.KEY_BONUS_EFFECTS)) {
                builder.addBonusEffect(ResourceLocation.parse(element.getAsString()));
            }
        }
        return builder.build();
    }

    private static CompatDefinition parseCompat(ResourceLocation fileId, JsonObject json) throws Exception {
        String modId = json.has(DatapackSchema.KEY_MOD_ID) ? json.get(DatapackSchema.KEY_MOD_ID).getAsString() : fileId.getPath();
        String categoryRaw = json.has(DatapackSchema.KEY_CATEGORY) ? json.get(DatapackSchema.KEY_CATEGORY).getAsString() : "SOURCE_MOD";
        CompatDefinition.CompatCategory category = CompatDefinition.CompatCategory.valueOf(categoryRaw.toUpperCase(Locale.ROOT));
        CompatDefinition.Builder builder = CompatDefinition.builder(modId).category(category);
        if (json.has(DatapackSchema.KEY_MAPPINGS) && json.get(DatapackSchema.KEY_MAPPINGS).isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject(DatapackSchema.KEY_MAPPINGS).entrySet()) {
                ResourceLocation itemId = ResourceLocation.parse(e.getKey());
                JsonElement mappingValue = e.getValue();
                if (mappingValue.isJsonObject()) {
                    JsonObject mappingObj = mappingValue.getAsJsonObject();
                    String valueKey = getRequiredString(mappingObj, "value");
                    float amount = getOptionalFloat(mappingObj, "amount", 1.0f);
                    builder.addSourceMapping(itemId, valueKey, amount);
                } else {
                    builder.addSourceMapping(itemId, mappingValue.getAsString(), 1.0f);
                }
            }
        }
        return builder.build();
    }

    private void applySourceFamilies(JsonObject json) {
        Map<String, List<String>> configured = new LinkedHashMap<>();
        JsonObject families = json.getAsJsonObject(DatapackSchema.KEY_FAMILIES);
        for (Map.Entry<String, JsonElement> entry : families.entrySet()) {
            if (!entry.getValue().isJsonArray()) {
                continue;
            }
            List<String> keywords = new ArrayList<>();
            for (JsonElement keyword : entry.getValue().getAsJsonArray()) {
                keywords.add(keyword.getAsString());
            }
            configured.put(entry.getKey(), keywords);
        }
        callbacks.replaceSourceFamilies(configured);
    }

    private void applyModuleLocks(JsonObject json) {
        Set<String> locked = new LinkedHashSet<>();
        Set<String> serverOnly = new LinkedHashSet<>();
        if (json.has(DatapackSchema.KEY_LOCKED) && json.get(DatapackSchema.KEY_LOCKED).isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray(DatapackSchema.KEY_LOCKED)) {
                locked.add(element.getAsString());
            }
        }
        if (json.has(DatapackSchema.KEY_SERVER_ONLY) && json.get(DatapackSchema.KEY_SERVER_ONLY).isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray(DatapackSchema.KEY_SERVER_ONLY)) {
                serverOnly.add(element.getAsString());
            }
        }
        callbacks.replaceModuleLocks(locked, serverOnly);
    }

    private static String getRequiredString(JsonObject json, String key) {
        if (!json.has(key)) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        return json.get(key).getAsString();
    }

    private static float getRequiredFloat(JsonObject json, String key) {
        if (!json.has(key)) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        return json.get(key).getAsFloat();
    }

    private static int getOptionalInt(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }

    private static float getOptionalFloat(JsonObject json, String key, float fallback) {
        return json.has(key) ? json.get(key).getAsFloat() : fallback;
    }

    private static boolean getOptionalBoolean(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }

    private static void warnMalformed(ResourceLocation fileId, Exception ex) {
        String path = "data/" + fileId.getNamespace() + "/" + DatapackSchema.root() + "/" + fileId.getPath() + ".json";
        String message = ex.getMessage();
        if (message != null && message.startsWith("Value already registered: ")) {
            String key = message.substring("Value already registered: ".length());
            MarieCore.LOGGER.debug("[MarieLib] Value '{}' already registered (loaded from config); ignoring datapack duplicate at {}", key, path);
            return;
        }
        MarieCore.LOGGER.warn("[MarieLib] Skipping malformed datapack entry at {}: {}", path, message);
    }
}
