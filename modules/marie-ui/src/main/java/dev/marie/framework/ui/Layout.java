package dev.marie.framework.ui;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.ui.component.Constraint;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.List;
import java.util.Map;

/**
 * Given an available area and a set of components with their {@link Constraint}s, computes each
 * child's resolved {@link Bounds}. Layouts consume Constraint data; they must not invent their
 * own sizing rules.
 */
@ApiStatus.Experimental
public interface Layout {

    Map<MarieComponent, Bounds> computeBounds(Bounds available, List<MarieComponent> children);
}
