package dev.marie.framework.data;

import dev.marie.framework.api.ApiStatus;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.Set;

/**
 * Registers and exposes the MarieLib datapack reload state.
 */
@ApiStatus.Internal
public final class MarieDataManager {

    private static volatile MarieDataLoader loader;

    private MarieDataManager() {}

    private static MarieDataLoader loader() {
        MarieDataLoader current = loader;
        if (current == null) {
            synchronized (MarieDataManager.class) {
                current = loader;
                if (current == null) {
                    loader = current = new MarieDataLoader();
                }
            }
        }
        return current;
    }

    public static void registerReloadListener(AddReloadListenerEvent event) {
        event.addListener(loader());
    }

    /** Expands queued {@code "tag"} source classifications; call once item tags are bound. */
    public static void resolvePendingTagClassifications() {
        loader().resolvePendingTagClassifications();
    }

    public static void setCallbacks(MarieDataLoader.Callbacks callbacks) {
        loader().setCallbacks(callbacks);
    }

    public static Set<ResourceLocation> getLoadedValues() {
        return loader().getLoadedValues();
    }

    public static Set<ResourceLocation> getLoadedSourceClassifications() {
        return loader().getLoadedSourceClassifications();
    }

    public static Set<ResourceLocation> getLoadedFoodOverrides() {
        return loader().getLoadedFoodOverrides();
    }

    public static Set<ResourceLocation> getLoadedEffects() {
        return loader().getLoadedEffects();
    }

    public static Set<ResourceLocation> getLoadedSynergies() {
        return loader().getLoadedSynergies();
    }

    public static Set<ResourceLocation> getLoadedSourcePairSynergies() {
        return loader().getLoadedSourcePairSynergies();
    }

    public static Set<ResourceLocation> getLoadedMilestones() {
        return loader().getLoadedMilestones();
    }

    public static Set<ResourceLocation> getLoadedProfiles() {
        return loader().getLoadedProfiles();
    }

    public static Set<ResourceLocation> getLoadedCompatEntries() {
        return loader().getLoadedCompatEntries();
    }
}
