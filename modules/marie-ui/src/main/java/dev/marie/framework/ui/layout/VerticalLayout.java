package dev.marie.framework.ui.layout;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.ui.Layout;
import dev.marie.framework.ui.component.Constraint;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Arranges children top-to-bottom; expandVertical children share leftover height equally. */
@ApiStatus.Experimental
public final class VerticalLayout implements Layout {

    private final int gap;

    public VerticalLayout(int gap) {
        this.gap = gap;
    }

    @Override
    public Map<MarieComponent, Bounds> computeBounds(Bounds available, List<MarieComponent> children) {
        Map<MarieComponent, Bounds> result = new LinkedHashMap<>();
        if (children.isEmpty()) {
            return result;
        }

        int fixedHeight = 0;
        int expandCount = 0;
        for (MarieComponent c : children) {
            Constraint k = c.constraint();
            fixedHeight += clampedHeight(k);
            if (k.expandVertical()) {
                expandCount++;
            }
        }
        int totalGap = gap * (children.size() - 1);
        int leftover = Math.max(0, available.height() - fixedHeight - totalGap);
        int extraPerExpand = expandCount > 0 ? leftover / expandCount : 0;

        int cursorY = available.y();
        for (MarieComponent c : children) {
            Constraint k = c.constraint();
            int h = clampedHeight(k) + (k.expandVertical() ? extraPerExpand : 0);
            int w = k.expandHorizontal() ? available.width() : Math.min(available.width(), k.preferredSize().width());
            int x = available.x() + horizontalOffset(k, available.width(), w);
            result.put(c, new Bounds(x, cursorY, w, h));
            cursorY += h + gap;
        }
        return result;
    }

    private static int clampedHeight(Constraint k) {
        int h = k.preferredSize().height();
        h = Math.max(h, k.minSize().height());
        h = Math.min(h, k.maxSize().height());
        return h;
    }

    private static int horizontalOffset(Constraint k, int availableWidth, int childWidth) {
        return switch (k.anchor()) {
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> (availableWidth - childWidth) / 2;
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> availableWidth - childWidth;
            default -> 0;
        };
    }
}
