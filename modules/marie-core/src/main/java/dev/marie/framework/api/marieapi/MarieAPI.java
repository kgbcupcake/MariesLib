package dev.marie.framework.api.marieapi;

import dev.marie.framework.api.effects.AbsorptionModifier;
import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.api.ConfigValidator;
import dev.marie.framework.api.effects.SleepBonusEvaluator;
import dev.marie.framework.api.effects.SynergyDefinition;
import dev.marie.framework.api.effects.ThresholdEffect;
import dev.marie.framework.api.hover.BlockHoverProvider;
import dev.marie.framework.api.marie.MariePlayerData;
import dev.marie.framework.api.marie.MarieSeasonHook;
import dev.marie.framework.api.progression.MilestoneDefinition;
import dev.marie.framework.api.progression.ProfileDefinition;
import dev.marie.framework.api.progression.TrackerMilestoneDefinition;
import dev.marie.framework.api.reporting.ApplicationHistoryView;
import dev.marie.framework.api.reporting.ExportResolver;
import dev.marie.framework.api.reporting.ReportProvider;
import dev.marie.framework.api.source.SourcePairSynergy;
import dev.marie.framework.api.source.SourcePropertySignal;
import dev.marie.framework.api.source.SourceTriggerDefinition;
import dev.marie.framework.api.source.SourceTriggerListener;
import dev.marie.framework.api.value.ValueDefinition;
import dev.marie.framework.api.value.ValueSourceTrigger;
import dev.marie.framework.network.GenericStateSyncPayload;

import javax.annotation.Nullable;

import java.util.function.BiConsumer;

import dev.marie.framework.command.CommandCapability;
import dev.marie.framework.compat.CompatDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Static entry point for the MarieLib public API.
 *
 * <p>All interaction with the MarieLib value system from external mods
 * should go through this class. Methods provide read access to player value
 * state and write access to register custom values, effects, compatibilities,
 * and extension hooks.</p>
 *
 * <p>This class is not instantiable. All methods are static.</p>
 */
@ApiStatus.Stable
public final class MarieAPI {

    private MarieAPI() {}

    // ───────────────────────────────────────────────────────────────
    // Player State Queries
    // ───────────────────────────────────────────────────────────────

    /**
     * Returns the aggregate level for the given player based on their
     * current value levels and recent source consumption.
     *
     * @param player the player to query
     * @return the player's current aggregate level
     * @throws IllegalStateException if the value system is not initialized
     */
    @ApiStatus.Stable
    public static float getAggregateLevel(Player player) {
        return PlayerStateDelegate.getAggregateLevel(player);
    }

    /**
     * Returns the current level of a specific value for the given player.
     *
     * @param player      the player to query
     * @param valueKey the internal key of the value (e.g. "value_a")
     * @return the value level as a normalized float (0.0 to 1.0),
     *         or {@code -1.0f} if the value key is not recognized
     */
    @ApiStatus.Stable
    public static float getValueLevel(Player player, String valueKey) {
        return PlayerStateDelegate.getValueLevel(player, valueKey);
    }

    /**
     * Returns a read-only view of the player's source consumption memory,
     * exposing recent applying history and variety information.
     *
     * @param player the player to query
     * @return an {@link ApplicationHistoryView} for the given player
     * @throws IllegalStateException if the value system is not initialized
     */
    @ApiStatus.Stable
    public static ApplicationHistoryView getApplicationHistory(Player player) {
        return PlayerStateDelegate.getApplicationHistory(player);
    }
    /**
     * Alias for {@link #getAggregateLevel(Player)}.
     *
     * @param player the player to query
     * @return the player's current aggregate level
     */
    @ApiStatus.Stable
    public static float getTotalCount(Player player) {
        return getAggregateLevel(player);
    }

    /**
     * Returns an aggregated tracking snapshot for the given player including
     * total, all registered value levels, and source memory state.
     *
     * @param player the player to query
     * @return aggregated player tracking state snapshot
     */
    @ApiStatus.Stable
    public static MariePlayerData getTrackingData(Player player) {
        return PlayerStateDelegate.getTrackingData(player);
    }

    /**
     * Applies a direct value delta by posting a {@link ValueModifierEvent}
     * and then applying the final event amount if the event is not cancelled.
     *
     * @param player      the player to modify
     * @param valueKey the value key to modify
     * @param delta       the value delta to apply
     */
    @ApiStatus.Stable
    public static void modifyValue(Player player, String valueKey, float delta) {
        PlayerStateDelegate.modifyValue(player, valueKey, delta);
    }

