package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.ThemeKey;
import dev.marie.framework.ui.edit.EditModeController;
import dev.marie.framework.ui.geometry.Anchor;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * A small vertical light switch for turning edit mode on and off: a light (green on, red off) above a
 * housing whose lever sits up when on, down when off. It anchors to a corner of any window's bounds
 * (re-read every frame, so it follows a window that moves) and reads its state live, so it can't drift
 * from what it controls.
 *
 * <pre>{@code
 * EditModeToggle toggle = EditModeToggle.forController(() -> windowBounds, controller).build();
 * // render:  toggle.render(context, mouseX, mouseY);
 * // click:   if (toggle.mouseClicked(mouseX, mouseY, button)) return true;
 * }</pre>
 */
@ApiStatus.Experimental
public final class EditModeToggle {

    private static final int LIGHT_SIZE = 6;
    private static final int LIGHT_GAP = 1;
    private static final int HOUSING_W = 10;
    private static final int HOUSING_H = 16;
    private static final int LEVER_INSET = 2;
    private static final int LEVER_H = 6;

    private final Builder b;

    private EditModeToggle(Builder builder) {
        this.b = builder;
    }

    /** A toggle anchored to {@code window} showing {@code on} and running {@code onToggle} when clicked. */
    public static Builder builder(Supplier<Bounds> window, BooleanSupplier on, Runnable onToggle) {
        return new Builder(window, on, onToggle);
    }

    /** A toggle that enters/exits {@code controller}'s edit mode and shows whether it's active. */
    public static Builder forController(Supplier<Bounds> window, EditModeController controller) {
        return new Builder(window, controller::isActive, () -> {
            if (controller.isActive()) {
                controller.exit();
            } else {
                controller.enter();
            }
        });
    }

    public boolean isOn() {
        return b.on.getAsBoolean();
    }

    /** The whole widget (light and housing) on screen this frame. */
    public Bounds bounds() {
        Bounds window = b.window.get();
        double s = b.scale.getAsDouble();
        int w = px(HOUSING_W, s);
        int h = px(LIGHT_SIZE + LIGHT_GAP + HOUSING_H, s);
        int ox = px(b.offsetX, s);
        int oy = px(b.offsetY, s);
        int x = switch (b.anchor) {
            case TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT -> window.x() + ox;
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> window.x() + (window.width() - w) / 2 + ox;
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> window.x() + window.width() - ox - w;
        };
        int y = switch (b.anchor) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> window.y() + oy;
            case CENTER_LEFT, CENTER, CENTER_RIGHT -> window.y() + (window.height() - h) / 2 + oy;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> window.y() + window.height() - oy - h;
        };
        return new Bounds(x, y, w, h);
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return b.visible.getAsBoolean() && bounds().contains((int) mouseX, (int) mouseY);
    }

    /** Flips the switch on a left click over it; true if the click was consumed. */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        b.onToggle.run();
        return true;
    }

    public void render(RenderContext context, int mouseX, int mouseY) {
        if (!b.visible.getAsBoolean()) {
            return;
        }
        double s = b.scale.getAsDouble();
        Bounds all = bounds();
        int lightSize = px(LIGHT_SIZE, s);
        Bounds light = new Bounds(all.x() + (all.width() - lightSize) / 2, all.y(), lightSize, lightSize);
        int housingTop = all.y() + lightSize + px(LIGHT_GAP, s);
        Bounds housing = new Bounds(all.x(), housingTop, all.width(), all.y() + all.height() - housingTop);
        boolean on = isOn();

        context.fillRect(light.x(), light.y(), light.width(), light.height(), b.lightBorder.getAsInt());
        context.fillRect(light.x() + 1, light.y() + 1, Math.max(0, light.width() - 2), Math.max(0, light.height() - 2),
                on ? b.onColor.getAsInt() : b.offColor.getAsInt());

        context.fillRect(housing.x(), housing.y(), housing.width(), housing.height(), b.housing.getAsInt());
        boolean hovered = all.contains(mouseX, mouseY);
        int border = b.housingBorder != null ? b.housingBorder.getAsInt()
                : context.theme().color(hovered ? ThemeKey.BORDER_HOVER : ThemeKey.BORDER);
        context.drawBorder(housing.x(), housing.y(), housing.width(), housing.height(), 1, border);

        int inset = px(LEVER_INSET, s);
        int leverH = px(LEVER_H, s);
        int leverY = on ? housing.y() + inset : housing.y() + housing.height() - inset - leverH;
        context.fillRect(housing.x() + inset, leverY, Math.max(1, housing.width() - 2 * inset), leverH, b.lever.getAsInt());
    }

    private static int px(int local, double scale) {
        return Math.max(1, (int) Math.round(local * scale));
    }

    /** Builder for {@link EditModeToggle}. Every color is re-read each frame. */
    @ApiStatus.Experimental
    public static final class Builder {

        private final Supplier<Bounds> window;
        private final BooleanSupplier on;
        private final Runnable onToggle;
        private Anchor anchor = Anchor.TOP_RIGHT;
        private int offsetX = 6;
        private int offsetY = 1;
        private DoubleSupplier scale = () -> 1.0d;
        private BooleanSupplier visible = () -> true;
        private IntSupplier onColor = () -> 0xFF3FBF3F;
        private IntSupplier offColor = () -> 0xFFBF3F3F;
        private IntSupplier lightBorder = () -> 0xFF101010;
        private IntSupplier housing = () -> 0xFF2A2A2A;
        private IntSupplier lever = () -> 0xFFBDBDBD;
        private IntSupplier housingBorder;

        private Builder(Supplier<Bounds> window, BooleanSupplier on, Runnable onToggle) {
            this.window = Objects.requireNonNull(window, "window");
            this.on = Objects.requireNonNull(on, "on");
            this.onToggle = Objects.requireNonNull(onToggle, "onToggle");
        }

        /** Which corner/edge of the window it sits at, {@code (offsetX, offsetY)} unscaled pixels inward. Top-right, (6, 1) by default. */
        public Builder anchor(Anchor anchor, int offsetX, int offsetY) {
            this.anchor = anchor;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            return this;
        }

        /** Size multiplier, e.g. the panel's own scale. */
        public Builder scale(DoubleSupplier scale) {
            this.scale = scale;
            return this;
        }

        /** Hidden (and unclickable) while this is false, e.g. while the window is minimized. */
        public Builder visibleWhen(BooleanSupplier visible) {
            this.visible = visible;
            return this;
        }

        public Builder lightColors(IntSupplier on, IntSupplier off) {
            this.onColor = on;
            this.offColor = off;
            return this;
        }

        /** Housing fill, lever, and the light's 1px frame. */
        public Builder bodyColors(IntSupplier housing, IntSupplier lever, IntSupplier lightBorder) {
            this.housing = housing;
            this.lever = lever;
            this.lightBorder = lightBorder;
            return this;
        }

        /** The housing's border; by default the theme's border, brighter on hover. */
        public Builder housingBorderColor(IntSupplier border) {
            this.housingBorder = border;
            return this;
        }

        public EditModeToggle build() {
            return new EditModeToggle(this);
        }
    }
}
