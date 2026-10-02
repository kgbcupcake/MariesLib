package dev.marie.framework.client.config;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.core.MarieContext;
import dev.marie.framework.core.MarieBootstrap;
import net.minecraft.client.gui.screens.Screen;

/**
 * Client-only casts for screen factories registered on {@link MarieContext}
 * or {@link MarieBootstrap}.
 */
@ApiStatus.Internal
public final class ClientScreenFactories {

    private ClientScreenFactories() {}

    public static Screen getConfigScreen() {
        Object screen = MarieContext.isRegistered()
                ? MarieContext.get().configScreenFactory()
                : MarieBootstrap.getConfigScreenFactory().get();
        return (Screen) screen;
    }

    public static Screen exportScreen(Screen parent) {
        Object screen = MarieContext.isRegistered()
                ? MarieContext.get().exportScreenFactory(parent)
                : MarieBootstrap.getExportScreenFactory().apply(parent);
        return (Screen) screen;
    }

    public static Screen importScreen(Screen parent) {
        Object screen = MarieContext.isRegistered()
                ? MarieContext.get().importScreenFactory(parent)
                : MarieBootstrap.getImportScreenFactory().apply(parent);
        return (Screen) screen;
    }
}
