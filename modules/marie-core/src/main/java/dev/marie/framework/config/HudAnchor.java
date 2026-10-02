package dev.marie.framework.config;

import dev.marie.framework.api.ApiStatus;

/**
 * Corner used to place the value HUD before applying pixel offsets.
 */
@ApiStatus.Experimental
public enum HudAnchor {
    BOTTOM_LEFT,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_RIGHT
}
