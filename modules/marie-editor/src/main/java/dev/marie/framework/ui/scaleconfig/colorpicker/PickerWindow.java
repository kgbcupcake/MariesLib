package dev.marie.framework.ui.scaleconfig.colorpicker;

import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.ThemeKey;
import dev.marie.framework.ui.component.Constraint;
import dev.marie.framework.ui.drag.DraggableResizable;
import dev.marie.framework.ui.geometry.Anchor;
import dev.marie.framework.ui.geometry.Bounds;
import dev.marie.framework.ui.geometry.Insets;
import dev.marie.framework.ui.geometry.Size;
import dev.marie.framework.ui.toolbox.OptionStyle;
import dev.marie.framework.ui.toolbox.colorpicker.ColorPicker;
import dev.marie.framework.ui.toolbox.colorpicker.ColorSlot;
import dev.marie.framework.ui.toolbox.OptionStyle;

/**
 * The single color-picker window a {@link ScaleConfigPanel} can show: a sibling of the module windows,
 * drawn at panel level rather than inside a module's body, so the host window's clip does not apply.
 * It hosts one {@link ColorPicker}; clicking another slot retargets the same window (keeping its size and
 * position) and retitles it. Bounds live in memory only.
 *
 * <p>It closes itself lazily, from {@link #beginFrame}: when its owner module is no longer the open
 * one (collapsed, or another module opened), or when no frame has been drawn for {@link #STALE_MS} (the
 * host stopped rendering the panel, e.g. its screen closed). Reopening the owner never restores it.
 */
@dev.marie.framework.api.ApiStatus.Internal
public final class PickerWindow {

    /** No render for this long means the host stopped drawing the panel; long enough that a lag spike mid-drag doesn't close it. */
    public static final long STALE_MS = 500;

    private static final int DEFAULT_WIDTH = 150;
    private static final int DEFAULT_HEIGHT = 190;
    private static final int MIN_WIDTH = 110;
    private static final int MIN_HEIGHT = 130;
    private static final int TITLE_HEIGHT = 16;
    private static final int PAD = 5;
    private static final int CLOSE_SIZE = 9;
    private static final int GAP = 6;

    private final ColorPicker picker = new ColorPicker(() -> net.minecraft.network.chat.Component.translatable("config.marieslib.colorpicker.reset").getString());
    private final DraggableResizable drag;
    private boolean open;
    private String ownerId;
    private String title = "";
    private Bounds bounds;
    private long lastFrameMs = System.currentTimeMillis();

    public PickerWindow() {
        Constraint constraint = new Constraint(new Size(DEFAULT_WIDTH, DEFAULT_HEIGHT), new Size(MIN_WIDTH, MIN_HEIGHT),
                Size.UNBOUNDED, false, false, true, true, Anchor.TOP_LEFT, Insets.NONE, Insets.NONE);
        // No SnapRegistry id: snapping stays off.
        this.drag = new DraggableResizable(picker, constraint, (target, committed) -> bounds = committed);
    }

    /** Opens (or retargets) the window on {@code slot}; a first-ever open is placed beside {@code ownerWindow}. */
    public void show(ColorSlot slot, String owner, String windowTitle, Bounds ownerWindow, Bounds screen) {
        picker.setSlot(slot);
        ownerId = owner;
        title = windowTitle;
        open = true;
        lastFrameMs = System.currentTimeMillis();
        bounds = bounds == null ? beside(ownerWindow, screen) : onScreen(bounds, screen);
    }

    /** Closes the window if its owner is no longer the open module ({@code openOwnerId}, or null if none) or the host stopped rendering. */
    public void beginFrame(String openOwnerId) {
        long now = System.currentTimeMillis();
        if (open && (!ownerId.equals(openOwnerId) || now - lastFrameMs > STALE_MS)) {
            close();
        }
        lastFrameMs = now;
    }

    /** Closes the window, telling the picker so the slot can drop any uncommitted preview. */
    private void close() {
        open = false;
        picker.cancel();
    }

    public boolean isLive() {
        return open && System.currentTimeMillis() - lastFrameMs <= STALE_MS;
    }

    /** Draws now, or — when a host has begun a {@link PickerLayer} pass — after every target, so nothing paints over it. */
    public void render(RenderContext context, Bounds screen) {
        if (!open) {
            return;
        }
        if (!drag.isDragging() && !drag.isResizing()) {
            bounds = onScreen(bounds, screen);
        }
        drag.setParentBounds(screen);
        if (!PickerLayer.defer(this, () -> draw(context))) {
            draw(context);
        }
    }

