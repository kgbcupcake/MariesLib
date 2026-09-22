package dev.marie.framework.ui.modulesettings;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.component.ComponentState;

/**
 * The on/off "move mode" flags a host polls to decide what dragging its content does. Storage reuses
 * {@link ComponentState#collapsed()} as a generic boolean under a dedicated {@code #moveContent}-
 * suffixed key — unrelated to any window's own collapsed state, the same field reuse other callers
 * make rather than inventing a new record shape for one boolean. This is the single owner of that key
 * convention; {@code ScaleConfigPanel} delegates to it.
 */
@ApiStatus.Internal
public final class MoveFlags {

    private static final String SUFFIX = "#moveContent";

    private MoveFlags() {}

    public static boolean isOn(PersistenceProvider persistence, String flagId) {
        return persistence.load(flagId + SUFFIX).map(ComponentState::collapsed).orElse(false);
    }

    public static void set(PersistenceProvider persistence, String flagId, boolean on) {
        persistence.save(flagId + SUFFIX, new ComponentState(0, 0, 0, 0, on, false, false, 0));
    }
}
