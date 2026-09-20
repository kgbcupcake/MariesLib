package dev.marie.framework.ui.toolbox.colorpicker;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.ThemeKey;
import dev.marie.framework.ui.component.Constraint;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.geometry.Bounds;
import dev.marie.framework.ui.toolbox.OptionStyle;
import java.util.function.Supplier;

/**
 * Picker for one {@link ColorSlot}: a hue ring with a saturation/brightness square inside it, the
 * current color as a swatch and {@code #RRGGBB} readout (read-only), and a Reset button. The ring and
 * square are drawn from {@code fillRect} cells (the only fill primitive {@code RenderContext} has), with
 * the cell size growing with the wheel so the fill count stays roughly constant at any window size.
 *
 * <p>Everything is laid out from the {@link Bounds} it is handed each frame, so it scales
 * proportionally with a resizable host window and never draws outside them. It knows nothing about
 * windows: a host calls {@link #setSlot} to point it at a slot, and the slot's setter runs live on every
 * drag tick where the RGB value changes, with {@code onCommit} once on release. RGB only — the setter
 * never sees alpha.
 */
@ApiStatus.Internal
public final class ColorPicker implements MarieComponent {

    private static final float REF_WIDTH = 150f;
    private static final float REF_HEIGHT = 176f;

    private enum Drag { NONE, RING, SQUARE }

    private final Supplier<String> resetCaption;
    private ColorSlot slot;
    private float hue;
    private float sat;
    private float val;
    private int lastRgb;
    private Drag drag = Drag.NONE;

    private int centerX;
    private int centerY;
    private int outerRadius;
    private int innerRadius;
    private int squareX;
    private int squareY;
    private int squareSide;
    private Bounds resetBounds = new Bounds(0, 0, 0, 0);

    /** {@code resetCaption} supplies the (localized) text of the Reset button, read each frame. */
    public ColorPicker(Supplier<String> resetCaption) {
        this.resetCaption = resetCaption;
    }

    /** Points the picker at {@code newSlot}, dropping any gesture in progress. */
    public void setSlot(ColorSlot newSlot) {
        if (slot != null && slot != newSlot) {
            slot.onCancel().run();
        }
        this.slot = newSlot;
        this.drag = Drag.NONE;
        syncFrom(newSlot.rgb());
    }

    /** Ends editing of the current slot without committing: drops a gesture in progress and runs the slot's {@code onCancel}. */
    public void cancel() {
        drag = Drag.NONE;
        if (slot != null) {
            slot.onCancel().run();
        }
    }

    public ColorSlot slot() {
        return slot;
    }

    @Override
    public String id() {
        return "color-picker";
    }

    @Override
    public Constraint constraint() {
        return Constraint.preferred((int) REF_WIDTH, (int) REF_HEIGHT);
    }

    @Override
    public void render(RenderContext context, Bounds bounds) {
        if (slot == null) {
            return;
        }
        int rgb = slot.rgb();
        if (drag == Drag.NONE && rgb != lastRgb) {
            syncFrom(rgb);
        }
        float scale = Math.max(0.3f, Math.min(bounds.width() / REF_WIDTH, bounds.height() / REF_HEIGHT));
        int pad = Math.max(2, Math.round(4 * scale));
        int rowH = Math.max(9, Math.round(12 * scale));
        float textScale = Math.max(0.5f, 0.8f * scale);
        int left = bounds.x() + pad;
        int width = Math.max(0, bounds.width() - 2 * pad);
        int top = bounds.y() + pad;

        drawReadout(context, left, top, rowH, textScale, rgb);
        int footY = bounds.y() + bounds.height() - pad - rowH;
        drawReset(context, left, footY, width, rowH, scale, textScale);

        int midTop = top + rowH + pad;
        int midHeight = footY - pad - midTop;
        int diameter = Math.min(width, midHeight);
        if (diameter < 12) {
            outerRadius = 0;
            return;
        }
        centerX = left + width / 2;
        centerY = midTop + midHeight / 2;
        outerRadius = diameter / 2;
        innerRadius = outerRadius - Math.max(3, Math.round(outerRadius * 0.26f));
        squareSide = Math.round(innerRadius * 1.3f);
        squareX = centerX - squareSide / 2;
        squareY = centerY - squareSide / 2;
        int cell = Math.max(1, (int) Math.ceil(diameter / 50.0));
        drawRing(context, cell);
        drawSquare(context, cell);
        int markerSize = Math.max(3, Math.round(5 * scale));
        double angle = hue * 2 * Math.PI;
        int ringMid = (outerRadius + innerRadius) / 2;
        drawMarker(context, centerX + (int) Math.round(Math.cos(angle) * ringMid),
                centerY + (int) Math.round(Math.sin(angle) * ringMid), markerSize);
        drawMarker(context, squareX + Math.round(sat * squareSide), squareY + Math.round((1 - val) * squareSide), markerSize);
    }

    private void drawReadout(RenderContext context, int x, int y, int rowH, float textScale, int rgb) {
        int swatch = rowH - 2;
        context.fillRect(x, y + 1, swatch, swatch, 0xFF000000 | rgb);
        context.drawBorder(x, y + 1, swatch, swatch, 1, context.theme().color(ThemeKey.BORDER));
        String hex = HexColors.formatRgbHex(rgb);
        context.drawText(hex, x + swatch + 4, y + Math.max(0, (rowH - Math.round(8 * textScale)) / 2),
                context.theme().color(ThemeKey.TEXT_PRIMARY), textScale);
    }

