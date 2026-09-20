package dev.marie.framework.ui.toolbox;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.ThemeKey;

/** Shared sizes/colors and small text helpers for the toolbox widgets. */
@ApiStatus.Internal
public final class OptionStyle {

    /** Cyan accent used for selected tabs, slider fills and "on" values. */
    public static final int ACCENT = 0xFF5DA9E9;
    static final float TEXT_SCALE = 0.8f;
    static final int LABEL_HEIGHT = 10;
    static final int LABEL_TRACK_GAP = 2;
    static final int SLIDER_HEIGHT = 7;
    static final int ROW_GAP = 5;
    static final int TAB_HEIGHT = 12;
    static final int VALUE_GAP = 6;
    /** Width a panel asks for; hosts may give it more. */
    static final int PREFERRED_WIDTH = 212;

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

    /** Label on the left, value right-aligned to {@code x + width}; the label is truncated rather than overlapping the value. */
    public static void drawLabelAndValue(RenderContext context, String label, String value, int x, int y, int width,
                                  int labelColor, int valueColor) {
        int valueWidth = context.textWidth(value, TEXT_SCALE);
        context.drawText(fit(context, label, TEXT_SCALE, Math.max(0, width - valueWidth - VALUE_GAP)), x, y, labelColor, TEXT_SCALE);
        context.drawText(value, x + width - valueWidth, y, valueColor, TEXT_SCALE);
    }
}
