package dev.marie.framework.ui.modulesettings;

import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.component.ComponentState;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Map-backed {@link PersistenceProvider} for tests; counts saves so tests can assert what is (not) written. */
final class InMemoryStore implements PersistenceProvider {

    final Map<String, ComponentState> data = new HashMap<>();
    int saves;

    @Override
    public Optional<ComponentState> load(String componentId) {
        return Optional.ofNullable(data.get(componentId));
    }

    @Override
    public void save(String componentId, ComponentState state) {
        saves++;
        data.put(componentId, state);
    }

    @Override
    public void remove(String componentId) {
        data.remove(componentId);
    }
}