    @ApiStatus.Stable
    public static String getVersion() {
        return MarieAPIVersion.VERSION;
    }

    // ───────────────────────────────────────────────────────────────
    // Registration — Values & Sources
    // ───────────────────────────────────────────────────────────────

    /**
     * Registers a custom value with the MarieLib system. The value will
     * participate in all standard mechanics (decay, thresholds, HUD rendering).
     *
     * <p>Must be called during mod initialization (before the server starts).</p>
     *
     * @param definition the value definition to register
     * @throws IllegalStateException    if called after initialization is complete
     * @throws IllegalArgumentException if a value with the same id already exists
     */
    @ApiStatus.Stable
    public static void registerValue(ValueDefinition definition) {
        ValueSourceRegistrationDelegate.registerValue(definition);
    }

    /**
     * Alias for {@link #registerValue(ValueDefinition)}.
     *
     * @param definition the value definition to register
     */
    @ApiStatus.Stable
    public static void addValue(ValueDefinition definition) {
        registerValue(definition);
    }

    /**
     * Registers a source item's value classification, mapping a source to a
     * specific value with a given contribution amount.
     *
     * @param sourceId      the registry identifier of the source item
     * @param valueKey the value key this source contributes to
     * @param amount      the value contribution amount per consumption
     * @throws IllegalArgumentException if the value key is not registered
     */
    @ApiStatus.Stable
    public static void registerSourceClassification(ResourceLocation sourceId, String valueKey, float amount) {
        ValueSourceRegistrationDelegate.registerSourceClassification(sourceId, valueKey, amount);
    }

    /**
     * Alias for {@link #registerSourceClassification(ResourceLocation, String, float)}.
     *
     * @param sourceId      the registry identifier of the source item
     * @param valueKey the value key this source contributes to
     * @param amount      the value contribution amount per consumption
     */
    @ApiStatus.Stable
    public static void registerSource(ResourceLocation sourceId, String valueKey, float amount) {
        registerSourceClassification(sourceId, valueKey, amount);
    }

    // ───────────────────────────────────────────────────────────────
    // Registration — Effects & Thresholds
    // ───────────────────────────────────────────────────────────────

    /**
     * Registers a custom effect triggered by value threshold crossings.
     *
     * @param definition the effect definition describing the trigger and effect
     * @throws IllegalArgumentException if the referenced value or effect doesn't exist
     */
    @ApiStatus.Stable
    public static void registerCustomEffect(ThresholdEffect definition) {
        EffectRegistrationDelegate.registerCustomEffect(definition);
    }

    /**
     * Alias for {@link #registerCustomEffect(ThresholdEffect)}.
     *
     * @param definition the effect definition describing the trigger and effect
     */
    @ApiStatus.Stable
    public static void addEffect(ThresholdEffect definition) {
        registerCustomEffect(definition);
    }

    // ───────────────────────────────────────────────────────────────
    // Registration — Compatibility
    // ───────────────────────────────────────────────────────────────

    /**
     * Registers a compatibility entry that maps source items from another mod
     * to MarieLib value keys.
     *
     * @param definition the compat definition with source-to-value mappings
     */
    @ApiStatus.Stable
    public static void registerCompatEntry(CompatDefinition definition) {
        CompatRegistrationDelegate.registerCompatEntry(definition);
    }

    /**
     * Alias for {@link #registerCompatEntry(CompatDefinition)}.
     *
     * @param definition the compat definition with source-to-value mappings
     */
    @ApiStatus.Stable
    public static void addCompat(CompatDefinition definition) {
        registerCompatEntry(definition);
    }

    // ───────────────────────────────────────────────────────────────
    // Registration — Synergies & Combos
    // ───────────────────────────────────────────────────────────────

    /**
     * Registers a value synergy interaction between two values.
     * When both values meet their conditions simultaneously, the synergy
     * effect is applied.
     *
     * @param definition the value synergy definition
     * @throws IllegalArgumentException if referenced values don't exist
     */
    @ApiStatus.Stable
    public static void registerValueSynergy(SynergyDefinition definition) {
        SynergyRegistrationDelegate.registerValueSynergy(definition);
    }

    /**
     * Alias for {@link #registerValueSynergy(SynergyDefinition)}.
     *
     * @param definition the value synergy definition
     */
    @ApiStatus.Stable
    public static void addValueSynergy(SynergyDefinition definition) {
        registerValueSynergy(definition);
    }

