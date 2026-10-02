package dev.marie.framework.ui.geometry;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.ui.component.MarieComponent;

/** Which corner/edge/center of the available area a {@link MarieComponent} is positioned relative to. */
@ApiStatus.Experimental
public enum Anchor {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    CENTER_LEFT, CENTER, CENTER_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
}
