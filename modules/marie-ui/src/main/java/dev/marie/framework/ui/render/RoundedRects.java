package dev.marie.framework.ui.render;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;

/**
 * Stepped-corner rounded rectangle drawn purely with {@link RenderContext#fillRect}. Each corner is a
 * quarter circle of {@code radius} pixels approximated one pixel row at a time; the border is the band
 * of {@code thickness} pixels between the outer shape and the same shape inset by {@code thickness}
 * (with radius reduced to match), and border and fill never overlap, so translucent colors don't
 * double-blend along the edge.
 */
@ApiStatus.Internal
public final class RoundedRects {

    private RoundedRects() {}

    public static void draw(RenderContext ctx, int x, int y, int w, int h, int thickness, int radius, int fill, int border) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int t = Math.max(0, Math.min(thickness, Math.min(w, h) / 2));
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        int band = Math.max(r, t);
        if (h <= 2 * band) {
            for (int row = 0; row < h; row++) {
                drawRow(ctx, x, y, w, h, t, r, row, fill, border);
            }
            return;
        }
        for (int d = 0; d < band; d++) {
            drawRow(ctx, x, y, w, h, t, r, d, fill, border);
            drawRow(ctx, x, y, w, h, t, r, h - 1 - d, fill, border);
        }
        int midY = y + band;
        int midH = h - 2 * band;
        if (t > 0) {
            ctx.fillRect(x, midY, t, midH, border);
            ctx.fillRect(x + w - t, midY, t, midH, border);
        }
        ctx.fillRect(x + t, midY, w - 2 * t, midH, fill);
    }

    /** Draws one pixel row of the shape: left border, fill, right border. */
    private static void drawRow(RenderContext ctx, int x, int y, int w, int h, int t, int r, int row, int fill, int border) {
        int d = Math.min(row, h - 1 - row);
        int outer = cornerInset(r, d);
        int py = y + row;
        int innerRow = d - t;
        if (innerRow < 0) {
            ctx.fillRect(x + outer, py, w - 2 * outer, 1, border);
            return;
        }
        int inner = t + cornerInset(Math.max(0, r - t), innerRow);
        inner = Math.max(inner, outer);
        if (inner > outer) {
            ctx.fillRect(x + outer, py, inner - outer, 1, border);
            ctx.fillRect(x + w - inner, py, inner - outer, 1, border);
        }
        ctx.fillRect(x + inner, py, w - 2 * inner, 1, fill);
    }

    /** Pixels cut from the left (and right) edge on row {@code d} counted from the shape's top/bottom; 0 once past the corner. */
    private static int cornerInset(int radius, int d) {
        if (d >= radius) {
            return 0;
        }
        int dy = radius - d - 1;
        return radius - (int) Math.floor(Math.sqrt((double) radius * radius - (double) dy * dy));
    }
}
