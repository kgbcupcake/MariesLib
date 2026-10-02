package dev.marie.framework.ui.geometry;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.ui.component.MarieComponent;

/** Resolved pixel rectangle a {@link MarieComponent} is told to occupy for one render/input pass. */
@ApiStatus.Experimental
public record Bounds(int x, int y, int width, int height) {

    public boolean contains(int px, int py) {
        return px >= x && py >= y && px < x + width && py < y + height;
    }
}