    /**
     * Registers a source synergy (source pair) that grants a bonus value burst
     * when two sources are consumed within a time window.
     *
     * @param definition the source synergy definition
     */
    @ApiStatus.Stable
    public static void registerSourcePairSynergy(SourcePairSynergy definition) {
        SynergyRegistrationDelegate.registerSourcePairSynergy(definition);
    }

    /**
     * Alias for {@link #registerSourcePairSynergy(SourcePairSynergy)}.
     *
     * @param definition the source synergy definition
     */
    @ApiStatus.Stable
    public static void addSourceSynergy(SourcePairSynergy definition) {
        registerSourcePairSynergy(definition);
    }

    // ───────────────────────────────────────────────────────────────
    // Registration — Profiles & Milestones
    // ───────────────────────────────────────────────────────────────

    /**
     * Registers a named tracking profile archetype that players can switch between.
     *
     * @param definition the tracking profile definition with custom thresholds and bonuses
     * @throws IllegalArgumentException if a profile with the same id already exists
     */
    @ApiStatus.Stable
    public static void registerTrackingProfile(ProfileDefinition definition) {
        ProfileMilestoneSeasonDelegate.registerTrackingProfile(definition);
    }

    /**
     * Alias for {@link #registerTrackingProfile(ProfileDefinition)}.
     *
     * @param definition the tracking profile definition with custom thresholds and bonuses
     */
    @ApiStatus.Stable
    public static void addProfile(ProfileDefinition definition) {
        registerTrackingProfile(definition);
    }

    /**
     * Registers a value milestone that fires once when a player reaches
     * a cumulative value goal.
     *
     * @param definition the milestone definition
     * @throws IllegalArgumentException if a milestone with the same id already exists
     */
    @ApiStatus.Stable
    public static void registerMilestone(MilestoneDefinition definition) {
        ProfileMilestoneSeasonDelegate.registerMilestone(definition);
    }

    /**
     * Alias for {@link #registerMilestone(MilestoneDefinition)}.
     *
     * @param definition the milestone definition
     */
    @ApiStatus.Stable
    public static void addMilestone(MilestoneDefinition definition) {
        registerMilestone(definition);
    }

    /**
     * Registers a tracker milestone that fires once when a player's built-in MarieLib
     * tracker (rather than a consuming mod's own value key) reaches a cumulative goal.
     *
     * @param definition the tracker milestone definition
     * @throws IllegalArgumentException if a milestone with the same id already exists
     */
    @ApiStatus.Stable
    public static void registerTrackerMilestone(TrackerMilestoneDefinition definition) {
        ProfileMilestoneSeasonDelegate.registerTrackerMilestone(definition);
    }

    /**
     * Alias for {@link #registerTrackerMilestone(TrackerMilestoneDefinition)}.
     *
     * @param definition the tracker milestone definition
     */
    @ApiStatus.Stable
    public static void addTrackerMilestone(TrackerMilestoneDefinition definition) {
        registerTrackerMilestone(definition);
    }

    // ───────────────────────────────────────────────────────────────
    // Registration — Hooks & Modifiers
    // ───────────────────────────────────────────────────────────────

    /**
     * Registers a season hook for integrating with Serene Seasons or similar mods.
     * Seasonal modifiers will be applied to value decay and absorption rates.
     *
     * @param hook the season hook implementation
     */
    @ApiStatus.Stable
    public static void registerSeasonHook(MarieSeasonHook hook) {
        ProfileMilestoneSeasonDelegate.registerSeasonHook(hook);
    }

    /**
     * Alias for {@link #registerSeasonHook(MarieSeasonHook)}.
     *
     * @param hook the season hook implementation
     */
    @ApiStatus.Stable
    public static void addSeasonHook(MarieSeasonHook hook) {
        registerSeasonHook(hook);
    }

    /**
     * Registers a value absorption modifier that dynamically adjusts how much
     * of a value a player absorbs based on their current state.
     *
     * @param modifier the absorption modifier implementation
     */
    @ApiStatus.Stable
    public static void registerAbsorptionModifier(AbsorptionModifier modifier) {
        HookProviderRegistrationDelegate.registerAbsorptionModifier(modifier);
    }

    /**
     * Alias for {@link #registerAbsorptionModifier(AbsorptionModifier)}.
     *
     * @param modifier the absorption modifier implementation
     */
    @ApiStatus.Stable
    public static void addAbsorptionModifier(AbsorptionModifier modifier) {
        registerAbsorptionModifier(modifier);
    }

