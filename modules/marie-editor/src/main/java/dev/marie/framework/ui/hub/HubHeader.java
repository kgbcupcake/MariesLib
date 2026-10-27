package dev.marie.framework.ui.hub;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.ThemeKey;
import dev.marie.framework.ui.geometry.Bounds;
import dev.marie.framework.ui.toolbox.OptionStyle;

/**
 * {@link HubPanel}'s window chrome, with the single centered title split into two: the selected
 * entry's own name on the left and a static "Editor" label on the right, separated by a vertical
 * divider — plus a minimize button in the corner.
 */
@ApiStatus.Internal
final class HubHeader {

    static final int HEIGHT = 16;
    private static final int PADDING = 6;
    private static final int MINIMIZE_BUTTON_SIZE = 9;
    /** Gap either side of the divider between the two labels. */
    private static final int DIVIDER_GAP = 6;

    /** Where the header drew its minimize button, and the content area below the header. */
    record Layout(Bounds minimizeButton, Bounds content) {}

    private HubHeader() {}

    static Layout draw(RenderContext context, Bounds bounds, int background, int border, int titleColor, int labelColor, int accent,
                       String editorLabel, String dynamicLabel) {
        context.drawRoundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 1, background, border);

        Bounds minimize = new Bounds(bounds.x() + bounds.width() - PADDING - MINIMIZE_BUTTON_SIZE,
                bounds.y() + (HEIGHT - MINIMIZE_BUTTON_SIZE) / 2, MINIMIZE_BUTTON_SIZE, MINIMIZE_BUTTON_SIZE);
        context.drawRoundedRect(minimize.x(), minimize.y(), minimize.width(), minimize.height(), 1,
                (0x40 << 24) | (accent & 0x00FFFFFF), accent);
        context.drawText("-", minimize.x() + 3, minimize.y(), accent, 0.8f);

        int editorWidth = context.textWidth(editorLabel, 1f);
        int editorX = minimize.x() - PADDING - editorWidth;
        int textY = bounds.y() + (HEIGHT - 8) / 2;
        context.drawText(editorLabel, editorX, textY, labelColor, 1f);

        int dividerX = editorX - DIVIDER_GAP;
        context.fillRect(dividerX, bounds.y() + 3, 1, HEIGHT - 6, border);

        int dynamicMaxWidth = Math.max(0, dividerX - DIVIDER_GAP - (bounds.x() + PADDING));
        context.drawText(OptionStyle.fit(context, dynamicLabel, 1f, dynamicMaxWidth), bounds.x() + PADDING, textY, titleColor, 1f);

        int dividerY = bounds.y() + HEIGHT;
        context.fillRect(bounds.x() + 1, dividerY, Math.max(0, bounds.width() - 2), 1, border);
        return new Layout(minimize, new Bounds(bounds.x(), dividerY + 1, bounds.width(), Math.max(0, bounds.height() - HEIGHT - 1)));
    }
}
