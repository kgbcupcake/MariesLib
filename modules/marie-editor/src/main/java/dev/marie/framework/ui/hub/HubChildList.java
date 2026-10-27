package dev.marie.framework.ui.hub;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.ArrayList;
import java.util.List;

/** A selected {@link HubGroupEntry}'s child rows in {@link HubPanel}'s content pane, with their own scroll. */
@ApiStatus.Internal
final class HubChildList {

    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 2;

    private record Hit(HubChildEntry child, Bounds bounds) {}

    private final List<Hit> hits = new ArrayList<>();
    /** Reset whenever the selection changes, since it's a different list at a different length each time. */
    private int scrollOffset;

    void draw(RenderContext context, Bounds body, HubGroupEntry group, int background, int border, int textColor, int padding) {
        hits.clear();
        List<HubChildEntry> children = group.children().get();
        int rowStep = ROW_HEIGHT + ROW_GAP;
        int totalHeight = children.isEmpty() ? 0 : children.size() * rowStep - ROW_GAP;
        int maxScroll = Math.max(0, totalHeight - body.height());
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        int y = body.y() - scrollOffset;
        for (HubChildEntry child : children) {
            Bounds row = new Bounds(body.x(), y, body.width(), ROW_HEIGHT);
            if (row.y() + row.height() > body.y() && row.y() < body.y() + body.height()) {
                context.drawRoundedRect(row.x(), row.y(), row.width(), row.height(), 1, background, border);
                context.drawText(child.label().getString(), row.x() + padding, row.y() + (ROW_HEIGHT - 8) / 2, textColor, 0.9f);
                hits.add(new Hit(child, row));
            }
            y += rowStep;
        }
    }

    void clearHits() {
        hits.clear();
    }

    HubChildEntry childAt(int mx, int my) {
        for (Hit hit : hits) {
            if (hit.bounds().contains(mx, my)) {
                return hit.child();
            }
        }
        return null;
    }

    void resetScroll() {
        scrollOffset = 0;
    }

    void scroll(double scrollY, int step) {
        scrollOffset -= (int) Math.signum(scrollY) * step;
    }
}
