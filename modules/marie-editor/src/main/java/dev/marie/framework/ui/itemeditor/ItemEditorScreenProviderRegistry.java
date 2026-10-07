package dev.marie.framework.ui.itemeditor;

import dev.marie.framework.api.ApiStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mod-scoped registry of {@link ItemEditorScreenProvider}s, mirroring {@link
 * ItemEditorFieldProviderRegistry}: {@link ItemEditorPanel} only ever asks for the screens
 * registered under the modId it's currently editing.
 */
@ApiStatus.Experimental
public final class ItemEditorScreenProviderRegistry {

    private static final Map<String, List<ItemEditorScreenProvider>> PROVIDERS = new ConcurrentHashMap<>();

    private ItemEditorScreenProviderRegistry() {}

    /** Registers {@code provider} as a File-menu destination whenever the editor is scoped to {@code modId}. */
    public static void register(String modId, ItemEditorScreenProvider provider) {
        if (modId == null) {
            throw new IllegalArgumentException("modId cannot be null");
        }
        if (provider == null) {
            throw new IllegalArgumentException("provider cannot be null");
        }
        PROVIDERS.computeIfAbsent(modId, k -> new ArrayList<>()).add(provider);
    }

    /** The providers registered for {@code modId}, in registration order; empty if none. */
    public static List<ItemEditorScreenProvider> get(String modId) {
        return PROVIDERS.getOrDefault(modId, Collections.emptyList());
    }

    @ApiStatus.Internal
    public static void resetInternal() {
        PROVIDERS.clear();
    }
}