    private void draw(RenderContext context) {
        Bounds window = bounds;
        context.drawRoundedRect(window.x(), window.y(), window.width(), window.height(), 1,
                context.theme().color(ThemeKey.PANEL_BACKGROUND), context.theme().color(ThemeKey.BORDER));
        int titleWidth = window.width() - 3 * PAD - CLOSE_SIZE;
        context.drawText(OptionStyle.fit(context, title, 0.85f, titleWidth), window.x() + PAD, window.y() + PAD,
                context.theme().color(ThemeKey.TEXT_PRIMARY), 0.85f);
        Bounds close = closeBounds(window);
        context.drawRoundedRect(close.x(), close.y(), close.width(), close.height(), 1,
                (0x40 << 24) | (OptionStyle.ACCENT & 0x00FFFFFF), OptionStyle.ACCENT);
        context.drawText("x", close.x() + 2, close.y() + 1, OptionStyle.ACCENT, 0.65f);
        Bounds body = body(window);
        context.pushClip(body.x(), body.y(), body.width(), body.height());
        try {
            picker.render(context, body);
        } finally {
            context.popClip();
        }
        context.drawResizeHandle(window.x() + window.width() - DraggableResizable.RESIZE_HANDLE_SIZE,
                window.y() + window.height() - DraggableResizable.RESIZE_HANDLE_SIZE, false, drag.isResizing());
    }

    /** True for anything on the window (so a host treats it as hit), after handling the press. Content gets the click first, so a drag on the wheel never moves the window. */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (!isLive() || !bounds.contains(mx, my)) {
            return false;
        }
        if (button != 0) {
            return true;
        }
        if (closeBounds(bounds).contains(mx, my)) {
            close();
        } else if (!(body(bounds).contains(mx, my) && picker.mouseClicked(mouseX, mouseY, 0))) {
            drag.mouseClicked(mx, my, bounds);
        }
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (!open) {
            return false;
        }
        if (drag.isDragging() || drag.isResizing()) {
            Bounds preview = drag.mouseDragged((int) mouseX, (int) mouseY);
            if (preview != null) {
                bounds = preview;
            }
            return true;
        }
        return picker.mouseDragged(mouseX, mouseY, button, 0, 0);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!open) {
            return false;
        }
        if (drag.isDragging() || drag.isResizing()) {
            drag.mouseReleased((int) mouseX, (int) mouseY);
            return true;
        }
        return picker.mouseReleased(mouseX, mouseY, button);
    }

    public boolean mouseScrolled(double mouseX, double mouseY) {
        return isLive() && bounds.contains((int) mouseX, (int) mouseY);
    }

    private static Bounds closeBounds(Bounds window) {
        return new Bounds(window.x() + window.width() - PAD - CLOSE_SIZE, window.y() + (TITLE_HEIGHT - CLOSE_SIZE) / 2 + 1,
                CLOSE_SIZE, CLOSE_SIZE);
    }

    private static Bounds body(Bounds window) {
        return new Bounds(window.x() + PAD, window.y() + TITLE_HEIGHT, Math.max(0, window.width() - 2 * PAD),
                Math.max(0, window.height() - TITLE_HEIGHT - PAD));
    }

    /** Default placement: to the right of the owner window, else its left, else pinned on screen. */
    private static Bounds beside(Bounds owner, Bounds screen) {
        int x = owner.x() + owner.width() + GAP;
        if (x + DEFAULT_WIDTH > screen.x() + screen.width()) {
            x = owner.x() - GAP - DEFAULT_WIDTH;
        }
        return onScreen(new Bounds(x, owner.y(), DEFAULT_WIDTH, DEFAULT_HEIGHT), screen);
    }

    /** {@code b} at or above the minimum size and fully inside {@code screen}. */
    private static Bounds onScreen(Bounds b, Bounds screen) {
        int width = Math.min(Math.max(b.width(), MIN_WIDTH), Math.max(MIN_WIDTH, screen.width()));
        int height = Math.min(Math.max(b.height(), MIN_HEIGHT), Math.max(MIN_HEIGHT, screen.height()));
        int x = Math.max(screen.x(), Math.min(b.x(), screen.x() + screen.width() - width));
        int y = Math.max(screen.y(), Math.min(b.y(), screen.y() + screen.height() - height));
        return new Bounds(x, y, width, height);
    }
}
