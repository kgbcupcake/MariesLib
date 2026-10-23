package dev.marie.framework.handler;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.api.marie.MarieEvents;
import dev.marie.framework.api.source.SourcePairSynergy;
import dev.marie.framework.api.effects.SynergyDefinition;
import dev.marie.framework.api.value.ValueDefinition;
import dev.marie.framework.api.value.ValueModifierContext;
import dev.marie.framework.api.value.ValueModifierEvent;
import dev.marie.framework.api.value.ValueSourceTrigger;
import dev.marie.framework.api.registry.SynergyRegistry;
import dev.marie.framework.tracking.TrackingDataApplicationHistoryView;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import dev.marie.framework.config.FeatureFlagCache;
import dev.marie.framework.core.IMarieConfig;
import dev.marie.framework.core.MarieContext;
import dev.marie.framework.core.MarieCore;
import dev.marie.framework.core.MarieModRegistry;
import dev.marie.framework.runtime.SourceClassificationRegistry;
import dev.marie.framework.runtime.SourceTriggerRegistry;
import dev.marie.framework.tracking.MilestoneTracker;
import dev.marie.framework.tracking.SynergyBuffTracker;
import dev.marie.framework.tracking.TrackingAttachment;
import dev.marie.framework.tracking.TrackingData;
import dev.marie.framework.registry.MarieAttributes;
import dev.marie.framework.core.KubeIntegration;
import dev.marie.framework.util.MarieRegistryUtils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;

@ApiStatus.Internal
public final class SourceApplicationPipeline {

    private static final java.util.concurrent.atomic.AtomicBoolean THRESHOLD_WARN_ONCE =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final java.util.concurrent.atomic.AtomicBoolean WARN_ONCE_SOURCE_APPLIED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private SourceApplicationPipeline() {}

    /**
     * Removes all tracked synergy state for the given player, e.g. on logout.
     *
     * @param playerId the player's UUID
     */
    public static void clearPlayer(UUID playerId) {
        SynergyStateRegistry.clearPlayer(playerId);
    }

