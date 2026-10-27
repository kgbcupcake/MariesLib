package dev.marie.framework.ui.persistence;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.component.ComponentState;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/** Backs {@link PersistenceProvider#scoped}: a prefixed view of another store, one cached instance per base and prefix. */
@ApiStatus.Internal
public final class ScopedPersistenceProvider implements PersistenceProvider {

    private static final Map<PersistenceProvider, Map<String, ScopedPersistenceProvider>> CACHE = new WeakHashMap<>();

    private final PersistenceProvider base;
    private final String prefix;

    private ScopedPersistenceProvider(PersistenceProvider base, String prefix) {
        this.base = base;
        this.prefix = prefix;
    }

    public static synchronized PersistenceProvider of(PersistenceProvider base, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return base;
        }
        // Scoping a scoped view nests the prefixes onto the root store, so equal paths share one instance.
        if (base instanceof ScopedPersistenceProvider scoped) {
            return of(scoped.base, scoped.prefix + prefix);
        }
        return CACHE.computeIfAbsent(base, b -> new HashMap<>())
                .computeIfAbsent(prefix, p -> new ScopedPersistenceProvider(base, p));
    }

    public String prefix() {
        return prefix;
    }

    @Override
    public Optional<ComponentState> load(String componentId) {
        return base.load(prefix + componentId);
    }

    @Override
    public void save(String componentId, ComponentState state) {
        base.save(prefix + componentId, state);
    }

    @Override
    public void remove(String componentId) {
        base.remove(prefix + componentId);
    }
}
