package dev.marie.framework.ui.scaleconfig;

import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.geometry.Bounds;
import dev.marie.framework.ui.toolbox.ModuleOptionRows;
import dev.marie.framework.ui.toolbox.OptionLayout;

/**
 * Hosting/forwarding for the content shown in a {@link ScaleConfigPanel} window: builds the default
 * content, sizes the window to it, draws it below the window header, and forwards input to it. Every
 * entry is hosted — an entry without caller-supplied content gets the default Text Scale / Padding /
 * Move Text and Icons rows, built from the same toolbox widgets a custom panel uses.
 */
final class HostedWindow {

    private HostedWindow() {}

    /** The default window content for an entry with none of its own: Text Scale, Padding and a Move Text and Icons toggle over {@code persistence}. */
    static MarieComponent defaultContent(PersistenceProvider persistence, String componentId) {
        OptionLayout layout = new OptionLayout(componentId);
        layout.addTab("");
        ModuleOptionRows.addTextScale(layout, persistence, componentId);
        ModuleOptionRows.addPadding(layout, persistence, componentId);
        ModuleOptionRows.addMoveTextToggle(layout, persistence, componentId);
        return layout;
    }

    /** The area below the window header where the content is drawn. */
    private static Bounds body(Bounds window) {
        int top = window.y() + ScaleConfigPanel.CARD_PADDING + ScaleConfigPanel.HEADER_HEIGHT + ScaleConfigPanel.ROW_GAP;
        return new Bounds(window.x() + ScaleConfigPanel.CARD_PADDING, top,
                window.width() - 2 * ScaleConfigPanel.CARD_PADDING,
                Math.max(0, window.y() + window.height() - ScaleConfigPanel.CARD_PADDING - top));
    }

    /**
     * {@code window} grown to the content's preferred height, for the size a window <em>first opens at</em>
     * (the render-time clamp keeps it on screen). It is deliberately not a minimum: the player can resize the
     * window smaller afterwards and the content scrolls instead of being forced open.
     */
    static Bounds fit(MarieComponent content, Bounds window) {
        int needed = ScaleConfigPanel.CARD_PADDING + ScaleConfigPanel.HEADER_HEIGHT + ScaleConfigPanel.ROW_GAP
                + content.constraint().preferredSize().height() + ScaleConfigPanel.CARD_PADDING;
        int height = Math.max(window.height(), needed);
        return new Bounds(window.x(), window.y(), window.width(), height);
    }

    /** Draws the content clipped to the window body. */
    static void render(RenderContext context, MarieComponent content, Bounds window) {
        Bounds body = body(window);
        context.pushClip(body.x(), body.y(), body.width(), body.height());
        try {
            content.render(context, body);
        } finally {
            context.popClip();
        }
    }

    static boolean mouseClicked(MarieComponent content, Bounds window, double mouseX, double mouseY) {
        return body(window).contains((int) mouseX, (int) mouseY) && content.mouseClicked(mouseX, mouseY, 0);
    }

    static boolean mouseDragged(MarieComponent content, double mouseX, double mouseY, int button) {
        return content.mouseDragged(mouseX, mouseY, button, 0, 0);
    }

    static boolean mouseReleased(MarieComponent content, double mouseX, double mouseY, int button) {
        return content.mouseReleased(mouseX, mouseY, button);
    }

    static boolean mouseScrolled(MarieComponent content, Bounds window, double mouseX, double mouseY, double scrollX, double scrollY) {
        return body(window).contains((int) mouseX, (int) mouseY) && content.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
