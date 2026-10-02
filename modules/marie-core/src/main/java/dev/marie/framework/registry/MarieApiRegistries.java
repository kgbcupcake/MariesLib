package dev.marie.framework.registry;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.api.effects.SynergyDefinition;
import dev.marie.framework.api.progression.MilestoneDefinition;
import dev.marie.framework.api.progression.ProfileDefinition;
import dev.marie.framework.api.progression.TrackerMilestoneDefinition;
import dev.marie.framework.api.source.SourcePairSynergy;
import dev.marie.framework.config.validation.ConfigValidatorRegistry;
import dev.marie.framework.api.registry.AbsorptionModifierRegistry;
import dev.marie.framework.api.registry.ProfileRegistry;
import dev.marie.framework.api.registry.MilestoneRegistry;
import dev.marie.framework.api.registry.TrackerMilestoneRegistry;
import dev.marie.framework.api.registry.ReportProviderRegistry;
import dev.marie.framework.api.registry.SeasonHookRegistry;
import dev.marie.framework.api.registry.SleepBonusEvaluatorRegistry;
import dev.marie.framework.api.registry.SourcePropertySignalRegistry;
import dev.marie.framework.api.registry.SynergyRegistry;
import dev.marie.framework.color.ColorDefinitionRegistry;
import dev.marie.framework.runtime.TriggerHandlerRegistry;
import dev.marie.framework.tracking.tracker.registry.TrackerRegistry;

import java.util.List;

/**
 * Coordinates freeze/reset for API definition registries around bootstrap and datapack reload.
 */
@ApiStatus.Internal
public final class MarieApiRegistries {

    private static boolean datapackApplyCompletedOnce;

    /**
     * Entries registered from code (mod constructor / common setup) before the first datapack
     * apply pass, captured at the start of that pass. Re-seeded after every later reset so a
     * {@code /reload} only replaces datapack-sourced entries.
     */
    private static List<ProfileDefinition> codeProfiles = List.of();
    private static List<MilestoneDefinition> codeMilestones = List.of();
    private static List<TrackerMilestoneDefinition> codeTrackerMilestones = List.of();
    private static List<SynergyDefinition> codeValueSynergies = List.of();
    private static List<SourcePairSynergy> codeSourcePairSynergies = List.of();

    private MarieApiRegistries() {}

    /**
     * Freezes value-tracking-specific list registries that only receive mod-constructor
     * registrations (no datapack pass). Domain-agnostic registries (e.g.
     * {@code BlockHoverProviderRegistry}) are frozen separately via
     * {@link dev.marie.framework.core.MarieBootstrap#attachFrameworkServices}.
     */
    public static void freezeValueTrackingOnlyRegistriesAfterCommonSetup() {
        AbsorptionModifierRegistry.freezeInternal();
        SeasonHookRegistry.freezeInternal();
        ReportProviderRegistry.freezeInternal();
        SourcePropertySignalRegistry.freezeInternal();
        SleepBonusEvaluatorRegistry.freezeInternal();
        TriggerHandlerRegistry.freezeInternal();
        ConfigValidatorRegistry.freezeInternal();
    }

    /**
     * Called at the start of each {@link dev.marie.framework.data.MarieDataLoader} apply pass.
     * On the first pass, mod-constructor entries are preserved and snapshotted; on later passes,
     * datapack-backed registries are cleared before JSON is re-applied.
     *
     * <p>{@link ProfileRegistry}, {@link MilestoneRegistry}, {@link TrackerMilestoneRegistry} and
     * {@link SynergyRegistry} are re-seeded from the first-pass snapshot right after the reset, so
     * entries registered from Java at mod init survive every reload without the consuming mod
     * doing anything — only the datapack-sourced entries are replaced.</p>
     *
     * <p>{@link TrackerRegistry} and {@link ColorDefinitionRegistry} have no datapack-driven
     * repopulation path (no {@code registerTracker}/{@code registerColor} slot in
     * {@link dev.marie.framework.data.MarieDataLoader.Callbacks}), so entries registered purely
     * via Java at mod init (e.g. {@code MarieAPI.registerTracker}, {@code MarieColors.registerColor})
     * are wiped here on every reload after the first and are not restored automatically — nothing
     * re-invokes the consuming mod's registration code on its own. Consuming mods should re-invoke
     * their registration code from {@code MarieContext.reloadBroadcastHook()}
     * ({@link dev.marie.framework.handler.ReloadGuardListener#reloadAndBroadcast}), which fires
     * after this reset/refreeze pass completes for every reload; re-registration is safe to repeat
     * since these two registries upsert on duplicate keys.</p>
     */
    public static void onDatapackApplyBegin() {
        if (!datapackApplyCompletedOnce) {
            codeProfiles = List.copyOf(ProfileRegistry.getAll());
            codeMilestones = List.copyOf(MilestoneRegistry.getAll());
            codeTrackerMilestones = List.copyOf(TrackerMilestoneRegistry.getAll());
            codeValueSynergies = List.copyOf(SynergyRegistry.getValueSynergies());
            codeSourcePairSynergies = List.copyOf(SynergyRegistry.getSourcePairSynergies());
            return;
        }
        ProfileRegistry.resetInternal();
        MilestoneRegistry.resetInternal();
        TrackerMilestoneRegistry.resetInternal();
        SynergyRegistry.resetInternal();
        TrackerRegistry.resetInternal();
        ColorDefinitionRegistry.resetInternal();

        codeProfiles.forEach(ProfileRegistry::register);
        codeMilestones.forEach(MilestoneRegistry::register);
        codeTrackerMilestones.forEach(TrackerMilestoneRegistry::register);
        codeValueSynergies.forEach(SynergyRegistry::registerValueSynergy);
        codeSourcePairSynergies.forEach(SynergyRegistry::registerSourcePairSynergy);
    }

    /**
     * Called at the end of each datapack apply pass, before the reload scope closes.
     */
    public static void onDatapackApplyEnd() {
        ProfileRegistry.freezeInternal();
        MilestoneRegistry.freezeInternal();
        TrackerMilestoneRegistry.freezeInternal();
        SynergyRegistry.freezeInternal();
        TrackerRegistry.freezeInternal();
        ColorDefinitionRegistry.freezeInternal();
        datapackApplyCompletedOnce = true;
    }

    /** Test hook: back to the pre-first-reload state. */
    static void resetForTests() {
        datapackApplyCompletedOnce = false;
        codeProfiles = List.of();
        codeMilestones = List.of();
        codeTrackerMilestones = List.of();
        codeValueSynergies = List.of();
        codeSourcePairSynergies = List.of();
    }
}
