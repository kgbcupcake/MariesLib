package dev.marie.framework.ui.persistence;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.component.ComponentState;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/** Backs {@link PersistenceProvider#scoped}: a prefixed view of another store, one cached instance per base and prefix. */
@ApiStatus.Internal
public final class ScopedPersistenceProvider implements PersistenceProvider {

    // Views are held weakly too: each one references its base strongly, so a strong value would keep
    // the weak key reachable forever and no base (or its views) could ever be collected.
    private static final Map<PersistenceProvider, Map<String, WeakReference<ScopedPersistenceProvider>>> CACHE = new WeakHashMap<>();

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
        Map<String, WeakReference<ScopedPersistenceProvider>> views = CACHE.computeIfAbsent(base, b -> new HashMap<>());
        // Drop prefixes whose views were collected, so short-lived profiles don't pile up under a long-lived base.
        views.values().removeIf(r -> r.get() == null);
        WeakReference<ScopedPersistenceProvider> ref = views.get(prefix);
        ScopedPersistenceProvider view = ref != null ? ref.get() : null;
        if (view == null) {
            view = new ScopedPersistenceProvider(base, prefix);
            views.put(prefix, new WeakReference<>(view));
        }
        return view;
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
