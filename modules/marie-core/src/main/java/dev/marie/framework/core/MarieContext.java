package dev.marie.framework.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.api.value.ValueDefinition;
import dev.marie.framework.api.value.ValueModifierContext;
import dev.marie.framework.api.value.ValueSourceTrigger;
import dev.marie.framework.api.registry.ValueRegistry;
import dev.marie.framework.config.MariesLibConfigBridge;
import dev.marie.framework.config.PresetRegistry;
import dev.marie.framework.tracking.AttachmentTrackingDataProvider;
import dev.marie.framework.runtime.SourceClassificationRegistry;
import dev.marie.framework.util.MarieRegistryUtils;
import dev.marie.framework.util.MarieValidation;
import dev.marie.framework.scan.ResolutionStageHandler;
import dev.marie.framework.tracking.TrackingData;
import dev.marie.framework.tracking.DiminishingReturnsConfig;
import dev.marie.framework.tracking.RespawnValueBehavior;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Runtime context injected by the consuming mod at bootstrap.
 * Holds the mod ID and optional overrides that MarieLib cannot own.
 *
 * <p>Only {@link Builder#build()} requires {@code modId}; every other builder field has a
 * safe lib-owned default. Use {@link MarieBootstrap#attach} for zero-config wiring.</p>
 */
@ApiStatus.Experimental
public final class MarieContext implements MarieLibSettings, IMarieConfig {

    @ApiStatus.Internal
    public record SourceDelta(float total, Map<String, Float> values) {}

    @ApiStatus.Experimental
    @FunctionalInterface
    public interface SourceDeltaResolver {
        /**
         * Resolves value deltas for a source item.
         *
         * @param stack       the item stack being applied (may be null for
         *                    non-item triggers)
         * @param level       the current level
         * @param payload     the numeric payload from the trigger (consuming
         *                    mod defines what this means — could be energy,
         *                    EMC value, damage dealt, anything)
         * @param matchedBars the pre-resolved value bar weights for this item
         * @return a SourceDelta with total and per-value amounts
         */
        SourceDelta resolve(ItemStack stack, Level level, double payload,
                Map<String, Float> matchedBars);
    }

    private static volatile MarieContext instance;

    /** Per-thread override of {@link #get()} while one mod's hook runs; see {@link #runAs}. */
    private static final ThreadLocal<MarieContext> SCOPED = new ThreadLocal<>();

    private final String modId;
    private final Supplier<Float> scannerConfidenceSpreadThreshold;
    private final Supplier<Float> compositeRatioThreshold;
    private final Supplier<Boolean> scannerEnableRecipeInheritance;
    private final Supplier<Boolean> enableDebugLogging;
    private final Supplier<Predicate<ItemStack>> sourceItemFilter;
    private final Supplier<Predicate<ItemStack>> sourceExclusionFilter;
    private final Supplier<Long> memoryWindowMinutes;
    private final Supplier<Integer> memoryWindowCount;
    private final Supplier<Long> streakWindowMs;
    private final Supplier<Float> streakWeight;
    private final Supplier<Float> debtThreshold;
    private final Supplier<Float> debtDecayRate;
    private final Supplier<Float> diminishingSteepness;
    private final Supplier<Float> diminishingMidpoint;
    private final Supplier<Boolean> debugMemoryLogging;
    private final Supplier<Float> excessThreshold;
    private final Supplier<Float> lowThreshold;
    private final Supplier<Float> criticalThreshold;
    private final Runnable onFullTrackingDataSynced;
    private final Supplier<Object> configScreenFactory;
    private final Function<Object, Object> exportScreenFactory;
    private final Function<Object, Object> importScreenFactory;
    private final Consumer<Map<String, Float>> onValuesDeltaReceived;
    private final Supplier<DiminishingReturnsConfig> clientMemoryConfigProvider;
    private final Function<String, DiminishingReturnsConfig> clientMemoryConfigBySourceProvider;
    private final Supplier<JsonObject> configExporter;
    private final Consumer<JsonObject> configImporter;
    private final Supplier<PresetRegistry.PresetValues> currentConfigPresetValues;
    private final Runnable ensureBuiltInPresetsOnDisk;
    private final Consumer<PresetRegistry.PresetValues> applyPresetValues;
    private final Runnable enableAllEffectsForPresets;
    private final Function<String, String> valueIconProvider;
    private final BiFunction<ItemStack, Player, Map<String, Float>> tooltipValueResolver;
    private final Supplier<TrackingData> clientTrackingDataProvider;
    private final Function<ResourceLocation, String> sourceFamilyResolver;
    private final Function<Item, Map<String, Float>> valueTagScoresProvider;
    private final Function<String, String> tagRoleResolver;
    private final BiPredicate<ServerPlayer, ValueSourceTrigger> heavySourceBlocker;
    private final BiPredicate<ServerPlayer, ValueSourceTrigger> lightSourceBlocker;
    private final DoubleSupplier multiValueInheritanceThreshold;
    private final ResolutionStageHandler[] runtimeResolverStages;
    private final Supplier<DiminishingReturnsConfig> trackingMemoryConfigProvider;
    @Nullable
    private final Function<String, DiminishingReturnsConfig> trackingMemoryConfigBySourceProvider;
    private final BiFunction<ItemStack, Level, Map<String, Float>> sourceValueResolver;
    private final SourceDeltaResolver sourceDeltaResolver;
    private final BiConsumer<ServerPlayer, TrackingData> effectApplier;
    private final Consumer<ServerPlayer> effectClearer;
    private final Supplier<Integer> decayIntervalTicks;
    private final Function<String, Float> decayRateResolver;
    private final Supplier<Boolean> showJoinMessage;
    private final Supplier<Component> joinMessageLine1;
    private final Supplier<Component> joinMessageLine2;
    private final BiConsumer<ServerPlayer, TrackingData> trackingDeltaSyncer;
    private final Consumer<ServerPlayer> syncOnJoin;
    private final Supplier<RespawnValueBehavior> respawnValueBehavior;
    @Nullable
    private final BiConsumer<ServerPlayer, TrackingData> respawnValueHandler;
    @Nullable
    private final MarieDataProvider dataProvider;
    @Nullable
    private final MarieRegistrationDelegate registrationDelegate;
    private final Runnable cacheInvalidatedHook;
    private final Consumer<MinecraftServer> reloadBroadcastHook;
    private final BiFunction<ValueModifierContext, Float, Float> postValueModifierHook;
    private final Supplier<Boolean> trackerSystemEnabled;
    private final Supplier<Integer> trackerMaxRetention;
    private final Supplier<Integer> trackerWeeklyPeriodDays;
    private final Supplier<Integer> trackerMonthlyPeriodDays;
    private final Supplier<Integer> trackerSyncIntervalTicks;
    private final BiConsumer<ServerPlayer, dev.marie.framework.tracking.tracker.definition.TrackerHistoryEntry> onTrackerPeriodCompletedHook;

    private MarieContext(Builder builder) {
        this.modId = builder.modId;
        this.scannerConfidenceSpreadThreshold = builder.scannerConfidenceSpreadThreshold;
        this.compositeRatioThreshold = builder.compositeRatioThreshold;
        this.scannerEnableRecipeInheritance = builder.scannerEnableRecipeInheritance;
        this.enableDebugLogging = builder.enableDebugLogging;
        this.sourceItemFilter = builder.sourceItemFilter;
        this.sourceExclusionFilter = builder.sourceExclusionFilter;
        this.memoryWindowMinutes = builder.memoryWindowMinutes;
        this.memoryWindowCount = builder.memoryWindowCount;
        this.streakWindowMs = builder.streakWindowMs;
        this.streakWeight = builder.streakWeight;
        this.debtThreshold = builder.debtThreshold;
        this.debtDecayRate = builder.debtDecayRate;
        this.diminishingSteepness = builder.diminishingSteepness;
        this.diminishingMidpoint = builder.diminishingMidpoint;
        this.debugMemoryLogging = builder.debugMemoryLogging;
        this.excessThreshold = builder.excessThreshold;
        this.lowThreshold = builder.lowThreshold;
        this.criticalThreshold = builder.criticalThreshold;
        this.onFullTrackingDataSynced = builder.onFullTrackingDataSynced;
        this.configScreenFactory = builder.configScreenFactory;
        this.exportScreenFactory = builder.exportScreenFactory;
        this.importScreenFactory = builder.importScreenFactory;
        this.onValuesDeltaReceived = builder.onValuesDeltaReceived;
        this.clientMemoryConfigProvider = builder.clientMemoryConfigProvider;
        this.clientMemoryConfigBySourceProvider = builder.clientMemoryConfigBySourceProvider;
        this.configExporter = builder.configExporter;
        this.configImporter = builder.configImporter;
        this.currentConfigPresetValues = builder.currentConfigPresetValues;
        this.ensureBuiltInPresetsOnDisk = builder.ensureBuiltInPresetsOnDisk;
        this.applyPresetValues = builder.applyPresetValues;
        this.enableAllEffectsForPresets = builder.enableAllEffectsForPresets;
        this.valueIconProvider = builder.valueIconProvider;
        this.tooltipValueResolver = builder.tooltipValueResolver;
        this.clientTrackingDataProvider = builder.clientTrackingDataProvider;
        this.sourceFamilyResolver = builder.sourceFamilyResolver;
        this.valueTagScoresProvider = builder.valueTagScoresProvider;
        this.tagRoleResolver = builder.tagRoleResolver;
        this.heavySourceBlocker = builder.heavySourceBlocker;
        this.lightSourceBlocker = builder.lightSourceBlocker;
        this.multiValueInheritanceThreshold = builder.multiValueInheritanceThreshold;
        this.runtimeResolverStages = builder.runtimeResolverStages;
        this.trackingMemoryConfigProvider = builder.trackingMemoryConfigProvider;
        this.trackingMemoryConfigBySourceProvider = builder.trackingMemoryConfigBySourceProvider;
        this.sourceValueResolver = builder.sourceValueResolver;
        this.sourceDeltaResolver = builder.sourceDeltaResolver;
        this.effectApplier = builder.effectApplier;
        this.effectClearer = builder.effectClearer;
        this.decayIntervalTicks = builder.decayIntervalTicks;
        this.decayRateResolver = builder.decayRateResolver;
        this.showJoinMessage = builder.showJoinMessage;
        this.joinMessageLine1 = builder.joinMessageLine1;
        this.joinMessageLine2 = builder.joinMessageLine2;
        this.trackingDeltaSyncer = builder.trackingDeltaSyncer;
        this.syncOnJoin = builder.syncOnJoin;
        this.respawnValueBehavior = builder.respawnValueBehavior;
        this.respawnValueHandler = builder.respawnValueHandler;
        this.dataProvider = builder.dataProvider != null
                ? builder.dataProvider
                : new AttachmentTrackingDataProvider();
        this.registrationDelegate = builder.registrationDelegate;
        this.cacheInvalidatedHook = builder.cacheInvalidatedHook;
        this.reloadBroadcastHook = builder.reloadBroadcastHook;
        this.postValueModifierHook = builder.postValueModifierHook;
        this.trackerSystemEnabled = builder.trackerSystemEnabled;
        this.trackerMaxRetention = builder.trackerMaxRetention;
        this.trackerWeeklyPeriodDays = builder.trackerWeeklyPeriodDays;
        this.trackerMonthlyPeriodDays = builder.trackerMonthlyPeriodDays;
        this.trackerSyncIntervalTicks = builder.trackerSyncIntervalTicks;
        this.onTrackerPeriodCompletedHook = builder.onTrackerPeriodCompletedHook;
    }

    @ApiStatus.Stable
    public static void register(MarieContext context) {
        instance = context;
        MarieModRegistry.register(context);
    }

    /**
     * Returns the most recently {@link #register}ed context.
     *
     * <p>When exactly one mod attaches MarieLib this is unambiguous. When more than one mod
     * attaches, every mod after the first overwrites this reference, so gameplay code that reads
     * {@code get()} runs the <em>last-attached</em> mod's hooks even for values, trackers, or
     * players that belong to an earlier-attached mod. Call sites that have a value key, tracker
     * id, or item/mod context available should resolve through {@link #forValue(String)},
     * {@link #forMod(String)}, or fan out over {@link MarieModRegistry#getAll()} instead — this
     * method remains only as a "primary mod" fallback for call sites with no such key (e.g.
     * process-wide scanner constants) and for the common single-mod case.</p>
     */
    @ApiStatus.Stable
    public static MarieContext get() {
        MarieContext scoped = SCOPED.get();
        if (scoped != null) {
            return scoped;
        }
        MarieContext ctx = instance;
        if (ctx == null) {
            throw new IllegalStateException("MarieContext not registered");
        }
        return ctx;
    }

    /**
     * Runs {@code action} with {@link #get()} returning {@code ctx} on this thread, so a fan-out over
     * {@link MarieModRegistry#getAll()} that invokes each mod's hook (e.g. re-registering trackers on
     * reload) has registries record that mod as the owner rather than the last-attached one.
     */
    @ApiStatus.Internal
    public static void runAs(MarieContext ctx, Runnable action) {
        MarieContext previous = SCOPED.get();
        SCOPED.set(ctx);
        try {
            action.run();
        } finally {
            if (previous != null) {
                SCOPED.set(previous);
            } else {
                SCOPED.remove();
            }
        }
    }

    @ApiStatus.Stable
    public static boolean isRegistered() {
        return instance != null;
    }

    /**
     * Resolves the {@link MarieContext} of the mod that registered {@code valueKey} (via
     * {@link ValueRegistry#ownerModId(String)}), falling back to {@link #get()} when the key is
     * unknown or was registered before any context was attached. Use this instead of {@link #get()}
     * for any hook keyed off a specific value (decay rate, thresholds, post-value modifiers, icons,
     * tooltip/tag resolution) so multi-mod setups dispatch to the mod that actually owns the value.
     */
    @ApiStatus.Experimental
    public static MarieContext forValue(String valueKey) {
        String ownerModId = valueKey != null ? ValueRegistry.ownerModId(valueKey) : null;
        if (ownerModId != null) {
            MarieContext owner = MarieModRegistry.get(ownerModId);
            if (owner != null) {
                return owner;
            }
        }
        return get();
    }

    /**
     * Resolves the {@link MarieContext} registered for {@code modId}, falling back to {@link #get()}
     * when that mod hasn't attached (or {@code modId} is null).
     */
    @ApiStatus.Experimental
    public static MarieContext forMod(@Nullable String modId) {
        MarieContext ctx = modId != null ? MarieModRegistry.get(modId) : null;
        return ctx != null ? ctx : get();
    }

    /**
     * Resolves the {@link MarieContext} of the mod that registered {@code trackerId} (via
     * {@link dev.marie.framework.tracking.tracker.registry.TrackerRegistry#ownerModId}), falling
     * back to {@link #get()} when the tracker is unknown.
     */
    @ApiStatus.Experimental
    public static MarieContext forTracker(ResourceLocation trackerId) {
        String ownerModId = trackerId != null
                ? dev.marie.framework.tracking.tracker.registry.TrackerRegistry.ownerModId(trackerId)
                : null;
        if (ownerModId != null) {
            MarieContext owner = MarieModRegistry.get(ownerModId);
            if (owner != null) {
                return owner;
            }
        }
        return get();
    }

    /**
     * True if any attached mod's {@link #sourceItemFilter()} allows this stack as a value source —
     * an item excluded by one mod's filter may still be a legitimate source for another mod's
     * values, so a single mod objecting must not veto every other mod. The default filter (for a
     * mod that never calls {@link Builder#sourceItemFilter}) rejects everything, so a mod with no
     * opinion on source items abstains rather than voting yes for every item on behalf of mods
     * that did configure a real filter.
     */
    @ApiStatus.Experimental
    public static boolean isSourceItemAllowed(ItemStack stack) {
        for (MarieContext ctx : MarieModRegistry.getAll()) {
            if (ctx.sourceItemFilter().test(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if any attached mod's {@link #sourceExclusionFilter()} considers {@code stack} excluded
     * from its own classification — the OR, not a unanimous vote, since one mod's player-authored
     * exclusion is a real "don't apply my values to this item" and must not be diluted by every
     * other attached mod abstaining (the default filter).
     */
    @ApiStatus.Experimental
    public static boolean isSourceExcludedByAnyMod(ItemStack stack) {
        for (MarieContext ctx : MarieModRegistry.getAll()) {
            if (ctx.sourceExclusionFilter().test(stack)) {
                return true;
            }
        }
        return false;
    }

    @Override
    @ApiStatus.Stable
    public String modId() {
        return modId;
    }

    @Override
    @ApiStatus.Internal
    public float scannerConfidenceSpreadThreshold() {
        return scannerConfidenceSpreadThreshold.get();
    }

    @Override
    @ApiStatus.Internal
    public float compositeRatioThreshold() {
        return compositeRatioThreshold.get();
    }

    @Override
    @ApiStatus.Internal
    public boolean scannerEnableRecipeInheritance() {
        return scannerEnableRecipeInheritance.get();
    }

    @Override
    @ApiStatus.Internal
    public boolean enableDebugLogging() {
        return enableDebugLogging.get();
    }

    @ApiStatus.Stable
    public List<String> valueKeys() {
        return ValueRegistry.getAll()
                .stream()
                .map(ValueDefinition::getId)
                .toList();
    }

    @ApiStatus.Internal
    public Predicate<ItemStack> sourceItemFilter() {
        return sourceItemFilter.get();
    }

    /**
     * Whether {@code this} mod considers {@code stack} excluded from its own value classification —
     * e.g. a player-authored "don't classify this item" toggle a mod builds on top of MariesLib (see
     * {@link dev.marie.framework.handler.SourceApplicationPipeline#process}, which consults this
     * across every attached mod before letting a generic {@code SourceClassificationRegistry}
     * override win outright). The default (for a mod that never calls {@link
     * Builder#sourceExclusionFilter}) rejects nothing, matching a mod with no exclusion concept of
     * its own staying silent rather than vetoing an item on behalf of a mod that does have one.
     */
    @ApiStatus.Internal
    public Predicate<ItemStack> sourceExclusionFilter() {
        return sourceExclusionFilter.get();
    }

    @ApiStatus.Internal
    public long memoryWindowMinutes() {
        return memoryWindowMinutes.get();
    }

    @ApiStatus.Internal
    public int memoryWindowCount() {
        return memoryWindowCount.get();
    }

    @ApiStatus.Internal
    public long streakWindowMs() {
        return streakWindowMs.get();
    }

    @ApiStatus.Internal
    public float streakWeight() {
        return streakWeight.get();
    }

    @ApiStatus.Internal
    public float debtThreshold() {
        return debtThreshold.get();
    }

    @ApiStatus.Internal
    public float debtDecayRate() {
        return debtDecayRate.get();
    }

    @ApiStatus.Internal
    public float diminishingSteepness() {
        return diminishingSteepness.get();
    }

    @ApiStatus.Internal
    public float diminishingMidpoint() {
        return diminishingMidpoint.get();
    }

    @ApiStatus.Internal
    public boolean debugMemoryLogging() {
        return debugMemoryLogging.get();
    }

    @ApiStatus.Internal
    public float excessThreshold() {
        return excessThreshold.get();
    }

    @ApiStatus.Internal
    public float lowThreshold() {
        return lowThreshold.get();
    }

    @ApiStatus.Internal
    public float criticalThreshold() {
        return criticalThreshold.get();
    }

    @ApiStatus.Internal
    public void onFullTrackingDataSynced() {
        onFullTrackingDataSynced.run();
    }

    @ApiStatus.Internal
    public Object configScreenFactory() {
        return configScreenFactory.get();
    }

    @ApiStatus.Internal
    public Object exportScreenFactory(Object parent) {
        return exportScreenFactory.apply(parent);
    }

    @ApiStatus.Internal
    public Object importScreenFactory(Object parent) {
        return importScreenFactory.apply(parent);
    }

    @ApiStatus.Internal
    public void onValuesDeltaReceived(Map<String, Float> delta) {
        onValuesDeltaReceived.accept(delta);
    }

    @ApiStatus.Internal
    public DiminishingReturnsConfig clientMemoryConfigProvider() {
        return clientMemoryConfigProvider.get();
    }

    /**
     * Source-aware client-side variant, mirroring {@link #trackingMemoryConfig(String)} on the
     * server side: consulted by tooltip rendering ({@code MarieTooltipHelper}) so the "Diminished
     * (N%)" preview line and its scaled gain numbers reflect a per-item exemption the same way the
     * actual server-side application does, instead of always falling back to one curve for every
     * item. Falls back to {@link #clientMemoryConfigProvider()} when the consumer never called
     * {@link Builder#clientMemoryConfigProvider(Function)}.
     */
    @ApiStatus.Internal
    public DiminishingReturnsConfig clientMemoryConfig(String sourceKey) {
        if (clientMemoryConfigBySourceProvider == null) {
            return clientMemoryConfigProvider();
        }
        DiminishingReturnsConfig cfg = clientMemoryConfigBySourceProvider.apply(sourceKey);
        return cfg != null ? cfg : defaultDiminishingReturnsConfig();
    }

    @ApiStatus.Internal
    public JsonObject configExporter() {
        return configExporter.get();
    }

    @ApiStatus.Internal
    public void configImporter(JsonObject json) {
        configImporter.accept(json);
    }

    @ApiStatus.Internal
    public PresetRegistry.PresetValues currentConfigPresetValues() {
        return currentConfigPresetValues.get();
    }

    @ApiStatus.Internal
    public void ensureBuiltInPresetsOnDisk() {
        ensureBuiltInPresetsOnDisk.run();
    }

    @ApiStatus.Internal
    public void applyPresetValues(PresetRegistry.PresetValues values) {
        applyPresetValues.accept(values);
    }

    @ApiStatus.Internal
    public void enableAllEffectsForPresets() {
        enableAllEffectsForPresets.run();
    }

    @ApiStatus.Internal
    public String valueIcon(String key) {
        return valueIconProvider.apply(key);
    }

    @ApiStatus.Internal
    public BiFunction<ItemStack, Player, Map<String, Float>> tooltipValueResolver() {
        return tooltipValueResolver;
    }

    @ApiStatus.Internal
    public Supplier<TrackingData> clientTrackingDataProvider() {
        return clientTrackingDataProvider;
    }

    @ApiStatus.Internal
    public Function<ResourceLocation, String> sourceFamilyResolver() {
        return sourceFamilyResolver;
    }

    @ApiStatus.Internal
    public Function<Item, Map<String, Float>> valueTagScoresProvider() {
        return valueTagScoresProvider;
    }

    /**
     * Resolves a named tag role to a full tag path for this mod's domain.
     * The consuming mod maps well-known role keys to their actual tag paths.
     *
     * The consuming mod may register any role keys it needs (e.g. "source_override").
     * Returns null if the role is not mapped, which means no tag filtering
     * is applied for that role.
     */
    @Nullable
    @ApiStatus.Internal
    public String resolveTagRole(String role) {
        return tagRoleResolver.apply(role);
    }

    /**
     * Predicate that decides whether a source trigger should be blocked
     * because it is considered "heavy" for the current player state.
     * The consuming mod defines what "heavy" means — the lib just asks.
     *
     * Return true to block the trigger, false to allow it.
     * Default: never block (always returns false).
     */
    @ApiStatus.Internal
    public boolean isHeavySourceBlocked(ServerPlayer player, ValueSourceTrigger trigger) {
        return heavySourceBlocker.test(player, trigger);
    }

    /**
     * Predicate that decides whether a source trigger should be blocked
     * because it is considered "light" for the current player state.
     * Default: never block (always returns false).
     */
    @ApiStatus.Internal
    public boolean isLightSourceBlocked(ServerPlayer player, ValueSourceTrigger trigger) {
        return lightSourceBlocker.test(player, trigger);
    }

    @Override
    @ApiStatus.Internal
    public double multiValueInheritanceThreshold() {
        return multiValueInheritanceThreshold.getAsDouble();
    }

    @ApiStatus.Internal
    public ResolutionStageHandler[] runtimeResolverStages() {
        return runtimeResolverStages;
    }

    @Override
    @ApiStatus.Internal
    public DiminishingReturnsConfig trackingMemoryConfig() {
        DiminishingReturnsConfig cfg = trackingMemoryConfigProvider.get();
        return cfg != null ? cfg : defaultDiminishingReturnsConfig();
    }

    /**
     * Source-aware variant consulted by {@link dev.marie.framework.handler.SourceApplicationPipeline#process}
     * so a mod can return a different curve for a specific source item/trigger (e.g. an item flagged
     * to bypass diminishing returns). Falls back to {@link #trackingMemoryConfig()} when the consumer
     * never called {@link Builder#trackingMemoryConfigProvider(Function)}.
     */
    @Override
    @ApiStatus.Internal
    public DiminishingReturnsConfig trackingMemoryConfig(String sourceKey) {
        if (trackingMemoryConfigBySourceProvider == null) {
            return trackingMemoryConfig();
        }
        DiminishingReturnsConfig cfg = trackingMemoryConfigBySourceProvider.apply(sourceKey);
        return cfg != null ? cfg : defaultDiminishingReturnsConfig();
    }

    /**
     * Whether this context registered a source-aware {@link Builder#trackingMemoryConfigProvider(Function)}.
     * Lets {@link dev.marie.framework.handler.SourceApplicationPipeline#process} pick out, among every
     * attached mod, the one that actually wants per-source control over the diminishing-returns curve
     * — rather than trusting {@link #get()}'s "last-registered wins" context, which silently drops a
     * source-aware provider the moment a second MariesLib-based mod attaches after it.
     */
    @ApiStatus.Internal
    public boolean hasSourceAwareMemoryConfigProvider() {
        return trackingMemoryConfigBySourceProvider != null;
    }

    @ApiStatus.Internal
    public BiFunction<ItemStack, Level, Map<String, Float>> sourceValueResolver() {
        return sourceValueResolver;
    }

    @ApiStatus.Internal
    public SourceDeltaResolver sourceDeltaResolver() {
        return sourceDeltaResolver;
    }

    @ApiStatus.Internal
    public BiConsumer<ServerPlayer, TrackingData> effectApplier() {
        return effectApplier;
    }

    @ApiStatus.Internal
    public Consumer<ServerPlayer> effectClearer() {
        return effectClearer;
    }

    @ApiStatus.Internal
    public int decayIntervalTicks() {
        return decayIntervalTicks.get();
    }

    @ApiStatus.Internal
    public float decayRateFor(String valueKey) {
        return decayRateResolver.apply(valueKey);
    }

    @ApiStatus.Internal
    public float criticalThresholdFor(String valueKey) {
        ValueDefinition def = ValueRegistry.get(valueKey);
        return def != null ? def.getCriticalThreshold() : criticalThreshold();
    }

    @ApiStatus.Internal
    public boolean showJoinMessage() {
        return showJoinMessage.get();
    }

    @ApiStatus.Internal
    public Component joinMessageLine1() {
        return joinMessageLine1.get();
    }

    @ApiStatus.Internal
    public Component joinMessageLine2() {
        return joinMessageLine2.get();
    }

    @ApiStatus.Internal
    public BiConsumer<ServerPlayer, TrackingData> trackingDeltaSyncer() {
        return trackingDeltaSyncer;
    }

    @ApiStatus.Internal
    public Consumer<ServerPlayer> syncOnJoin() {
        return syncOnJoin;
    }

    /**
     * Default death respawn policy when {@link #respawnValueHandler()} is not set.
     */
    @ApiStatus.Stable
    public Supplier<RespawnValueBehavior> respawnValueBehavior() {
        return respawnValueBehavior;
    }

    /**
     * @deprecated use {@link #respawnValueBehavior()}
     */
    @Deprecated
    @ApiStatus.Stable
    public Supplier<RespawnValueBehavior> deathNutritionBehavior() {
        return respawnValueBehavior();
    }

    /**
     * Optional override for death respawn tracking adjustments. When non-null, replaces
     * {@link #respawnValueBehavior()} entirely. The consumer should mutate {@code tracking}
     * in place; sync runs afterward via {@link #syncOnJoin()}.
     */
    @Nullable
    @ApiStatus.Stable
    public BiConsumer<ServerPlayer, TrackingData> respawnValueHandler() {
        return respawnValueHandler;
    }

    /**
     * @deprecated use {@link #respawnValueHandler()}
     */
    @Nullable
    @Deprecated
    @ApiStatus.Stable
    public BiConsumer<ServerPlayer, TrackingData> deathNutritionHandler() {
        return respawnValueHandler();
    }

    @ApiStatus.Experimental
    public Runnable cacheInvalidatedHook() {
        return cacheInvalidatedHook;
    }

    /**
     * The "reload happened, please re-register" hook. Fires after every resource reload
     * completes — both vanilla {@code /reload} and a mod's own reload command — strictly after
     * {@code MarieApiRegistries} has reset and re-frozen {@code TrackerRegistry} and
     * {@code ColorDefinitionRegistry} for that reload pass. Registrations made purely via Java at
     * mod init (e.g. {@link dev.marie.framework.tracking.tracker.MarieTracking#registerTracker},
     * {@link dev.marie.framework.color.MarieColors#registerColor}) are wiped by that reset with
     * nothing to restore them; consumers that want their trackers/colors to survive a
     * {@code /reload} should re-register them from this hook. Re-registration is safe to call
     * repeatedly — {@code TrackerRegistry}/{@code ColorDefinitionRegistry} upsert on duplicate keys
     * rather than throwing.
     *
     * @see dev.marie.framework.handler.ReloadGuardListener#reloadAndBroadcast
     */
    @ApiStatus.Experimental
    public Consumer<MinecraftServer> reloadBroadcastHook() {
        return reloadBroadcastHook;
    }

    @ApiStatus.Internal
    public float applyPostValueModifier(ValueModifierContext ctx, float amount) {
        return postValueModifierHook.apply(ctx, amount);
    }

    @Override
    @ApiStatus.Internal
    public boolean trackerSystemEnabled() {
        return trackerSystemEnabled.get();
    }

    @Override
    @ApiStatus.Internal
    public int trackerMaxRetention() {
        return trackerMaxRetention.get();
    }

    @Override
    @ApiStatus.Internal
    public int trackerWeeklyPeriodDays() {
        return trackerWeeklyPeriodDays.get();
    }

    @Override
    @ApiStatus.Internal
    public int trackerMonthlyPeriodDays() {
        return trackerMonthlyPeriodDays.get();
    }

    @Override
    @ApiStatus.Internal
    public int trackerSyncIntervalTicks() {
        return trackerSyncIntervalTicks.get();
    }

    @ApiStatus.Experimental
    public BiConsumer<ServerPlayer, dev.marie.framework.tracking.tracker.definition.TrackerHistoryEntry> onTrackerPeriodCompletedHook() {
        return onTrackerPeriodCompletedHook;
    }

    @Nullable
    @ApiStatus.Internal
    public MarieDataProvider dataProvider() {
        return dataProvider;
    }

    /**
     * @deprecated Use {@link dev.marie.framework.api.marieapi.MarieAPI#registerValue} and related
     *             {@code MarieAPI.register*} methods directly instead of supplying a delegate.
     */
    @Nullable
    @Deprecated
    @ApiStatus.Internal
    public MarieRegistrationDelegate registrationDelegate() {
        return registrationDelegate;
    }

    /**
     * Returns the ValueDefinition for the given key, or null if not registered.
     */
    @Nullable
    @ApiStatus.Stable
    public ValueDefinition valueDefinitionFor(String key) {
        return ValueRegistry.get(key);
    }

    @ApiStatus.Internal
    public static boolean isValueBeneficial(String valueKey) {
        ValueDefinition def = ValueRegistry.get(valueKey);
        return def == null || def.isBeneficial();
    }

    @ApiStatus.Stable
    public static Builder builder(String modId) {
        return new Builder(modId);
    }

    private static DiminishingReturnsConfig defaultDiminishingReturnsConfig() {
        return new DiminishingReturnsConfig(60L, 1.2, 3.0, 0.2, 0.5);
    }

    /**
     * Scoped to the value keys {@code modId} owns (unowned keys fall to the primary context, as in
     * {@link #forValue(String)}): {@code SourceApplicationPipeline} sums every attached mod's
     * resolver, so two mods both on this default must not each contribute every registered value.
     */
    private static Map<String, Float> defaultSourceValueResolver(String modId, ItemStack stack, Level level) {
        if (stack == null || stack.isEmpty()) {
            return Map.of();
        }
        ResourceLocation itemId = MarieRegistryUtils.itemKey(stack);
        if (itemId == null) {
            return Map.of();
        }
        Map<String, Float> result = new HashMap<>();
        for (ValueDefinition def : ValueRegistry.getAll()) {
            if (!modId.equals(forValue(def.getId()).modId())) {
                continue;
            }
            float score = SourceClassificationRegistry.getScore(itemId.toString(), def.getId());
            if (score != 0f) {
                result.put(def.getId(), score);
            }
        }
        return result.isEmpty() ? Map.of() : result;
    }

    private static Map<String, Float> defaultTooltipValueResolver(ItemStack stack, Player player) {
        if (stack == null || stack.isEmpty()) {
            return Map.of();
        }
        ResourceLocation itemId = MarieRegistryUtils.itemKey(stack);
        if (itemId == null) {
            return Map.of();
        }
        Map<String, Float> result = new HashMap<>();
        for (ValueDefinition def : ValueRegistry.getAll()) {
            float score = SourceClassificationRegistry.getScore(itemId.toString(), def.getId());
            if (score != 0f) {
                result.put(def.getId(), score);
            }
        }
        return result.isEmpty() ? Map.of() : result;
    }

    private static SourceDelta defaultSourceDeltaResolver(
            ItemStack stack,
            Level level,
            double payload,
            Map<String, Float> bars
    ) {
        if (bars == null || bars.isEmpty()) {
            return new SourceDelta(0f, Map.of());
        }
        Map<String, Float> deltas = new HashMap<>();
        float total = 0f;
        for (Map.Entry<String, Float> entry : bars.entrySet()) {
            String key = entry.getKey();
            float barWeight = entry.getValue();
            ValueDefinition def = ValueRegistry.get(key);
            double scale = def != null ? def.getAmountScale() : 1.0;
            float delta = (float) (payload * barWeight / scale);
            deltas.put(key, delta);
            total += delta;
        }
        return new SourceDelta(total, deltas);
    }

    public static final class Builder {
        private final String modId;
        private Supplier<Float> scannerConfidenceSpreadThreshold = () -> 0.15f;
        private Supplier<Float> compositeRatioThreshold = () -> 0.5f;
        private Supplier<Boolean> scannerEnableRecipeInheritance = () -> false;
        private Supplier<Boolean> enableDebugLogging = () -> false;
        private Supplier<Predicate<ItemStack>> sourceItemFilter = () -> stack -> false;
        private Supplier<Predicate<ItemStack>> sourceExclusionFilter = () -> stack -> false;
        private Supplier<Long> memoryWindowMinutes = () -> 60L;
        private Supplier<Integer> memoryWindowCount = () -> 20;
        private Supplier<Long> streakWindowMs = () -> 300_000L;
        private Supplier<Float> streakWeight = () -> 1.5f;
        private Supplier<Float> debtThreshold = () -> 5f;
        private Supplier<Float> debtDecayRate = () -> 0.01f;
        private Supplier<Float> diminishingSteepness = () -> 1.0f;
        private Supplier<Float> diminishingMidpoint = () -> 3.0f;
        private Supplier<Boolean> debugMemoryLogging = () -> false;
        private Supplier<Float> excessThreshold = () -> 0.9f;
        private Supplier<Float> lowThreshold = () -> 0.3f;
        private Supplier<Float> criticalThreshold = () -> 0.25f;
        private Runnable onFullTrackingDataSynced = () -> {};
        private Supplier<Object> configScreenFactory = () -> null;
        private Function<Object, Object> exportScreenFactory = parent -> null;
        private Function<Object, Object> importScreenFactory = parent -> null;
        private Consumer<Map<String, Float>> onValuesDeltaReceived = delta -> {};
        private Supplier<DiminishingReturnsConfig> clientMemoryConfigProvider = MarieContext::defaultDiminishingReturnsConfig;
        private Function<String, DiminishingReturnsConfig> clientMemoryConfigBySourceProvider = null;
        private Supplier<JsonObject> configExporter = MariesLibConfigBridge::buildExportRoot;
        private Consumer<JsonObject> configImporter = MariesLibConfigBridge::applyImport;
        private Supplier<PresetRegistry.PresetValues> currentConfigPresetValues = PresetRegistry.PresetValues::empty;
        private Runnable ensureBuiltInPresetsOnDisk = () -> {};
        private Consumer<PresetRegistry.PresetValues> applyPresetValues = values -> {};
        private Runnable enableAllEffectsForPresets = () -> {};
        private Function<String, String> valueIconProvider = key -> "minecraft:barrier";
        private BiFunction<ItemStack, Player, Map<String, Float>> tooltipValueResolver =
                MarieContext::defaultTooltipValueResolver;
        private Supplier<TrackingData> clientTrackingDataProvider = TrackingData::new;
        private Function<ResourceLocation, String> sourceFamilyResolver = id -> null;
        private Function<Item, Map<String, Float>> valueTagScoresProvider = item -> Map.of();
        private Function<String, String> tagRoleResolver = role -> null;
        private BiPredicate<ServerPlayer, ValueSourceTrigger> heavySourceBlocker =
                (player, trigger) -> false;
        private BiPredicate<ServerPlayer, ValueSourceTrigger> lightSourceBlocker =
                (player, trigger) -> false;
        private DoubleSupplier multiValueInheritanceThreshold = () -> 0.20;
        private ResolutionStageHandler[] runtimeResolverStages = new ResolutionStageHandler[0];
        private Supplier<DiminishingReturnsConfig> trackingMemoryConfigProvider = MarieContext::defaultDiminishingReturnsConfig;
        @Nullable
        private Function<String, DiminishingReturnsConfig> trackingMemoryConfigBySourceProvider = null;
        private BiFunction<ItemStack, Level, Map<String, Float>> sourceValueResolver;
        private SourceDeltaResolver sourceDeltaResolver = MarieContext::defaultSourceDeltaResolver;
        private BiConsumer<ServerPlayer, TrackingData> effectApplier = (p, d) -> {};
        private Consumer<ServerPlayer> effectClearer = p -> {};
        private Supplier<Integer> decayIntervalTicks = () -> 20;
        private Function<String, Float> decayRateResolver = valueKey -> {
            ValueDefinition def = ValueRegistry.get(valueKey);
            return def != null ? def.getDefaultDecayRate() : 0.001f;
        };
        private Supplier<Boolean> showJoinMessage = () -> false;
        private Supplier<Component> joinMessageLine1 = Component::empty;
        private Supplier<Component> joinMessageLine2 = Component::empty;
        private BiConsumer<ServerPlayer, TrackingData> trackingDeltaSyncer = (p, d) -> {};
        private Consumer<ServerPlayer> syncOnJoin = p -> {};
        private Supplier<RespawnValueBehavior> respawnValueBehavior = () -> RespawnValueBehavior.PRESERVE;
        @Nullable
        private BiConsumer<ServerPlayer, TrackingData> respawnValueHandler = null;
        @Nullable
        private MarieDataProvider dataProvider;
        @Nullable
        private MarieRegistrationDelegate registrationDelegate;
        private Runnable cacheInvalidatedHook = () -> {};
        private Consumer<MinecraftServer> reloadBroadcastHook = server -> {};
        private BiFunction<ValueModifierContext, Float, Float> postValueModifierHook = (ctx, amount) -> amount;
        private Supplier<Boolean> trackerSystemEnabled = () -> true;
        private Supplier<Integer> trackerMaxRetention = () -> 90;
        private Supplier<Integer> trackerWeeklyPeriodDays = () -> 7;
        private Supplier<Integer> trackerMonthlyPeriodDays = () -> 30;
        private Supplier<Integer> trackerSyncIntervalTicks = () -> 20;
        private BiConsumer<ServerPlayer, dev.marie.framework.tracking.tracker.definition.TrackerHistoryEntry> onTrackerPeriodCompletedHook = (p, e) -> {};

        private Builder(String modId) {
            this.modId = modId;
            this.sourceValueResolver = (stack, level) -> defaultSourceValueResolver(modId, stack, level);
        }

        public Builder scannerConfidenceSpreadThreshold(Supplier<Float> s) { this.scannerConfidenceSpreadThreshold = s; return this; }
        public Builder compositeRatioThreshold(Supplier<Float> s) { this.compositeRatioThreshold = s; return this; }
        public Builder scannerEnableRecipeInheritance(Supplier<Boolean> s) { this.scannerEnableRecipeInheritance = s; return this; }
        public Builder enableDebugLogging(Supplier<Boolean> s) { this.enableDebugLogging = s; return this; }
        /**
         * Restricts which items this mod will offer up as value sources (to
         * {@link dev.marie.framework.scanner.ItemScanner} and
         * {@link dev.marie.framework.runtime.RuntimeResolver}). Defaults to rejecting everything — a
         * mod that has no concept of "source items" (e.g. one only using Marieslib for its
         * config-screen plumbing) should leave this unset rather than accept everything, since
         * {@link #isSourceItemAllowed} ORs every attached mod's filter together and an unset
         * accept-all filter would silently make every item scannable for every other mod too.
         */
        @ApiStatus.Experimental
        public Builder sourceItemFilter(Supplier<Predicate<ItemStack>> s) { this.sourceItemFilter = s; return this; }
        /**
         * A predicate for "is {@code stack} excluded from my own value classification" — e.g. a
         * player-authored exclusion toggle a mod builds on top of MariesLib (Nourished's "Excluded
         * from classification" item-editor page is the first consumer). Checked across every
         * attached mod by {@link #isSourceExcludedByAnyMod} wherever a generic, mod-agnostic
         * mechanism (like a {@code SourceClassificationRegistry} override) would otherwise bypass
         * per-mod exclusion entirely. Leave unset (rejects nothing) for a mod with no exclusion
         * concept of its own, the same reasoning {@link #sourceItemFilter} documents for its own
         * unset default.
         */
        @ApiStatus.Experimental
        public Builder sourceExclusionFilter(Supplier<Predicate<ItemStack>> s) { this.sourceExclusionFilter = s; return this; }
        public Builder memoryWindowMinutes(Supplier<Long> s) { this.memoryWindowMinutes = s; return this; }
        public Builder memoryWindowCount(Supplier<Integer> s) { this.memoryWindowCount = s; return this; }
        public Builder streakWindowMs(Supplier<Long> s) { this.streakWindowMs = s; return this; }
        public Builder streakWeight(Supplier<Float> s) { this.streakWeight = s; return this; }
        public Builder debtThreshold(Supplier<Float> s) { this.debtThreshold = s; return this; }
        public Builder debtDecayRate(Supplier<Float> s) { this.debtDecayRate = s; return this; }
        public Builder diminishingSteepness(Supplier<Float> s) { this.diminishingSteepness = s; return this; }
        public Builder diminishingMidpoint(Supplier<Float> s) { this.diminishingMidpoint = s; return this; }
        public Builder debugMemoryLogging(Supplier<Boolean> s) { this.debugMemoryLogging = s; return this; }
        public Builder excessThreshold(Supplier<Float> s) { this.excessThreshold = s; return this; }
        public Builder lowThreshold(Supplier<Float> s) { this.lowThreshold = s; return this; }
        public Builder criticalThreshold(Supplier<Float> s) { this.criticalThreshold = s; return this; }
        @ApiStatus.Experimental
        public Builder onFullTrackingDataSynced(Runnable r) { this.onFullTrackingDataSynced = r; return this; }
        public Builder configScreenFactory(Supplier<Object> s) { this.configScreenFactory = s; return this; }
        public Builder exportScreenFactory(Function<Object, Object> f) { this.exportScreenFactory = f; return this; }
        public Builder importScreenFactory(Function<Object, Object> f) { this.importScreenFactory = f; return this; }
        @ApiStatus.Experimental
        public Builder onValuesDeltaReceived(Consumer<Map<String, Float>> c) { this.onValuesDeltaReceived = c; return this; }
        public Builder clientMemoryConfigProvider(Supplier<DiminishingReturnsConfig> s) { this.clientMemoryConfigProvider = s; return this; }
        /**
         * Source-aware overload of {@link #clientMemoryConfigProvider(Supplier)}: {@code f} is
         * invoked with the hovered item's id so tooltip rendering ({@code MarieTooltipHelper}) can
         * show a per-item exemption in its "Diminished (N%)" preview the same way the server-side
         * application already honors it, instead of always using one curve for every item.
         */
        @ApiStatus.Experimental
        public Builder clientMemoryConfigProvider(Function<String, DiminishingReturnsConfig> f) { this.clientMemoryConfigBySourceProvider = f; return this; }
        public Builder configExporter(Supplier<JsonObject> s) { this.configExporter = s; return this; }
        public Builder configImporter(Consumer<JsonObject> c) { this.configImporter = c; return this; }
        public Builder currentConfigPresetValues(Supplier<PresetRegistry.PresetValues> s) { this.currentConfigPresetValues = s; return this; }
        public Builder ensureBuiltInPresetsOnDisk(Runnable r) { this.ensureBuiltInPresetsOnDisk = r; return this; }
        public Builder applyPresetValues(Consumer<PresetRegistry.PresetValues> c) { this.applyPresetValues = c; return this; }
        public Builder enableAllEffectsForPresets(Runnable r) { this.enableAllEffectsForPresets = r; return this; }
        public Builder valueIconProvider(Function<String, String> f) { this.valueIconProvider = f; return this; }
        @ApiStatus.Experimental
        public Builder tooltipValueResolver(BiFunction<ItemStack, Player, Map<String, Float>> f) { this.tooltipValueResolver = f; return this; }
        @ApiStatus.Experimental
        public Builder clientTrackingDataProvider(Supplier<TrackingData> s) { this.clientTrackingDataProvider = s; return this; }
        public Builder sourceFamilyResolver(Function<ResourceLocation, String> f) { this.sourceFamilyResolver = f; return this; }
        @ApiStatus.Experimental
        public Builder valueTagScoresProvider(Function<Item, Map<String, Float>> f) { this.valueTagScoresProvider = f; return this; }
        public Builder tagRoleResolver(Function<String, String> f) { this.tagRoleResolver = f; return this; }
        public Builder heavySourceBlocker(BiPredicate<ServerPlayer, ValueSourceTrigger> p) {
            this.heavySourceBlocker = p;
            return this;
        }
        public Builder lightSourceBlocker(BiPredicate<ServerPlayer, ValueSourceTrigger> p) {
            this.lightSourceBlocker = p;
            return this;
        }
        public Builder multiValueInheritanceThreshold(DoubleSupplier s) { this.multiValueInheritanceThreshold = s; return this; }
        @ApiStatus.Experimental
        public Builder runtimeResolverStages(ResolutionStageHandler[] stages) { this.runtimeResolverStages = stages; return this; }
        public Builder trackingMemoryConfigProvider(Supplier<DiminishingReturnsConfig> s) { this.trackingMemoryConfigProvider = s; return this; }
        /**
         * Source-aware overload of {@link #trackingMemoryConfigProvider(Supplier)}: {@code f} is
         * invoked with the triggering source's {@code sourceId()} (may be null) so a mod can return
         * a different curve for specific items (e.g. one flagged to bypass diminishing returns)
         * instead of one curve for every source. Setting this takes priority over the no-arg
         * overload at lookup time ({@link MarieContext#trackingMemoryConfig(String)}).
         */
        @ApiStatus.Experimental
        public Builder trackingMemoryConfigProvider(Function<String, DiminishingReturnsConfig> f) { this.trackingMemoryConfigBySourceProvider = f; return this; }
        @ApiStatus.Experimental
        public Builder sourceValueResolver(BiFunction<ItemStack, Level, Map<String, Float>> f) { this.sourceValueResolver = f; return this; }
        @ApiStatus.Experimental
        public Builder sourceDeltaResolver(SourceDeltaResolver r) { this.sourceDeltaResolver = r; return this; }
        @ApiStatus.Experimental
        public Builder effectApplier(BiConsumer<ServerPlayer, TrackingData> c) { this.effectApplier = c; return this; }
        @ApiStatus.Experimental
        public Builder effectClearer(Consumer<ServerPlayer> c) { this.effectClearer = c; return this; }
        public Builder decayIntervalTicks(Supplier<Integer> s) { this.decayIntervalTicks = s; return this; }
        public Builder decayRateFor(Function<String, Float> resolver) { this.decayRateResolver = resolver; return this; }
        @ApiStatus.Experimental
        public Builder showJoinMessage(Supplier<Boolean> s) { this.showJoinMessage = s; return this; }
        @ApiStatus.Experimental
        public Builder joinMessageLine1(Supplier<Component> s) { this.joinMessageLine1 = s; return this; }
        @ApiStatus.Experimental
        public Builder joinMessageLine2(Supplier<Component> s) { this.joinMessageLine2 = s; return this; }
        @ApiStatus.Experimental
        public Builder trackingDeltaSyncer(BiConsumer<ServerPlayer, TrackingData> c) { this.trackingDeltaSyncer = c; return this; }
        @ApiStatus.Experimental
        public Builder syncOnJoin(Consumer<ServerPlayer> c) { this.syncOnJoin = c; return this; }
        /**
         * Death respawn policy when {@link #respawnValueHandler(BiConsumer)} is not set.
         * Default: {@link RespawnValueBehavior#PRESERVE}.
         */
        @ApiStatus.Stable
        public Builder respawnValueBehavior(Supplier<RespawnValueBehavior> s) {
            this.respawnValueBehavior = s != null ? s : () -> RespawnValueBehavior.PRESERVE;
            return this;
        }
        /**
         * @deprecated use {@link #respawnValueBehavior(Supplier)}
         */
        @Deprecated
        @ApiStatus.Stable
        public Builder deathNutritionBehavior(Supplier<RespawnValueBehavior> s) {
            return respawnValueBehavior(s);
        }
        /**
         * Fully replaces {@link #respawnValueBehavior(Supplier)} when set. Use for mod-specific
         * death handling (e.g. resetting auxiliary attachments) before tracking sync on respawn.
         */
        @ApiStatus.Stable
        public Builder respawnValueHandler(@Nullable BiConsumer<ServerPlayer, TrackingData> handler) {
            this.respawnValueHandler = handler;
            return this;
        }
        /**
         * @deprecated use {@link #respawnValueHandler(BiConsumer)}
         */
        @Deprecated
        @ApiStatus.Stable
        public Builder deathNutritionHandler(@Nullable BiConsumer<ServerPlayer, TrackingData> handler) {
            return respawnValueHandler(handler);
        }
        @ApiStatus.Experimental
        public Builder onCacheInvalidated(Runnable hook) {
            this.cacheInvalidatedHook = hook != null ? hook : () -> {};
            return this;
        }
        /**
         * Sets the "reload happened, please re-register" hook. See
         * {@link MarieContext#reloadBroadcastHook()} for when this fires and what to do with it.
         */
        @ApiStatus.Experimental
        public Builder onReloadBroadcast(Consumer<MinecraftServer> hook) {
            this.reloadBroadcastHook = hook != null ? hook : server -> {};
            return this;
        }
        @ApiStatus.Experimental
        public Builder postValueModifierHook(BiFunction<ValueModifierContext, Float, Float> hook) {
            this.postValueModifierHook = hook != null ? hook : (ctx, amount) -> amount;
            return this;
        }
        @ApiStatus.Experimental
        public Builder trackerSystemEnabled(Supplier<Boolean> s) { this.trackerSystemEnabled = s; return this; }
        @ApiStatus.Experimental
        public Builder trackerMaxRetention(Supplier<Integer> s) { this.trackerMaxRetention = s; return this; }
        @ApiStatus.Experimental
        public Builder trackerWeeklyPeriodDays(Supplier<Integer> s) { this.trackerWeeklyPeriodDays = s; return this; }
        @ApiStatus.Experimental
        public Builder trackerMonthlyPeriodDays(Supplier<Integer> s) { this.trackerMonthlyPeriodDays = s; return this; }
        @ApiStatus.Experimental
        public Builder trackerSyncIntervalTicks(Supplier<Integer> s) { this.trackerSyncIntervalTicks = s; return this; }
        @ApiStatus.Experimental
        public Builder onTrackerPeriodCompleted(
                BiConsumer<ServerPlayer, dev.marie.framework.tracking.tracker.definition.TrackerHistoryEntry> hook) {
            this.onTrackerPeriodCompletedHook = hook != null ? hook : (p, e) -> {};
            return this;
        }
        @ApiStatus.Stable
        public Builder dataProvider(MarieDataProvider p) { this.dataProvider = p; return this; }
        /**
         * @deprecated Use {@link dev.marie.framework.api.marieapi.MarieAPI#registerValue} and related
         *             {@code MarieAPI.register*} methods directly instead of supplying a delegate.
         */
        @Deprecated
        @ApiStatus.Internal
        public Builder registrationDelegate(MarieRegistrationDelegate d) { this.registrationDelegate = d; return this; }

        @ApiStatus.Stable
        public MarieContext build() {
            if (!MarieValidation.sanitizeModId(modId)) {
                throw new IllegalArgumentException(
                        "modId must match [a-z0-9_]{1,64}, got: '" + modId + "'");
            }
            return new MarieContext(this);
        }
    }
}
