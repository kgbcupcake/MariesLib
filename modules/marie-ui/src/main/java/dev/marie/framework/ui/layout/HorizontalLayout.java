package dev.marie.framework.ui.layout;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.ui.Layout;
import dev.marie.framework.ui.component.Constraint;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Arranges children left-to-right; expandHorizontal children share leftover width equally. */
@ApiStatus.Experimental
public final class HorizontalLayout implements Layout {

    private final int gap;

    public HorizontalLayout(int gap) {
        this.gap = gap;
    }

    @Override
    public Map<MarieComponent, Bounds> computeBounds(Bounds available, List<MarieComponent> children) {
        Map<MarieComponent, Bounds> result = new LinkedHashMap<>();
        if (children.isEmpty()) {
            return result;
        }

        int fixedWidth = 0;
        int expandCount = 0;
        for (MarieComponent c : children) {
            Constraint k = c.constraint();
            fixedWidth += clampedWidth(k);
            if (k.expandHorizontal()) {
                expandCount++;
            }
        }
        int totalGap = gap * (children.size() - 1);
        int leftover = Math.max(0, available.width() - fixedWidth - totalGap);
        int extraPerExpand = expandCount > 0 ? leftover / expandCount : 0;

        int cursorX = available.x();
        for (MarieComponent c : children) {
            Constraint k = c.constraint();
            int w = clampedWidth(k) + (k.expandHorizontal() ? extraPerExpand : 0);
            int h = k.expandVertical() ? available.height() : Math.min(available.height(), k.preferredSize().height());
            int y = available.y() + verticalOffset(k, available.height(), h);
            result.put(c, new Bounds(cursorX, y, w, h));
            cursorX += w + gap;
        }
        return result;
    }

    private static int clampedWidth(Constraint k) {
        int w = k.preferredSize().width();
        w = Math.max(w, k.minSize().width());
        w = Math.min(w, k.maxSize().width());
        return w;
    }

    private static int verticalOffset(Constraint k, int availableHeight, int childHeight) {
        return switch (k.anchor()) {
            case CENTER_LEFT, CENTER, CENTER_RIGHT -> (availableHeight - childHeight) / 2;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> availableHeight - childHeight;
            default -> 0;
        };
    }
}