    public static void process(ServerPlayer player, ValueSourceTrigger trigger, ItemStack stack,
                             TrackingData tracking, long gameTimeMs) {
        if (ReloadGuardListener.isReloadInProgress()) {
            return;
        }

        String sourceKey = trigger.sourceId();
        DiminishingReturnsConfigOrNull config = resolveMemoryConfig(sourceKey);
        tracking.setMemoryConfig(config.config());

        MarieEvents.SourceTriggerEvent triggerEvent =
                new MarieEvents.SourceTriggerEvent(player, trigger);
        NeoForge.EVENT_BUS.post(triggerEvent);
        if (triggerEvent.isCanceled()) {
            return;
        }

        if (FeatureFlagCache.enableBlockHeavySources()
                && anyModBlocks(ctx -> ctx.isHeavySourceBlocked(player, trigger))) {
            return;
        }
        if (FeatureFlagCache.enableBlockLightSource()
                && anyModBlocks(ctx -> ctx.isLightSourceBlocked(player, trigger))) {
            return;
        }
        boolean debugApplyLog = FeatureFlagCache.enableDebugLogging();
        Map<String, Float> valuesBefore = debugApplyLog ? snapshotValues(tracking) : Map.of();

        SourceClassificationRegistry.SourceClassification override =
                SourceClassificationRegistry.getOverride(sourceKey).orElse(null);
        // The generic, mod-agnostic SourceClassificationRegistry override (the item editor's own
        // Values sliders) otherwise always wins outright below, bypassing every attached mod's own
        // per-mod exclusion concept entirely — a player excluding an item through, say, Nourished's
        // own exclusion toggle had no effect on eating it if *any* mod's item editor had ever saved
        // an override for that same item, since this override has no notion of which mod's exclusion
        // should apply to it. If any attached mod now considers this item excluded, the override is
        // set aside here so the pipeline falls through to the normal per-mod resolver merge below,
        // which already correctly yields zero/empty for whichever mod's own exclusion applies.
        if (override != null && stack != null && !stack.isEmpty() && MarieContext.isSourceExcludedByAnyMod(stack)) {
            override = null;
        }

        float totalAdded;
        Map<String, Float> valueDeltas;
        Map<String, Float> matchedBars;
        ResourceLocation sourceResourceId;
        if (stack != null && !stack.isEmpty()) {
            sourceResourceId = MarieRegistryUtils.itemKey(stack);
        } else {
            try {
                sourceResourceId = ResourceLocation.parse(trigger.sourceId());
            } catch (Exception e) {
                MarieCore.LOGGER.warn("[MarieLib] Invalid sourceId in trigger: '{}' — skipping pipeline",
                        trigger.sourceId());
                return;
            }
        }
        // Multiple mods may each own their own subset of value keys and independently classify the
        // same source item/trigger, so every attached mod's resolver runs and their results merge —
        // rather than only the resolver of whichever mod last called MarieBootstrap.attach.
        Map<String, Float> resolverMatchedBars = new LinkedHashMap<>();
        Map<String, Float> resolverDeltaValues = new HashMap<>();
        float resolverDeltaTotal = 0f;
        if (stack == null || stack.isEmpty()) {
            for (MarieContext modCtx : MarieModRegistry.getAll()) {
                MarieContext.SourceDelta delta = modCtx.sourceDeltaResolver().resolve(
                        stack, player.level(), trigger.payload(), Map.of());
                resolverDeltaTotal += delta.total();
                delta.values().forEach((k, v) -> resolverDeltaValues.merge(k, v, Float::sum));
            }
        } else {
            for (MarieContext modCtx : MarieModRegistry.getAll()) {
                Map<String, Float> bars = modCtx.sourceValueResolver().apply(stack, player.level());
                resolverMatchedBars.putAll(bars);
                MarieContext.SourceDelta delta = modCtx.sourceDeltaResolver().resolve(
                        stack, player.level(), trigger.payload(), new LinkedHashMap<>(bars));
                resolverDeltaTotal += delta.total();
                delta.values().forEach((k, v) -> resolverDeltaValues.merge(k, v, Float::sum));
            }
        }
        MarieContext.SourceDelta resolverDelta = new MarieContext.SourceDelta(resolverDeltaTotal, resolverDeltaValues);

        if (override != null) {
            valueDeltas = new HashMap<>(override.values());
            for (Map.Entry<String, Float> e : resolverDelta.values().entrySet()) {
                valueDeltas.putIfAbsent(e.getKey(), e.getValue());
            }
            matchedBars = new HashMap<>(override.values());
            for (Map.Entry<String, Float> e : resolverMatchedBars.entrySet()) {
                matchedBars.putIfAbsent(e.getKey(), e.getValue());
            }
            // total and per-value deltas are different units; don't conflate them.
            // Being source-classified must not zero an item's calories: prefer the item's
            // existing/resolved calorie value (vanilla default, or a food_overrides.json entry the
            // consumer routes through resolverDelta) and only fall back to the classification
            // entry's own explicit calories/total when the resolver yields nothing.
            totalAdded = resolverDelta.total() != 0f ? resolverDelta.total() : override.total();
            MarieCore.LOGGER.debug("[MarieLib] using override for {} (total={}, values={})",
                    sourceKey, totalAdded, valueDeltas);
        } else {
            matchedBars = resolverMatchedBars;
            totalAdded = resolverDelta.total();
            valueDeltas = new HashMap<>(resolverDelta.values());
        }

        Map<String, Float> matchedBarWeights = new LinkedHashMap<>(matchedBars);

        java.util.List<SourceTriggerRegistry.Entry> triggerEntries =
                SourceTriggerRegistry.getEntries(trigger.type(), trigger.sourceId());
        for (SourceTriggerRegistry.Entry entry : triggerEntries) {
            valueDeltas.merge(entry.valueKey(), entry.amount(), Float::sum);
        }

        boolean hasRegisteredSource = override != null || totalAdded != 0f || !valueDeltas.isEmpty();
        if (!hasRegisteredSource) {
            if (FeatureFlagCache.enableDebugLogging()) {
                MarieCore.LOGGER.debug(
                        "[MarieLib] No registered source for '{}' — skipping pipeline, recent-source memory left untouched",
                        sourceKey);
            }
            return;
        }

        String dominantCategory = matchedBars.isEmpty()
                ? null
                : matchedBars.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(null);
        String familyKey = null;
        for (MarieContext modCtx : MarieModRegistry.getAll()) {
            familyKey = modCtx.sourceFamilyResolver().apply(sourceResourceId);
            if (familyKey != null) {
                break;
            }
        }

        float multiplier = tracking.recordSource(sourceKey, dominantCategory, familyKey, gameTimeMs);
        TrackingData.MultiplierBreakdown multiplierBreakdown = debugApplyLog
                ? tracking.getMultiplierBreakdown(sourceKey, dominantCategory, familyKey, gameTimeMs)
                : null;

        if (FeatureFlagCache.enableTotalTracking()) {
            // total is added unscaled; multiplier only diminishes per-value gains, not the aggregate total
            MarieCore.LOGGER.debug("[MarieLib] total: adding {} for {} (multiplier {} not applied to total)",
                    totalAdded,
                    stack != null ? stack.getItem().getDescriptionId() : trigger.sourceId(),
                    multiplier);
            tracking.addTotal(totalAdded);
        }

        Map<String, Float> afterMultiplierOnly = new HashMap<>();
        Map<String, Float> finalApplied = new HashMap<>();

        for (String key : MarieContext.get().valueKeys()) {
            float valueDelta = valueDeltas.getOrDefault(key, 0f);
            MarieContext keyCtx = MarieContext.forValue(key);
            ValueDefinition valueDef = keyCtx.valueDefinitionFor(key);
            if (valueDef != null && valueDef.getAmountScale() != 1.0) {
                valueDelta = (float) (valueDelta / valueDef.getAmountScale());
            }
            if (valueDelta != 0f) {
                float adjustedDelta = valueDelta * multiplier;
                afterMultiplierOnly.put(key, adjustedDelta);
                adjustedDelta = ValueAbsorptionAdjuster.applySeasonalAbsorption(player, key, adjustedDelta);
                adjustedDelta = ValueAbsorptionAdjuster.applyAbsorptionModifiers(player, key, adjustedDelta);
                adjustedDelta *= MarieAttributes.valueRegenMultiplier(player);
                ValueModifierContext modifierCtx =
                        ValueModifierContext.of(player, sourceResourceId, key);
                adjustedDelta = KubeIntegration.applyValueDeltaModifier(modifierCtx, adjustedDelta);

                ValueModifierEvent modifierEvent = new ValueModifierEvent(modifierCtx, adjustedDelta);
                NeoForge.EVENT_BUS.post(modifierEvent);

                if (modifierEvent.isCanceled()) {
                    continue;
                }

                float finalDelta = modifierEvent.getAmount();
                finalDelta = keyCtx.applyPostValueModifier(modifierCtx, finalDelta);
                if (!Float.isFinite(finalDelta)) {
                    MarieCore.LOGGER.warn("[MarieLib] non-finite finalDelta {} for player={} source={} value={} — skipping",
                            finalDelta, player.getName().getString(), sourceKey, key);
                    continue;
                }
                if (finalDelta == 0f) {
                    continue;
                }
                finalApplied.put(key, finalDelta);
                float oldValue = tracking.values.getOrDefault(key, 0f);
                tracking.addValue(key, finalDelta);
                float newValue = tracking.values.getOrDefault(key, 0f);

                if (oldValue != newValue) {
                    NeoForge.EVENT_BUS.post(new MarieEvents.ValueChangedEvent(
                            player, key, oldValue, newValue));
                }

                NeoForge.EVENT_BUS.post(new MarieEvents.SourceAppliedEvent(
                        player, sourceResourceId, key, finalDelta));
                MilestoneTracker.onValueApplied(player, key, finalDelta);
            }
        }

        if (FeatureFlagCache.enableSynergies()) {
            var synergies = SynergyRegistry.getSourcePairSynergies();
            if (!synergies.isEmpty()) {
                long currentGameTick = player.level().getGameTime();
                TrackingDataApplicationHistoryView historyView = new TrackingDataApplicationHistoryView(tracking);
                for (SourcePairSynergy synergy : synergies) {
                    ResourceLocation otherSourceId;
                    if (sourceResourceId.equals(synergy.getSourceA())) {
                        otherSourceId = synergy.getSourceB();
                    } else if (sourceResourceId.equals(synergy.getSourceB())) {
                        otherSourceId = synergy.getSourceA();
                    } else {
                        continue;
                    }
                    long elapsed = historyView.getTimeSinceSource(otherSourceId);
                    if (elapsed == -1L || elapsed > synergy.getTimeWindowTicks()) {
                        continue;
                    }
                    Long lastFiredTick = SynergyStateRegistry.getLastFired(player.getUUID(), synergy.getId());
                    if (lastFiredTick != null && currentGameTick - lastFiredTick < synergy.getTimeWindowTicks()) {
                        continue;
                    }
                    String bonusKey = synergy.getBonusValueKey();
                    if (!tracking.values.containsKey(bonusKey)) {
                        continue;
                    }
                    float bonusAmount = synergy.getBonusAmount();
                    ValueModifierContext bonusCtx = ValueModifierContext.of(player, sourceResourceId, bonusKey);
                    ValueModifierEvent bonusEvent = new ValueModifierEvent(bonusCtx, bonusAmount);
                    NeoForge.EVENT_BUS.post(bonusEvent);
                    if (bonusEvent.isCanceled()) {
                        continue;
                    }
                    float finalBonus = bonusEvent.getAmount();
                    finalBonus = MarieContext.forValue(bonusKey).applyPostValueModifier(bonusCtx, finalBonus);
                    if (!Float.isFinite(finalBonus) || finalBonus == 0f) {
                        continue;
                    }
                    float oldBonusValue = tracking.values.getOrDefault(bonusKey, 0f);
                    tracking.addValue(bonusKey, finalBonus);
                    float newBonusValue = tracking.values.getOrDefault(bonusKey, 0f);
                    if (oldBonusValue != newBonusValue) {
                        NeoForge.EVENT_BUS.post(new MarieEvents.ValueChangedEvent(
                                player, bonusKey, oldBonusValue, newBonusValue));
                    }
                    NeoForge.EVENT_BUS.post(new MarieEvents.SourceAppliedEvent(
                            player, sourceResourceId, bonusKey, finalBonus));
                    MilestoneTracker.onValueApplied(player, bonusKey, finalBonus);
                    SynergyStateRegistry.recordFired(player.getUUID(), synergy.getId(), currentGameTick);

                    if (synergy.getValueModifier() != 1.0f && synergy.getModifierDurationTicks() > 0) {
                        SynergyBuffTracker.activate(player.getUUID(), synergy.getBonusValueKey(),
                                synergy.getValueModifier(), currentGameTick + synergy.getModifierDurationTicks());
                    }
                }
            }
        }

        if (FeatureFlagCache.enableSynergies()) {
            var valueSynergies = SynergyRegistry.getValueSynergies();
            if (!valueSynergies.isEmpty()) {
                for (SynergyDefinition synergy : valueSynergies) {
                    String keyA = synergy.getValueKeyA();
                    String keyB = synergy.getValueKeyB();
                    ValueDefinition defA = MarieContext.get().valueDefinitionFor(keyA);
                    ValueDefinition defB = MarieContext.get().valueDefinitionFor(keyB);
                    if (defA == null || defB == null) {
                        continue;
                    }
                    float levelA = tracking.values.getOrDefault(keyA, 0f);
                    float levelB = tracking.values.getOrDefault(keyB, 0f);
                    boolean currentlyActive = SynergyStateRegistry.meetsSynergyCondition(levelA, defA, synergy.getConditionA())
                            && SynergyStateRegistry.meetsSynergyCondition(levelB, defB, synergy.getConditionB());
                    boolean wasActive = SynergyStateRegistry.isActive(player.getUUID(), synergy.getId());
                    if (currentlyActive && !wasActive) {
                        SynergyStateRegistry.setActive(player.getUUID(), synergy.getId());
                        ResourceLocation effectId = synergy.getBonusEffectId();
                        if (effectId != null) {
                            BuiltInRegistries.MOB_EFFECT.getHolder(effectId).ifPresentOrElse(
                                    holder -> player.addEffect(new MobEffectInstance(
                                            holder,
                                            synergy.getEffectDuration(),
                                            synergy.getEffectAmplifier())),
                                    () -> MarieCore.LOGGER.warn(
                                            "[MarieLib] ValueSynergy '{}' references unknown effect '{}'",
                                            synergy.getId(), effectId));
                        }
                    } else if (!currentlyActive && wasActive) {
                        SynergyStateRegistry.clearActive(player.getUUID(), synergy.getId());
                    }
                }
            }
        }

        if (debugApplyLog) {
            Map<String, Float> valuesAfter = snapshotValues(tracking);
            SourceApplyDebugReporter.submitSourceApplyDebug(
                    player,
                    stack,
                    gameTimeMs,
                    sourceKey,
                    sourceResourceId,
                    override != null,
                    matchedBarWeights,
                    valueDeltas,
                    afterMultiplierOnly,
                    finalApplied,
                    valuesBefore,
                    valuesAfter,
                    multiplier,
                    multiplierBreakdown
            );
        }

        ThresholdCrossingEvaluator.checkThresholdCrossings(player, tracking);

        TrackingAttachment.setData(player, tracking);
        // Every attached mod owns a subset of `tracking`'s values, so every mod's sync/effect hook
        // runs against the shared blob rather than only the last-attached mod's.
        MarieModRegistry.forEach(modCtx -> {
            modCtx.trackingDeltaSyncer().accept(player, tracking);
            modCtx.effectApplier().accept(player, tracking);
        });

        MarieCore.LOGGER.debug("{} applied {} -> {}",
                player.getName().getString(),
                stack != null ? stack.getItem().getDescriptionId() : trigger.sourceId(),
                tracking);
    }

