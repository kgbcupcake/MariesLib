package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.geometry.Bounds;

/**
 * Draws a container's modules for {@link ModuleEditHost#asComponent}: lay out and render each module,
 * passing it through {@link ModuleEditHost#track} so it's drawn at its live size mid-drag. The host's
 * frame has already begun and its overlay is drawn after this returns.
 */
@ApiStatus.Experimental
@FunctionalInterface
public interface EditHostRenderer {

    void render(RenderContext context, Bounds area, ModuleEditHost host);
}
