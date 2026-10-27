package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.color.ColorScope;
import dev.marie.framework.color.MarieColors;
import dev.marie.framework.ui.PersistenceProvider;

import java.util.function.Supplier;

/**
 * One settings profile for a set of modules: the store their positions, sizes and display settings
 * live in, plus the {@link ColorScope} their colors resolve through. Two profiles of the same modules
 * (say a full screen and a compact copy of it) never share a setting or a color.
 *
 * <pre>{@code
 * ModuleProfile full = ModuleProfile.of(store);
 * ModuleProfile compact = ModuleProfile.scoped(store, "compact/");
 * compact.run(() -> { renderModules(compact.store()); return null; });
 * }</pre>
 */
@ApiStatus.Experimental
public interface ModuleProfile {

    /** Where this profile's module settings are saved. */
    PersistenceProvider store();

    /** How this profile's colors resolve; {@link ColorScope#NONE} to share the base colors. */
    default ColorScope colorScope() {
        return ColorScope.NONE;
    }

    /** Runs {@code action} with this profile's color scope active, for rendering or building option panels. */
    default <T> T run(Supplier<T> action) {
        return MarieColors.withColorScope(colorScope(), action);
    }

    /** As {@link #run(Supplier)}, without a result. */
    default void run(Runnable action) {
        run(() -> {
            action.run();
            return null;
        });
    }

    /** The base profile over {@code store}, sharing the base colors. */
    static ModuleProfile of(PersistenceProvider store) {
        return () -> store;
    }

    /**
     * A profile whose settings live under {@code prefix} in {@code base} and whose colors use {@link
     * ColorScope#prefixed}. Both use {@link ColorScope#normalizePrefix}, so e.g. {@code "Compact/"} and
     * {@code "compact/"} are the same profile rather than separate settings sharing one set of colors.
     */
    static ModuleProfile scoped(PersistenceProvider base, String prefix) {
        String normalized = ColorScope.normalizePrefix(prefix);
        return of(base.scoped(normalized), ColorScope.prefixed(normalized));
    }

    static ModuleProfile of(PersistenceProvider store, ColorScope colors) {
        return new ModuleProfile() {
            @Override
            public PersistenceProvider store() {
                return store;
            }

            @Override
            public ColorScope colorScope() {
                return colors;
            }
        };
    }
}