    /**
     * Applies an absolute value write (commands, KubeJS force-set, etc.).
     * Fires {@link MarieEvents.ValueChangedEvent} and threshold crossings when the level changes.
     *
     * @return {@code true} if the stored level changed
     */
    @ApiStatus.Internal
    public static boolean writeDirectValue(
            ServerPlayer player, TrackingData tracking, String key, float newValue) {
        if (ReloadGuardListener.isReloadInProgress()) {
            return false;
        }
        if (!tracking.values.containsKey(key)) {
            return false;
        }
        float oldValue = tracking.values.getOrDefault(key, 0f);
        float clamped = Mth.clamp(newValue, 0f, 1f);
        if (Float.isNaN(clamped)) {
            clamped = 0f;
        }
        if (oldValue == clamped) {
            return false;
        }
        tracking.lastValues.put(key, oldValue);
        tracking.values.put(key, clamped);
        NeoForge.EVENT_BUS.post(new MarieEvents.ValueChangedEvent(player, key, oldValue, clamped));
        ThresholdCrossingEvaluator.checkThresholdCrossings(player, tracking);
        tracking.lastValues.put(key, clamped);
        return true;
    }

    /**
     * Applies a direct delta write ({@link dev.marie.framework.api.marieapi.MarieAPI#modifyValue}).
     *
     * @return {@code true} if the stored level changed
     */
    @ApiStatus.Internal
    public static boolean applyDirectDelta(
            ServerPlayer player, TrackingData tracking, String key, float delta) {
        if (!tracking.values.containsKey(key)) {
            return false;
        }
        float oldValue = tracking.values.get(key);
        return writeDirectValue(player, tracking, key, Mth.clamp(oldValue + delta, 0f, 1f));
    }

