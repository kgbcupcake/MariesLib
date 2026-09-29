package dev.marie.framework.ui;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.ui.component.ComponentState;

import java.util.Optional;

/**
 * Saves/loads {@link ComponentState} keyed by component id. Deliberately knows nothing about how
 * the data is serialized or where it lives underneath — swap the implementation (e.g. a future
 * server-sync or per-world provider) without touching any MarieUI caller.
 */
@ApiStatus.Experimental
public interface PersistenceProvider {

    Optional<ComponentState> load(String componentId);

    void save(String componentId, ComponentState state);

    /**
     * Clears any persisted state for {@code componentId}, so a future {@link #load} returns
     * empty. Intended for one-time resets when a stored value's meaning has changed (e.g. a
     * coordinate-space migration) and old data must not be reinterpreted under a new scheme —
     * not a general end-user "reset position" action. Default no-op for providers that don't
     * need it; {@code MarieConfigPersistenceProvider} is the only current implementation.
     */
    default void remove(String componentId) {
    }
}
