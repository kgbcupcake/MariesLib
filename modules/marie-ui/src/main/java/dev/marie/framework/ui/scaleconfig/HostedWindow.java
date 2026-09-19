package dev.marie.framework.ui.scaleconfig;

import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.geometry.Bounds;

/**
 * Hosting/forwarding for a {@link ScaleConfigEntry}'s optional content: sizes the open window to it,
 * draws it below the window header, and forwards input to it. Every method is a no-op returning
 * false/unchanged for an entry without content, so {@link ScaleConfigPanel} calls these
 * unconditionally and keeps its built-in rows as the fallback.
 */
final class HostedWindow {

    /** Hit region for the built-in rows of a hosted window, which must never respond. */
    static final Bounds NO_HIT = new Bounds(0, 0, 0, 0);

    private HostedWindow() {}

    /** The area below the window header where the content is drawn. */
    private static Bounds body(Bounds window) {
        int top = window.y() + ScaleConfigPanel.CARD_PADDING + ScaleConfigPanel.HEADER_HEIGHT + ScaleConfigPanel.ROW_GAP;
        return new Bounds(window.x() + ScaleConfigPanel.CARD_PADDING, top,
                window.width() - 2 * ScaleConfigPanel.CARD_PADDING,
                Math.max(0, window.y() + window.height() - ScaleConfigPanel.CARD_PADDING - top));
    }

    /** {@code window} grown, if needed, to fit the content's preferred height (capped at the window maximum; the content scrolls past that). */
    static Bounds fit(ScaleConfigEntry entry, Bounds window) {
        MarieComponent content = entry.content();
        if (content == null) {
            return window;
        }
        int needed = ScaleConfigPanel.CARD_PADDING + ScaleConfigPanel.HEADER_HEIGHT + ScaleConfigPanel.ROW_GAP
                + content.constraint().preferredSize().height() + ScaleConfigPanel.CARD_PADDING;
        int height = Math.max(window.height(), Math.min(needed, ScaleConfigPanel.MAX_WINDOW_HEIGHT));
        return new Bounds(window.x(), window.y(), window.width(), height);
    }

    /** Draws the content clipped to the window body; false if the entry has none. */
    static boolean render(RenderContext context, ScaleConfigEntry entry, Bounds window) {
        MarieComponent content = entry.content();
        if (content == null) {
            return false;
        }
        Bounds body = body(window);
        context.pushClip(body.x(), body.y(), body.width(), body.height());
        try {
            content.render(context, body);
        } finally {
            context.popClip();
        }
        return true;
    }

    static boolean mouseClicked(ScaleConfigEntry entry, Bounds window, double mouseX, double mouseY) {
        MarieComponent content = entry.content();
        return content != null && body(window).contains((int) mouseX, (int) mouseY) && content.mouseClicked(mouseX, mouseY, 0);
    }

    static boolean mouseDragged(ScaleConfigEntry entry, double mouseX, double mouseY, int button) {
        MarieComponent content = entry.content();
        return content != null && content.mouseDragged(mouseX, mouseY, button, 0, 0);
    }

    static boolean mouseReleased(ScaleConfigEntry entry, double mouseX, double mouseY, int button) {
        MarieComponent content = entry.content();
        return content != null && content.mouseReleased(mouseX, mouseY, button);
    }

    static boolean mouseScrolled(ScaleConfigEntry entry, Bounds window, double mouseX, double mouseY, double scrollX, double scrollY) {
        MarieComponent content = entry.content();
        return content != null && body(window).contains((int) mouseX, (int) mouseY)
                && content.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
