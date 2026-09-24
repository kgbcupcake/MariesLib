package dev.marie.framework.ui.layout;

import dev.marie.framework.ui.Layout;
import dev.marie.framework.ui.component.Constraint;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Positions each child independently within the available area, anchored to a corner/edge/center
 * and offset by its own margin — siblings never affect each other's placement. This is the shape
 * a free-floating, individually draggable panel needs (HUD panels, dialog windows): each owns its
 * anchor plus a pixel offset, nothing else. Generalizes the anchor-switch pattern an earlier,
 * consumer-specific HUD layout implementation hand-rolled.
 */
public final class FreeformLayout implements Layout {

    @Override
    public Map<MarieComponent, Bounds> computeBounds(Bounds available, List<MarieComponent> children) {
        Map<MarieComponent, Bounds> result = new LinkedHashMap<>();
        for (MarieComponent c : children) {
            Constraint k = c.constraint();
            int w = k.expandHorizontal() ? available.width() : clamp(k.preferredSize().width(), k.minSize().width(), k.maxSize().width());
            int h = k.expandVertical() ? available.height() : clamp(k.preferredSize().height(), k.minSize().height(), k.maxSize().height());

            int x = switch (k.anchor()) {
                case TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT -> available.x() + k.margin().left();
                case TOP_CENTER, CENTER, BOTTOM_CENTER ->
                        available.x() + (available.width() - w) / 2 + k.margin().left() - k.margin().right();
                case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> available.x() + available.width() - w - k.margin().right();
            };
            int y = switch (k.anchor()) {
                case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> available.y() + k.margin().top();
                case CENTER_LEFT, CENTER, CENTER_RIGHT ->
                        available.y() + (available.height() - h) / 2 + k.margin().top() - k.margin().bottom();
                case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> available.y() + available.height() - h - k.margin().bottom();
            };

            result.put(c, new Bounds(x, y, w, h));
        }
        return result;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