    /**
     * Registers a tracking report provider that injects custom sections into the
     * {@code /marie} command report output.
     *
     * @param provider the report provider implementation
     */
    @ApiStatus.Stable
    public static void registerReportProvider(ReportProvider provider) {
        ReportingRegistrationDelegate.registerReportProvider(provider);
    }

    /**
     * Alias for {@link #registerReportProvider(ReportProvider)}.
     *
     * @param provider the report provider implementation
     */
    @ApiStatus.Stable
    public static void addReportSection(ReportProvider provider) {
        registerReportProvider(provider);
    }

    /**
     * Registers a block-hover provider that supplies "what's at this block" data over the
     * client/server request-response network channel.
     *
     * @param provider the block-hover provider implementation
     * @throws IllegalStateException    if registration is closed
     * @throws IllegalArgumentException if {@code provider} is null
     */
    @ApiStatus.Experimental
    public static void registerBlockHoverProvider(BlockHoverProvider provider) {
        HookProviderRegistrationDelegate.registerBlockHoverProvider(provider);
    }

    /**
     * Registers a source property signal used by the scanner to classify
     * items. The consuming mod provides all signal logic — the lib has
     * no opinion on what item properties mean.
     */
    @ApiStatus.Stable
    public static void registerSourcePropertySignal(SourcePropertySignal signal) {
        HookProviderRegistrationDelegate.registerSourcePropertySignal(signal);
    }

    /**
     * @deprecated {@link ExportResolver} carries no registry key, so this overload has no way
     * to know which registry to iterate — it cannot be implemented correctly. Use
     * {@link #registerExportResolver(String, net.minecraft.resources.ResourceKey, ExportResolver)}
     * instead, which takes the registry key explicitly.
     * @param resolver the resolver (unused — this overload always throws)
     * @param <T> the registry entry type
     * @throws UnsupportedOperationException always
     */
    @ApiStatus.Stable
    @Deprecated
    public static <T> void registerExportResolver(ExportResolver<T> resolver) {
        HookProviderRegistrationDelegate.registerExportResolver(resolver);
    }

    /**
     * Registers an export resolver that produces per-entry data for an entire registry,
     * written to an editable config file when {@code /marieslib dump <resolverId>} is run.
     * The consuming mod decides what the exported data means for each entry.
     *
     * @param key         unique identifier for this export, used as the output filename prefix
     * @param registryKey the registry this resolver applies to (e.g. {@link net.minecraft.core.registries.Registries#ITEM})
     * @param resolver    produces exportable data for each entry in the registry
     * @param <T> the registry entry type
     */
    @ApiStatus.Stable
    public static <T> void registerExportResolver(
            String key,
            net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<T>> registryKey,
            ExportResolver<T> resolver) {
        HookProviderRegistrationDelegate.registerExportResolver(key, registryKey, resolver);
    }

    /**
     * Registers a config validator that checks consuming-mod config files on demand.
     * MarieLib runs validators and collects results; the consuming mod defines what "valid" means.
     *
     * @param validator the validator to register
     * @throws IllegalArgumentException if {@code validator} is null
     */
    @ApiStatus.Stable
    public static void registerConfigValidator(ConfigValidator validator) {
        ConfigValidationDelegate.registerConfigValidator(validator);
    }

    /**
     * Registers a sleep bonus evaluator. When a player wakes up,
     * MarieLib calls the evaluator and applies any returned effect.
     * Only one evaluator should be registered per mod; registering
     * multiple evaluators is supported and all are called in order.
     */
    @ApiStatus.Stable
    public static void registerSleepBonusEvaluator(SleepBonusEvaluator evaluator) {
        HookProviderRegistrationDelegate.registerSleepBonusEvaluator(evaluator);
    }

    /**
     * Registers a consuming-mod trigger listener. MarieLib will call
     * {@link SourceTriggerListener#register(IEventBus)} during
     * server event bus setup so the listener can subscribe its own
     * NeoForge event listeners.
     *
     * <p>This is optional — consuming mods may instead call
     * {@link #fireSourceTrigger} directly from their own event handlers
     * without using this registration.</p>
     */
    @ApiStatus.Stable
    public static void registerTriggerHandler(SourceTriggerListener handler) {
        TriggerRegistrationDelegate.registerTriggerHandler(handler);
    }

