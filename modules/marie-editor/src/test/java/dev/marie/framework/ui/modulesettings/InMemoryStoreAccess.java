package dev.marie.framework.ui.modulesettings;

import dev.marie.framework.ui.PersistenceProvider;

/** Lets tests in other packages get a fresh {@link InMemoryStore} without widening its visibility. */
public final class InMemoryStoreAccess {

    private InMemoryStoreAccess() {}

    public static PersistenceProvider create() {
        return new InMemoryStore();
    }
}
