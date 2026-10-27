package dev.marie.framework.ui.hub;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.geometry.Bounds;
import dev.marie.framework.ui.toolbox.OptionStyle;

/**
 * {@link HubPanel}'s minimized strip: a static label (it identifies the hub, not whatever was last open)
 * plus a restore affordance. A plain click restores the hub; click-and-drag repositions the strip,
 * the same distinction dragging the header makes while expanded.
 */
@ApiStatus.Internal
final class HubCollapsedStrip {

    static final int WIDTH = 180;
    static final int HEIGHT = 20;
    private static final int PADDING = 6;

    private boolean dragging;
    private boolean moved;
    private int offsetX;
    private int offsetY;

    /** Draws the strip at the hub's position and returns its bounds. */
    Bounds draw(RenderContext context, Bounds panel, int background, int border, int titleColor, int accent, String label) {
        Bounds strip = new Bounds(panel.x(), panel.y(), Math.min(WIDTH, panel.width()), HEIGHT);
        context.drawRoundedRect(strip.x(), strip.y(), strip.width(), strip.height(), 1, background, border);
        int maxWidth = Math.max(0, strip.width() - 2 * PADDING - 10);
        context.drawText(OptionStyle.fit(context, label, 1f, maxWidth),
                strip.x() + PADDING, strip.y() + (strip.height() - 8) / 2, titleColor, 1f);
        context.drawText("+", strip.x() + strip.width() - PADDING - 6, strip.y() + (strip.height() - 8) / 2, accent, 0.8f);
        return strip;
    }

    boolean press(int mx, int my, Bounds strip, Bounds panel) {
        if (!strip.contains(mx, my)) {
            return false;
        }
        dragging = true;
        moved = false;
        offsetX = mx - panel.x();
        offsetY = my - panel.y();
        return true;
    }

    /** The hub's new position while the strip is dragged, or null if it isn't. */
    Bounds drag(int mx, int my, Bounds panel) {
        if (!dragging) {
            return null;
        }
        moved = true;
        return new Bounds(mx - offsetX, my - offsetY, panel.width(), panel.height());
    }

    boolean isDragging() {
        return dragging;
    }

    /** Ends a press; true if it was a drag (keep minimized), false if a plain click (restore). */
    boolean release() {
        dragging = false;
        return moved;
    }
}
