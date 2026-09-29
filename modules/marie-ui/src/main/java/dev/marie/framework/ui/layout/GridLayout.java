package dev.marie.framework.ui.layout;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.ui.Layout;
import dev.marie.framework.ui.component.Constraint;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Arranges children into a fixed-column grid; each row's height is the tallest cell in that row. */
@ApiStatus.Experimental
public final class GridLayout implements Layout {

    private final int columns;
    private final int hGap;
    private final int vGap;

    public GridLayout(int columns, int hGap, int vGap) {
        if (columns < 1) {
            throw new IllegalArgumentException("columns must be >= 1");
        }
        this.columns = columns;
        this.hGap = hGap;
        this.vGap = vGap;
    }

    @Override
    public Map<MarieComponent, Bounds> computeBounds(Bounds available, List<MarieComponent> children) {
        Map<MarieComponent, Bounds> result = new LinkedHashMap<>();
        if (children.isEmpty()) {
            return result;
        }

        int cellW = (available.width() - hGap * (columns - 1)) / columns;
        int colIndex = 0;
        int rowY = available.y();
        int rowHeight = 0;

        for (int i = 0; i < children.size(); i++) {
            MarieComponent c = children.get(i);
            Constraint k = c.constraint();
            int h = clampedHeight(k);
            rowHeight = Math.max(rowHeight, h);

            int x = available.x() + colIndex * (cellW + hGap);
            result.put(c, new Bounds(x, rowY, cellW, h));

            colIndex++;
            if (colIndex >= columns || i == children.size() - 1) {
                colIndex = 0;
                rowY += rowHeight + vGap;
                rowHeight = 0;
            }
        }
        return result;
    }

    private static int clampedHeight(Constraint k) {
        int h = k.preferredSize().height();
        h = Math.max(h, k.minSize().height());
        h = Math.min(h, k.maxSize().height());
        return h;
    }
}
