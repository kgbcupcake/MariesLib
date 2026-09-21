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
 * Picker for one {@link ColorSlot}: a round hue/saturation disc (hue is the angle, saturation the distance
 * from the centre) with a brightness slider under it, the current color as a swatch and {@code #RRGGBB}
 * readout (read-only), and a Reset button. The disc and slider are drawn from {@code fillRect} cells (the
 * only fill primitive {@code RenderContext} has), with the cell size growing with the disc so the fill count
 * stays roughly constant at any window size; a thin outline keeps the stepped edge clean.
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

    private enum Drag { NONE, DISC, VALUE }

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
    private Bounds valueBounds = new Bounds(0, 0, 0, 0);
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

        int sliderH = Math.max(6, Math.round(9 * scale));
        int sliderY = footY - pad - sliderH;
        valueBounds = new Bounds(left, sliderY, width, sliderH);

        int midTop = top + rowH + pad;
        int midHeight = sliderY - pad - midTop;
        int diameter = Math.min(width, midHeight);
        if (diameter < 12) {
            outerRadius = 0;
            valueBounds = new Bounds(0, 0, 0, 0);
            return;
        }
        centerX = left + width / 2;
        centerY = midTop + midHeight / 2;
        outerRadius = diameter / 2;
        int cell = Math.max(1, (int) Math.ceil(diameter / 48.0));
        drawDisc(context, cell);
        drawValueSlider(context, valueBounds, cell);
        int markerSize = Math.max(5, Math.round(7 * scale));
        double angle = hue * 2 * Math.PI;
        drawMarker(context, centerX + (int) Math.round(Math.cos(angle) * sat * outerRadius),
                centerY + (int) Math.round(Math.sin(angle) * sat * outerRadius), markerSize, hsvToRgb(hue, sat, 1f));
        int knobX = valueBounds.x() + Math.round(val * (valueBounds.width() - 1));
        drawMarker(context, knobX, valueBounds.y() + valueBounds.height() / 2, Math.min(markerSize, sliderH + 2), hsvToRgb(hue, sat, val));
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
        OptionStyle.drawPillButton(context, resetBounds.x(), resetBounds.y(), resetBounds.width(), resetBounds.height(),
                resetCaption.get(), textScale, true);
    }

    /** Hue around, saturation outward, at full brightness; the cells whose centre is inside the radius, then a 1px outline. */
    private void drawDisc(RenderContext context, int cell) {
        double radiusSq = (double) outerRadius * outerRadius;
        for (int gy = centerY - outerRadius; gy < centerY + outerRadius; gy += cell) {
            for (int gx = centerX - outerRadius; gx < centerX + outerRadius; gx += cell) {
                double dx = gx + cell / 2.0 - centerX;
                double dy = gy + cell / 2.0 - centerY;
                double distSq = dx * dx + dy * dy;
                if (distSq > radiusSq) {
                    continue;
                }
                context.fillRect(gx, gy, cell, cell,
                        0xFF000000 | hsvToRgb(hueAt(dx, dy), (float) Math.min(1.0, Math.sqrt(distSq) / outerRadius), 1f));
            }
        }
        int outline = context.theme().color(ThemeKey.BORDER);
        int points = Math.min(240, Math.max(48, outerRadius * 6));
        int dot = Math.max(1, (int) Math.ceil(outerRadius / 60.0));
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            context.fillRect(centerX + (int) Math.round(Math.cos(a) * outerRadius) - dot,
                    centerY + (int) Math.round(Math.sin(a) * outerRadius) - dot, dot, dot, outline);
        }
    }

    /** Black to the current hue/saturation at full brightness, left to right, in a rounded outline. */
    private void drawValueSlider(RenderContext context, Bounds b, int cell) {
        int inset = 1;
        int step = Math.max(1, cell);
        for (int i = 0; i < b.width() - 2 * inset; i += step) {
            int w = Math.min(step, b.width() - 2 * inset - i);
            float v = (i + w / 2f) / (b.width() - 2 * inset);
            context.fillRect(b.x() + inset + i, b.y() + inset, w, b.height() - 2 * inset, 0xFF000000 | hsvToRgb(hue, sat, v));
        }
        context.drawRoundedRect(b.x(), b.y(), b.width(), b.height(), 1, Math.max(1, b.height() / 2), 0x00000000,
                context.theme().color(ThemeKey.BORDER));
    }

    /** A round knob filled with {@code rgb}, white ring inside a black one, centred on {@code (x, y)}. */
    private static void drawMarker(RenderContext context, int x, int y, int size, int rgb) {
        int half = size / 2;
        context.drawRoundedRect(x - half - 1, y - half - 1, size + 2, size + 2, 1, (size + 2) / 2, 0xFF000000 | rgb, 0xFF000000);
        context.drawRoundedRect(x - half, y - half, size, size, 1, size / 2, 0xFF000000 | rgb, 0xFFFFFFFF);
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
        if (Math.sqrt(dx * dx + dy * dy) <= outerRadius) {
            drag = Drag.DISC;
        } else if (valueBounds.contains(mx, my)) {
            drag = Drag.VALUE;
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

    /** Updates hue and saturation (disc) or brightness (slider) from the pointer, and calls the setter if the RGB value changed. */
    private void apply(double mouseX, double mouseY) {
        if (drag == Drag.DISC) {
            double dx = mouseX - centerX;
            double dy = mouseY - centerY;
            hue = hueAt(dx, dy);
            sat = clamp01((float) (Math.sqrt(dx * dx + dy * dy) / outerRadius));
        } else {
            val = clamp01((float) ((mouseX - valueBounds.x()) / Math.max(1, valueBounds.width() - 1)));
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
