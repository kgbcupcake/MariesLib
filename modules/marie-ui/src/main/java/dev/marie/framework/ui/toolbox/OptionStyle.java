package dev.marie.framework.ui.toolbox;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.ThemeKey;

/** Shared sizes/colors and small text helpers for the toolbox widgets. */
@ApiStatus.Internal
public final class OptionStyle {

    /** Cyan accent used for selected tabs, slider fills and "on" values. */
    public static final int ACCENT = 0xFF5DA9E9;
    public static final float TEXT_SCALE = 0.8f;
    /** Smallest scale a label/value pair shrinks to before the label is truncated instead. */
    static final float MIN_TEXT_SCALE = 0.6f;
    private static final float SCALE_STEP = 0.05f;
    static final int LABEL_HEIGHT = 10;
    static final int LABEL_TRACK_GAP = 2;
    static final int SLIDER_HEIGHT = 9;
    /** Side of the square arrow buttons at the ends of a slider. */
    static final int ARROW_BUTTON = 9;
    static final int ARROW_GAP = 2;
    /** Padding around the rows inside the row area (none: rows sit directly on the window's own background). */
    static final int PANEL_PAD = 0;
    /** Soft grey surface behind grouped rows, and the subtle edge around it. */
    static final int PANEL_FILL = 0x1AFFFFFF;
    static final int PANEL_EDGE = 0x26FFFFFF;
    static final int ROW_GAP = 6;
    static final int TAB_HEIGHT = 12;
    static final int VALUE_GAP = 6;
    /** Width a panel asks for; hosts may give it more. */
    public static final int PREFERRED_WIDTH = 212;

    private static final String ELLIPSIS = "..";

    private OptionStyle() {}

    public static int labelColor(RenderContext context, boolean enabled) {
        int color = context.theme().color(ThemeKey.TEXT_SECONDARY);
        return enabled ? color : dimmed(color);
    }

    public static int accentColor(boolean enabled) {
        return enabled ? ACCENT : dimmed(ACCENT);
    }

    /** {@code argb} at half its alpha, for disabled rows. */
    public static int dimmed(int argb) {
        int alpha = (argb >>> 24) / 2;
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /** {@code text} cut with a trailing ellipsis so it renders no wider than {@code maxWidth}. */
    public static String fit(RenderContext context, String text, float scale, int maxWidth) {
        if (context.textWidth(text, scale) <= maxWidth) {
            return text;
        }
        String cut = text;
        while (!cut.isEmpty() && context.textWidth(cut + ELLIPSIS, scale) > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + ELLIPSIS;
    }

    /**
     * Label on the left, value right-aligned to {@code x + width}. When the pair does not fit at
     * {@link #TEXT_SCALE} the text shrinks step by step (down to {@link #MIN_TEXT_SCALE}) so narrow
     * windows stay readable; only past that floor is the label truncated rather than overlapping the value.
     */
    public static void drawLabelAndValue(RenderContext context, String label, String value, int x, int y, int width,
                                  int labelColor, int valueColor) {
        float scale = TEXT_SCALE;
        while (scale - SCALE_STEP >= MIN_TEXT_SCALE - 1e-4f
                && context.textWidth(label, scale) + context.textWidth(value, scale) + VALUE_GAP > width) {
            scale -= SCALE_STEP;
        }
        int valueWidth = context.textWidth(value, scale);
        // Shrunk text is shorter, so drop it by the difference to keep it centered in the label line.
        int textY = y + Math.round((TEXT_SCALE - scale) * 4);
        context.drawText(fit(context, label, scale, Math.max(0, width - valueWidth - VALUE_GAP)), x, textY, labelColor, scale);
        context.drawText(value, x + width - valueWidth, textY, valueColor, scale);
    }

    /** A rounded, accent-outlined button with its caption centred and truncated to fit: the one button look every widget shares. */
    public static void drawPillButton(RenderContext context, int x, int y, int width, int height, String caption, float textScale, boolean enabled) {
        int accent = accentColor(enabled);
        context.drawRoundedRect(x, y, width, height, 1, height / 2, (0x40 << 24) | (ACCENT & 0x00FFFFFF), accent);
        String text = fit(context, caption, textScale, width - 6);
        context.drawText(text, x + (width - context.textWidth(text, textScale)) / 2,
                y + Math.max(0, (height - Math.round(8 * textScale)) / 2), accent, textScale);
    }
}
