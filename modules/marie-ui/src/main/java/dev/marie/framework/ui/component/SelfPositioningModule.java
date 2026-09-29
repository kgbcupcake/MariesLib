package dev.marie.framework.ui.component;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.ui.geometry.Bounds;

/**
 * A {@link MarieComponent} that resolves and owns its own {@link Bounds}.
 *
 * <p>Modules expose their occupied height through {@link #localHeight()} so containers can stack
 * them without knowing their concrete implementation.
 */

@ApiStatus.Experimental
public interface SelfPositioningModule extends MarieComponent {

    /** Local (pre-scale) height occupied by this module; 0 when hidden or unavailable. */

    int localHeight();

    /** Current resolved screen {@link Bounds} for this module. */
    Bounds resolvedBounds();
}