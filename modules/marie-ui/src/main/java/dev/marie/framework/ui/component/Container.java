package dev.marie.framework.ui.component;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.ui.Layout;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.List;
import java.util.Map;

/**
 * A {@link MarieComponent} that owns children and arranges them via a {@link Layout}. This is the
 * only place composition lives — a future Window is just a Container, not a special case.
 */
@ApiStatus.Experimental
public interface Container extends MarieComponent {

    List<MarieComponent> children();

    void addChild(MarieComponent child);

    void removeChild(MarieComponent child);

    Layout layout();

    @Override
    default void render(RenderContext context, Bounds bounds) {
        Map<MarieComponent, Bounds> childBounds = layout().computeBounds(bounds, children());
        for (MarieComponent child : children()) {
            if (!child.visibilityRule().isVisible()) {
                continue;
            }
            Bounds resolved = childBounds.get(child);
            if (resolved != null) {
                child.render(context, resolved);
            }
        }
    }
}
