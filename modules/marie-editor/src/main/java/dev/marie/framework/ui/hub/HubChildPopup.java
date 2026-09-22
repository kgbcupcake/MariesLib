package dev.marie.framework.ui.hub;

import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.ThemeKey;
import dev.marie.framework.ui.component.Constraint;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.drag.DraggableResizable;
import dev.marie.framework.ui.geometry.Anchor;
import dev.marie.framework.ui.geometry.Bounds;
import dev.marie.framework.ui.geometry.Insets;
import dev.marie.framework.ui.geometry.Size;

/**
 * The single popup window a {@link HubPanel} can show for a {@link HubGroupEntry}'s selected child —
 * modeled directly on {@code ScaleConfigPanel}'s color-picker popup ({@code PickerWindow}), generalized
 * to host an arbitrary {@link MarieComponent} instead of a color picker: a small draggable/resizable
 * window (title, close "x", clipped body, resize handle) drawn above the hub. Clicking another child
 * retargets the same window (keeping its size/position) and swaps its content/title; it closes itself
 * lazily from {@link #beginFrame} when its owner group is no longer the hub's selected entry, or when
 * no frame has been drawn for {@link #STALE_MS} (the host stopped rendering the hub).
 */
final class HubChildPopup {

    static final long STALE_MS = 500;

    private static final int DEFAULT_WIDTH = 240;
    private static final int DEFAULT_HEIGHT = 260;
    private static final int MIN_WIDTH = 180;
    private static final int MIN_HEIGHT = 160;
    private static final int TITLE_HEIGHT = 16;
    private static final int PAD = 5;
    private static final int CLOSE_SIZE = 9;
    private static final int GAP = 6;

    private final DraggableResizable drag;
    private boolean open;
    private String ownerGroupId;
    private String title = "";
    private MarieComponent content;
    private Bounds bounds;
    private long lastFrameMs = System.currentTimeMillis();

    HubChildPopup() {
        Constraint constraint = new Constraint(new Size(DEFAULT_WIDTH, DEFAULT_HEIGHT), new Size(MIN_WIDTH, MIN_HEIGHT),
                Size.UNBOUNDED, false, false, true, true, Anchor.TOP_LEFT, Insets.NONE, Insets.NONE);
        MarieComponent target = new MarieComponent() {
            @Override
            public String id() {
                return "marieslib.hub.childpopup";
            }

            @Override
            public Constraint constraint() {
                return constraint;
            }

            @Override
            public void render(RenderContext context, Bounds bounds) {
                // unused: HubChildPopup renders itself directly, this target only carries identity/constraint
            }
        };
        this.drag = new DraggableResizable(target, constraint, (t, committed) -> bounds = committed);
    }

    /** Opens (or retargets) the popup on {@code child}; a first-ever open is placed beside {@code ownerWindow}. */
    void show(HubChildEntry child, String ownerGroupId, Bounds ownerWindow, Bounds screen) {
        this.content = child.content();
        this.title = child.label().getString();
        this.ownerGroupId = ownerGroupId;
        this.open = true;
        this.lastFrameMs = System.currentTimeMillis();
        this.bounds = bounds == null ? beside(ownerWindow, screen) : onScreen(bounds, screen);
    }

    /** Closes the popup if its owner group is no longer the hub's selected entry ({@code openGroupId}, or null if none/a leaf), or the host stopped rendering. */
    void beginFrame(String openGroupId) {
        long now = System.currentTimeMillis();
        if (open && (openGroupId == null || !ownerGroupId.equals(openGroupId) || now - lastFrameMs > STALE_MS)) {
            close();
        }
        lastFrameMs = now;
    }

    private void close() {
        open = false;
    }

    boolean isLive() {
        return open && System.currentTimeMillis() - lastFrameMs <= STALE_MS;
    }

    void render(RenderContext context, Bounds screen) {
        if (!open) {
            return;
        }
        if (!drag.isDragging() && !drag.isResizing()) {
            bounds = onScreen(bounds, screen);
        }
        drag.setParentBounds(screen);
        draw(context);
    }

    private void draw(RenderContext context) {
        Bounds window = bounds;
        context.drawRoundedRect(window.x(), window.y(), window.width(), window.height(), 1,
                context.theme().color(ThemeKey.PANEL_BACKGROUND), context.theme().color(ThemeKey.BORDER));
        int titleWidth = window.width() - 3 * PAD - CLOSE_SIZE;
        context.drawText(title, window.x() + PAD, window.y() + PAD, context.theme().color(ThemeKey.TEXT_PRIMARY), 0.85f);
        Bounds close = closeBounds(window);
        int accent = context.theme().color(ThemeKey.BORDER_HOVER);
        context.drawRoundedRect(close.x(), close.y(), close.width(), close.height(), 1,
                (0x40 << 24) | (accent & 0x00FFFFFF), accent);
        context.drawText("x", close.x() + 2, close.y() + 1, accent, 0.65f);
        Bounds body = body(window);
        context.pushClip(body.x(), body.y(), body.width(), body.height());
        try {
            content.render(context, body);
        } finally {
            context.popClip();
        }
        context.drawResizeHandle(window.x() + window.width() - DraggableResizable.RESIZE_HANDLE_SIZE,
                window.y() + window.height() - DraggableResizable.RESIZE_HANDLE_SIZE, false, drag.isResizing());
    }

    /** True for anything on the popup (so the hub treats it as hit and stops looking at what's underneath), after handling the press. Content gets the click first, so a click on a widget never starts a drag. */
    boolean mouseClicked(double mouseX, double mouseY, int button) {
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
        } else if (!(body(bounds).contains(mx, my) && content.mouseClicked(mouseX, mouseY, 0))) {
            drag.mouseClicked(mx, my, bounds);
        }
        return true;
    }

    boolean mouseDragged(double mouseX, double mouseY, int button) {
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
        return content.mouseDragged(mouseX, mouseY, button, 0, 0);
    }

    boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!open) {
            return false;
        }
        if (drag.isDragging() || drag.isResizing()) {
            drag.mouseReleased((int) mouseX, (int) mouseY);
            return true;
        }
        return content.mouseReleased(mouseX, mouseY, button);
    }

    boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return isLive() && body(bounds).contains((int) mouseX, (int) mouseY) && content.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