    /**
     * Registers a non-item source contribution for a specific trigger type.
     * When {@link #fireSourceTrigger} is called with a matching trigger,
     * the registered amounts are added to the pipeline.
     *
     * <p>Use this for EMC transactions, crafting, block breaking, etc.</p>
     */
    @ApiStatus.Stable
    public static void registerTriggerSource(SourceTriggerDefinition definition) {
        TriggerRegistrationDelegate.registerTriggerSource(definition);
    }

    /** Alias for registerTriggerSource. */
    @ApiStatus.Stable
    public static void addTriggerSource(SourceTriggerDefinition definition) {
        registerTriggerSource(definition);
    }

    // ───────────────────────────────────────────────────────────────
    // Registration — Tag Audit
    // ───────────────────────────────────────────────────────────────

    /**
     * Registers a tag audit rule. The consuming mod's TagRule implementation
     * inspects tag data via a TagAuditContext (also consuming-mod-supplied) and
     * produces issues and/or fix suggestions, run by {@link dev.marie.framework.tagaudit.TagScanner}.
     *
     * @param rule the rule implementation
     */
    @ApiStatus.Stable
    public static void registerTagRule(dev.marie.framework.tagaudit.rule.TagRule rule) {
        TagAuditRegistrationDelegate.registerTagRule(rule);
    }

    /**
     * Registers a TagAuditContext for this mod, so {@code /marieslib audit_tags <modid>}
     * can run {@link dev.marie.framework.tagaudit.TagScanner} against it.
     *
     * @param modId   the registering mod's id, used as the lookup key for the command
     * @param context the consuming mod's TagAuditContext implementation
     */
    @ApiStatus.Stable
    public static void registerTagAuditContext(String modId, dev.marie.framework.tagaudit.model.TagAuditContext context) {
        TagAuditRegistrationDelegate.registerTagAuditContext(modId, context);
    }

    // ───────────────────────────────────────────────────────────────
    // Registration — Command Capabilities
    // ───────────────────────────────────────────────────────────────

    @ApiStatus.Experimental
    public static void registerCommandCapability(
            ResourceLocation modId,
            ResourceLocation capability,
            CommandCapability handler) {
        CommandCapabilityDelegate.registerCommandCapability(modId, capability, handler);
    }

    /**
     * Fires the value pipeline for a custom trigger on a player.
     * Use this to contribute values from non-item actions (crafting,
     * EMC transactions, block breaking, etc.).
     *
     * <p>Must be called server-side. The trigger will be processed through
     * the full pipeline including absorption modifiers, synergies, and
     * threshold checks.</p>
     *
     * @param player  the server-side player receiving the value
     * @param trigger the trigger describing the source action
     */
    @ApiStatus.Stable
    public static void fireSourceTrigger(ServerPlayer player, ValueSourceTrigger trigger) {
        SourceTriggerFiringDelegate.fireSourceTrigger(player, trigger);
    }

    /**
     * Fires the value pipeline for a custom trigger on a player.
     * For {@link ValueSourceTrigger.TriggerType#ITEM_CONSUMED}, pass the consumed stack so
     * classification lookup can run.
     *
     * @param player  the server-side player receiving the value
     * @param trigger the trigger describing the source action
     * @param stack   the item stack for item-based triggers, or {@code null} otherwise
     */
    @ApiStatus.Stable
    public static void fireSourceTrigger(ServerPlayer player, ValueSourceTrigger trigger, @Nullable ItemStack stack) {
        SourceTriggerFiringDelegate.fireSourceTrigger(player, trigger, stack);
    }

    // ───────────────────────────────────────────────────────────────
    // Registration — Networking
    // ───────────────────────────────────────────────────────────────

    /**
     * Registers a server-side handler invoked whenever a client sends a
     * {@link GenericStateSyncPayload}. MarieLib does not interpret the payload's tag — the
     * handler defines what the synced state means and how to apply it.
     *
     * <p>Must be called during mod initialization (before the server starts).</p>
     *
     * @param handler called on the server network thread's work queue with the sending player
     *                 and the received payload
     * @throws IllegalStateException    if called after initialization is complete
     * @throws IllegalArgumentException if {@code handler} is null
     */
    @ApiStatus.Experimental
    public static void registerGenericStateSyncHandler(BiConsumer<ServerPlayer, GenericStateSyncPayload> handler) {
        NetworkRegistrationDelegate.registerGenericStateSyncHandler(handler);
    }

}
