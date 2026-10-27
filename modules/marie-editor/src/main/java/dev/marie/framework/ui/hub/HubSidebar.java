package dev.marie.framework.ui.hub;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.Theme;
import dev.marie.framework.ui.ThemeKey;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.ArrayList;
import java.util.List;

/** {@link HubPanel}'s scrollable sidebar list: drawing, row hit-testing and its own scroll offset. */
@ApiStatus.Internal
final class HubSidebar {

    static final int WIDTH = 120;
    private static final int ROW_HEIGHT = 20;
    private static final int SELECTION_BAR_WIDTH = 3;
    private static final int ROW_INSET = 8;

    private record Hit(HubSidebarEntry entry, Bounds bounds) {}

    private final List<Hit> hits = new ArrayList<>();
    private Bounds lastBounds = new Bounds(0, 0, 0, 0);
    /** Vertical scroll in pixels, clamped to the overflow on every draw — needed once the hub is shrunk small enough that not every entry fits. */
    private int scrollOffset;

    /**
     * {@code textColor} is null for the theme's primary/secondary text; a hub with its own title color
     * passes it so the sidebar picks up the override like the rest of its chrome.
     */
    void draw(RenderContext context, Bounds content, List<HubSidebarEntry> entries, String selectedId,
              int accent, Integer textColor, int padding) {
        hits.clear();
        Theme theme = context.theme();
        int areaY = content.y();
        int areaHeight = content.height();
        lastBounds = new Bounds(content.x(), areaY, WIDTH, areaHeight);

        int totalHeight = entries.size() * ROW_HEIGHT;
        int maxScroll = Math.max(0, totalHeight + padding - areaHeight);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        context.pushClip(content.x(), areaY, WIDTH, areaHeight);
        try {
            int y = areaY + padding - scrollOffset;
            for (HubSidebarEntry entry : entries) {
                if (y + ROW_HEIGHT > areaY && y < areaY + areaHeight) {
                    boolean selected = entry.id().equals(selectedId);
                    if (selected) {
                        context.fillRect(content.x(), y, WIDTH, ROW_HEIGHT, theme.color(ThemeKey.HANDLE_BACKGROUND));
                        context.fillRect(content.x(), y, SELECTION_BAR_WIDTH, ROW_HEIGHT, accent);
                    }
                    int color = textColor != null ? textColor : theme.color(selected ? ThemeKey.TEXT_PRIMARY : ThemeKey.TEXT_SECONDARY);
                    context.drawText(entry.label().getString(), content.x() + ROW_INSET, y + ROW_HEIGHT / 2 - 4, color, 1f);
                    hits.add(new Hit(entry, new Bounds(content.x(), y, WIDTH, ROW_HEIGHT)));
                }
                y += ROW_HEIGHT;
            }
        } finally {
            context.popClip();
        }
    }

    /** The entry whose row was clicked, or null. */
    HubSidebarEntry entryAt(int mx, int my) {
        for (Hit hit : hits) {
            if (hit.bounds().contains(mx, my)) {
                return hit.entry();
            }
        }
        return null;
    }

    boolean scroll(int mx, int my, double scrollY, int step) {
        if (scrollY == 0 || !lastBounds.contains(mx, my)) {
            return false;
        }
        scrollOffset -= (int) Math.signum(scrollY) * step;
        return true;
    }
}