    @ApiStatus.Internal
    public static void finalizeDirectWrite(ServerPlayer player, TrackingData tracking) {
        TrackingAttachment.setData(player, tracking);
        if (!MarieContext.isRegistered()) {
            return;
        }
        MarieModRegistry.forEach(modCtx -> {
            modCtx.trackingDeltaSyncer().accept(player, tracking);
            modCtx.effectApplier().accept(player, tracking);
        });
    }

    /**
     * True if any attached mod's blocking predicate returns true for the current trigger — a
     * single mod objecting is enough to veto the shared trigger.
     */
    private static boolean anyModBlocks(java.util.function.Predicate<MarieContext> blocker) {
        for (MarieContext modCtx : MarieModRegistry.getAll()) {
            if (blocker.test(modCtx)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@link MarieContext#get()} returns whichever mod registered last — fine for the common
     * single-mod case, but when a second MariesLib-based mod attaches afterward it silently steals
     * that slot, and a source-aware {@code trackingMemoryConfigProvider} an earlier mod registered
     * (e.g. for a per-item diminishing-returns exemption) would never be consulted again even though
     * that mod is still fully attached. Scanning every attached mod for the one that actually
     * registered a source-aware provider — instead of trusting {@code get()}'s "last wins" context —
     * keeps this resolving correctly regardless of attach order or how many mods share MariesLib.
     */
    private static DiminishingReturnsConfigOrNull resolveMemoryConfig(String sourceKey) {
        for (MarieContext modCtx : MarieModRegistry.getAll()) {
            if (modCtx.hasSourceAwareMemoryConfigProvider()) {
                return new DiminishingReturnsConfigOrNull(modCtx.trackingMemoryConfig(sourceKey), false);
            }
        }
        return new DiminishingReturnsConfigOrNull(IMarieConfig.get().trackingMemoryConfig(sourceKey), false);
    }

    private static Map<String, Float> snapshotValues(TrackingData tracking) {
        Map<String, Float> m = new HashMap<>();
        for (String key : MarieContext.get().valueKeys()) {
            m.put(key, tracking.values.getOrDefault(key, 0f));
        }
        return m;
    }

    static void resetSnapshotWarnings() {
        WARN_ONCE_SOURCE_APPLIED.set(false);
        THRESHOLD_WARN_ONCE.set(false);
    }

    private record DiminishingReturnsConfigOrNull(
            dev.marie.framework.tracking.DiminishingReturnsConfig config,
            boolean wasNullProvider
    ) {}
}