    private void drawReset(RenderContext context, int x, int y, int width, int rowH, float scale, float textScale) {
        int buttonWidth = Math.min(width, Math.round(60 * scale));
        resetBounds = new Bounds(x + (width - buttonWidth) / 2, y, buttonWidth, rowH);
        context.drawRoundedRect(resetBounds.x(), resetBounds.y(), resetBounds.width(), resetBounds.height(), 1,
                (0x40 << 24) | (OptionStyle.ACCENT & 0x00FFFFFF), OptionStyle.ACCENT);
        String caption = OptionStyle.fit(context, resetCaption.get(),
                textScale, buttonWidth - 4);
        context.drawText(caption, resetBounds.x() + (buttonWidth - context.textWidth(caption, textScale)) / 2,
                resetBounds.y() + Math.max(0, (rowH - Math.round(8 * textScale)) / 2), OptionStyle.ACCENT, textScale);
    }

    private void drawRing(RenderContext context, int cell) {
        double outerSq = (double) outerRadius * outerRadius;
        double innerSq = (double) innerRadius * innerRadius;
        for (int gy = centerY - outerRadius; gy < centerY + outerRadius; gy += cell) {
            for (int gx = centerX - outerRadius; gx < centerX + outerRadius; gx += cell) {
                double dx = gx + cell / 2.0 - centerX;
                double dy = gy + cell / 2.0 - centerY;
                double distSq = dx * dx + dy * dy;
                if (distSq > outerSq || distSq < innerSq) {
                    continue;
                }
                context.fillRect(gx, gy, cell, cell, 0xFF000000 | hsvToRgb(hueAt(dx, dy), 1f, 1f));
            }
        }
    }

    private void drawSquare(RenderContext context, int cell) {
        for (int j = 0; j < squareSide; j += cell) {
            int h = Math.min(cell, squareSide - j);
            float v = 1f - (j + h / 2f) / squareSide;
            for (int i = 0; i < squareSide; i += cell) {
                int w = Math.min(cell, squareSide - i);
                context.fillRect(squareX + i, squareY + j, w, h, 0xFF000000 | hsvToRgb(hue, (i + w / 2f) / squareSide, v));
            }
        }
    }

    private static void drawMarker(RenderContext context, int x, int y, int size) {
        int half = size / 2;
        context.drawBorder(x - half - 1, y - half - 1, size + 2, size + 2, 1, 0xFF000000);
        context.drawBorder(x - half, y - half, size, size, 1, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || slot == null) {
            return false;
        }
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (resetBounds.contains(mx, my)) {
            syncFrom(slot.defaultValue() & 0xFFFFFF);
            slot.setter().accept(lastRgb);
            slot.onCommit().run();
            return true;
        }
        if (outerRadius == 0) {
            return false;
        }
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist <= outerRadius && dist >= innerRadius) {
            drag = Drag.RING;
        } else if (mx >= squareX && my >= squareY && mx < squareX + squareSide && my < squareY + squareSide) {
            drag = Drag.SQUARE;
        } else {
            return false;
        }
        apply(mouseX, mouseY);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (drag == Drag.NONE) {
            return false;
        }
        apply(mouseX, mouseY);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (drag == Drag.NONE) {
            return false;
        }
        drag = Drag.NONE;
        slot.onCommit().run();
        return true;
    }

    /** Updates hue or saturation/brightness from the pointer, and calls the setter if the RGB value changed. */
    private void apply(double mouseX, double mouseY) {
        if (drag == Drag.RING) {
            hue = hueAt(mouseX - centerX, mouseY - centerY);
        } else {
            sat = clamp01((float) ((mouseX - squareX) / squareSide));
            val = 1f - clamp01((float) ((mouseY - squareY) / squareSide));
        }
        int rgb = hsvToRgb(hue, sat, val);
        if (rgb != lastRgb) {
            lastRgb = rgb;
            slot.setter().accept(rgb);
        }
    }

    /** Adopts {@code rgb} as the current value; hue is kept when the color has none (gray/black). */
    private void syncFrom(int rgb) {
        lastRgb = rgb;
        float[] hsv = rgbToHsv(rgb);
        if (hsv[1] > 0f && hsv[2] > 0f) {
            hue = hsv[0];
        }
        sat = hsv[1];
        val = hsv[2];
    }

    private static float hueAt(double dx, double dy) {
        double turns = Math.atan2(dy, dx) / (2 * Math.PI);
        return (float) (turns < 0 ? turns + 1 : turns);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /** {@code 0xRRGGBB} for hue/saturation/value each in {@code [0, 1]} (hue 1.0 wraps to 0). */
    static int hsvToRgb(float h, float s, float v) {
        float sector = (h - (float) Math.floor(h)) * 6f;
        int i = (int) sector;
        float f = sector - i;
        float p = v * (1 - s);
        float q = v * (1 - s * f);
        float t = v * (1 - s * (1 - f));
        float r;
        float g;
        float b;
        switch (i) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    /** {hue, saturation, value} in {@code [0, 1]} for {@code 0xRRGGBB}. */
    static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float delta = max - Math.min(r, Math.min(g, b));
        float h;
        if (delta == 0f) {
            h = 0f;
        } else if (max == r) {
            h = ((g - b) / delta) / 6f;
        } else if (max == g) {
            h = (2f + (b - r) / delta) / 6f;
        } else {
            h = (4f + (r - g) / delta) / 6f;
        }
        return new float[]{h < 0 ? h + 1 : h, max == 0f ? 0f : delta / max, max};
    }
}
