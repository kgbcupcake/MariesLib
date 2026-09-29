package dev.marie.framework.ui.geometry;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.ui.component.Constraint;

/** A width/height pair used by {@link Constraint} to describe sizing intent, not a resolved layout result. */
@ApiStatus.Experimental
public record Size(int width, int height) {

    public static final Size ZERO = new Size(0, 0);
    public static final Size UNBOUNDED = new Size(Integer.MAX_VALUE, Integer.MAX_VALUE);
}
