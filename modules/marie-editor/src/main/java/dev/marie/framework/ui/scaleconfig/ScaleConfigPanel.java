package dev.marie.framework.ui.scaleconfig;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.ThemeKey;
import dev.marie.framework.ui.component.ComponentState;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.drag.DraggableResizable;
import dev.marie.framework.ui.scaleconfig.colorpicker.PickerWindow;
import dev.marie.framework.ui.toolbox.colorpicker.ColorSlot;
import dev.marie.framework.ui.toolbox.OptionLayout;
import dev.marie.framework.ui.geometry.Anchor;
import dev.marie.framework.ui.geometry.Bounds;
import dev.marie.framework.ui.modulesettings.MoveFlags;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Embeddable dashboard-style overlay: a stack of collapsed tabs, one per entry, that each open into a
 * single draggable/resizable window hosting that entry's content (by default Text Scale, Padding and a
 * Move Text and Icons toggle over the entry's persisted scales; see {@link ScaleConfigEntry#withContent}
 * for custom content). Not a Screen; a host screen owns/renders/forwards input to an instance only
 * while visible.
 */
@ApiStatus.Experimental
public final class ScaleConfigPanel {

    /** Cycling fallback palette for entries with no explicit {@link ScaleConfigEntry#accentColor()}. */
    private static final int[] ACCENT_PALETTE = {0xFF5DA9E9, 0xFFE98F5D, 0xFF7ED9A6, 0xFFD97ED9, 0xFFE9DE5D};

    static final int CARD_WIDTH = 224;
    private static final int CARD_GAP = 6;
    static final int CARD_PADDING = 6;
    static final int HEADER_HEIGHT = 14;
    static final int ROW_GAP = 5;

    /** Header plus the default window's three rows (two sliders and a toggle); no window can be smaller. */
    static final int CARD_HEIGHT = 89;

    /** Collapsed-tab row height. */
    private static final int TAB_HEIGHT = 18;

    /** Small header control that collapses the open window back to a tab. */
    private static final int COLLAPSE_BUTTON_SIZE = 9;

    private final List<ScaleConfigEntry> entries;
    private final PersistenceProvider persistence;
    private final Anchor anchor;
    private final WindowStates windows;

    /** Each entry's window content, keyed by componentId — its own, or the default built once per panel. */
    private final Map<String, MarieComponent> contents = new HashMap<>();

    /** This frame's rendered collapsed-tab hit regions, rebuilt every render() pass, for click hit-testing. */
    private final List<TabLayout> lastTabLayout = new ArrayList<>();

    /** This frame's rendered open window (at most one), or {@code null} if every entry is collapsed. */
    private WindowLayout lastOpenWindow;

    /** This frame's rendered collapsed-tab stack bounding box, for the outside-panel early-out. */
    private Bounds lastPanelBounds = new Bounds(0, 0, 0, 0);

    /** The full screen {@link Bounds} passed into the most recent {@link #render}, used to clamp drag/resize gestures and to place a newly-opened window near the anchor. */
    private Bounds lastRenderBounds = new Bounds(0, 0, 0, 0);

    /** The one color-picker window this panel can show; see {@link PickerWindow}. */
    private final PickerWindow picker = new PickerWindow();

    private String draggingWindowComponentId;
    private int dragGrabOffsetX;
    private int dragGrabOffsetY;

    private String resizingWindowComponentId;
    private int resizeOriginX;
    private int resizeOriginY;

    public ScaleConfigPanel(List<ScaleConfigEntry> entries, PersistenceProvider persistence, Anchor anchor) {
        this.entries = entries;
        this.persistence = persistence;
        this.anchor = anchor;
        this.windows = new WindowStates(persistence);
        for (ScaleConfigEntry entry : entries) {
            contents.put(entry.componentId(), entry.content() != null
                    ? entry.content()
                    : HostedWindow.defaultContent(persistence, entry.componentId()));
            if (contents.get(entry.componentId()) instanceof OptionLayout layout) {
                layout.setColorSlotListener(slot -> showPicker(entry, slot));
            }
        }
    }

    public void render(RenderContext context, Bounds bounds) {
        lastTabLayout.clear();
        lastOpenWindow = null;
        lastRenderBounds = bounds;

        // Only the first entry found with a non-collapsed window state is honored as "open" — any
        // others (e.g. from a hand-edited save file) render as tabs instead of a second window.
        ScaleConfigEntry openEntry = null;
        ComponentState openState = null;
        int openIndex = -1;
        List<ScaleConfigEntry> collapsedEntries = new ArrayList<>();
        List<Integer> collapsedIndices = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            ScaleConfigEntry entry = entries.get(i);
            windows.register(entry.componentId());
            ComponentState windowState = windows.load(entry.componentId());
            if (openEntry == null && !windowState.collapsed()) {
                openEntry = entry;
                openState = windowState;
                openIndex = i;
            } else {
                collapsedEntries.add(entry);
                collapsedIndices.add(i);
            }
        }

        picker.beginFrame(openEntry == null ? null : openEntry.componentId());
        int panelHeight = collapsedEntries.size() * TAB_HEIGHT + Math.max(0, collapsedEntries.size() - 1) * CARD_GAP;
        int panelX = AnchorStack.anchorX(bounds, anchor);
        int stackOffset = AnchorStack.claimStackOffset(this, anchor, panelHeight);
        int panelY = AnchorStack.anchorY(bounds, anchor, panelHeight, stackOffset);
        lastPanelBounds = new Bounds(panelX, panelY, CARD_WIDTH, panelHeight);

        int y = panelY;
        for (int i = 0; i < collapsedEntries.size(); i++) {
            ScaleConfigEntry entry = collapsedEntries.get(i);
            Bounds tabBounds = new Bounds(panelX, y, CARD_WIDTH, TAB_HEIGHT);
            drawTab(context, entry, accentFor(entry, collapsedIndices.get(i)), tabBounds);
            lastTabLayout.add(new TabLayout(entry, tabBounds));
            y += TAB_HEIGHT + CARD_GAP;
        }

        if (openEntry != null) {
            Bounds raw = new Bounds(openState.x(), openState.y(), openState.width(), openState.height());
            lastOpenWindow = layoutWindow(openEntry, accentFor(openEntry, openIndex), WindowStates.clamp(raw, bounds));
            drawWindow(context, lastOpenWindow);
        }
        picker.render(context, bounds);
    }

    /** A color slot in {@code entry}'s content was clicked: open or retarget the picker window on it. */
    private void showPicker(ScaleConfigEntry entry, ColorSlot slot) {
        if (lastOpenWindow != null) {
            picker.show(slot, entry.componentId(), slot.label() + " - " + entry.label().getString(),
                    lastOpenWindow.windowBounds(), lastRenderBounds);
        }
    }

    private int accentFor(ScaleConfigEntry entry, int index) {
        return entry.accentColor() != null ? entry.accentColor() : ACCENT_PALETTE[index % ACCENT_PALETTE.length];
    }

    private void drawTab(RenderContext context, ScaleConfigEntry entry, int accent, Bounds tabBounds) {
        int panelBg = context.theme().color(ThemeKey.PANEL_BACKGROUND);
        int border = context.theme().color(ThemeKey.BORDER);
        context.drawRoundedRect(tabBounds.x(), tabBounds.y(), tabBounds.width(), tabBounds.height(), 1, panelBg, border);
        context.fillRect(tabBounds.x() + 1, tabBounds.y() + 1, 2, tabBounds.height() - 2, accent);
        context.drawText(entry.label().getString(), tabBounds.x() + CARD_PADDING, tabBounds.y() + (tabBounds.height() - 8) / 2, accent, 0.85f);
    }

    private WindowLayout layoutWindow(ScaleConfigEntry entry, int accent, Bounds windowBounds) {
        Bounds headerBounds = new Bounds(windowBounds.x(), windowBounds.y(), windowBounds.width(), CARD_PADDING + HEADER_HEIGHT);
        Bounds collapseButton = new Bounds(
                windowBounds.x() + windowBounds.width() - CARD_PADDING - COLLAPSE_BUTTON_SIZE,
                windowBounds.y() + (CARD_PADDING + HEADER_HEIGHT - COLLAPSE_BUTTON_SIZE) / 2,
                COLLAPSE_BUTTON_SIZE, COLLAPSE_BUTTON_SIZE);
        return new WindowLayout(entry, contents.get(entry.componentId()), accent, windowBounds, headerBounds, collapseButton);
    }

    private void drawWindow(RenderContext context, WindowLayout layout) {
        Bounds window = layout.windowBounds();
        context.drawRoundedRect(window.x(), window.y(), window.width(), window.height(), 1,
                context.theme().color(ThemeKey.PANEL_BACKGROUND), context.theme().color(ThemeKey.BORDER));

        // "Bold" header: no font-weight primitive on RenderContext, so accent color + a slightly larger scale stand in for it.
        context.drawText(layout.entry().label().getString(), window.x() + CARD_PADDING, window.y() + CARD_PADDING, layout.accentColor(), 1.05f);
        drawCollapseButton(context, layout.collapseButtonBounds(), layout.accentColor());
        HostedWindow.render(context, layout.content(), window);

        boolean resizingThis = layout.entry().componentId().equals(resizingWindowComponentId);
        context.drawResizeHandle(window.x() + window.width() - DraggableResizable.RESIZE_HANDLE_SIZE,
                window.y() + window.height() - DraggableResizable.RESIZE_HANDLE_SIZE, false, resizingThis);
    }

    private static void drawCollapseButton(RenderContext context, Bounds bounds, int accent) {
        int bg = (0x40 << 24) | (accent & 0x00FFFFFF);
        context.drawRoundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 1, bg, accent);
        context.drawText("x", bounds.x() + 2, bounds.y() + 1, accent, 0.65f);
    }

    /**
     * Hit-tests against the panel's own rendered bounds first (the collapsed-tab stack, plus the
     * open window's bounds if any): false (uncontested) outside them so the host screen keeps
     * handling its own dragging, true for anything inside. Left-clicks act on whatever was hit —
     * a tab opens it (collapsing any other open entry), the open window's content gets the click
     * first (so a slider under the resize corner still wins), then its corner handle starts a
     * resize, its collapse control re-collapses it, and its header starts a reposition drag.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (picker.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int mx = (int) mouseX;
        int my = (int) mouseY;
        boolean inWindow = lastOpenWindow != null && lastOpenWindow.windowBounds().contains(mx, my);
        if (!inWindow && !lastPanelBounds.contains(mx, my)) {
            return false;
        }
        if (button == 0) {
            if (inWindow) {
                handleWindowClick(mouseX, mouseY, mx, my);
            } else {
                for (TabLayout tab : lastTabLayout) {
                    if (tab.tabBounds().contains(mx, my)) {
                        openEntry(tab.entry());
                        break;
                    }
                }
            }
        }
        return true;
    }

    private void handleWindowClick(double mouseX, double mouseY, int mx, int my) {
        WindowLayout window = lastOpenWindow;
        Bounds windowBounds = window.windowBounds();
        if (HostedWindow.mouseClicked(window.content(), windowBounds, mouseX, mouseY)) {
            return;
        }
        String id = window.entry().componentId();
        if (DraggableResizable.handleBounds(windowBounds).contains(mx, my)) {
            resizingWindowComponentId = id;
            resizeOriginX = windowBounds.x();
            resizeOriginY = windowBounds.y();
        } else if (window.collapseButtonBounds().contains(mx, my)) {
            windows.collapse(id);
        } else if (window.headerBounds().contains(mx, my)) {
            draggingWindowComponentId = id;
            dragGrabOffsetX = mx - windowBounds.x();
            dragGrabOffsetY = my - windowBounds.y();
        }
    }

    /** Continues whatever gesture {@link #mouseClicked} started on the open window — a content drag (e.g. a slider scrub), or a window reposition/resize clamped to the last {@link #render}-supplied screen {@link Bounds}. Uncontested (false) if none is active. */
    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (picker.mouseDragged(mouseX, mouseY, button)) {
            return true;
        }
        if (lastOpenWindow != null && HostedWindow.mouseDragged(lastOpenWindow.content(), mouseX, mouseY, button)) {
            return true;
        }
        return continueWindowGesture((int) mouseX, (int) mouseY, false);
    }

    /**
     * Finalizes and ends any active gesture on the open window: content first (e.g. a slider scrub
     * commits), then a header drag or corner resize, whose final bounds are persisted the same way
     * {@link #mouseDragged}'s preview does. True if a gesture was consumed, false (uncontested) if
     * none was active, matching {@link #mouseClicked}/{@link #mouseDragged}/{@link #mouseScrolled}.
     */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (picker.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (lastOpenWindow != null && HostedWindow.mouseReleased(lastOpenWindow.content(), mouseX, mouseY, button)) {
            return true;
        }
        return continueWindowGesture((int) mouseX, (int) mouseY, true);
    }

    /** Applies the active window drag/resize at the pointer position; {@code finish} also ends it. */
    private boolean continueWindowGesture(int mx, int my, boolean finish) {
        Bounds target;
        String id;
        if (draggingWindowComponentId != null && lastOpenWindow != null) {
            Bounds current = lastOpenWindow.windowBounds();
            target = new Bounds(mx - dragGrabOffsetX, my - dragGrabOffsetY, current.width(), current.height());
            id = draggingWindowComponentId;
            if (finish) {
                draggingWindowComponentId = null;
            }
        } else if (resizingWindowComponentId != null) {
            target = new Bounds(resizeOriginX, resizeOriginY, mx - resizeOriginX, my - resizeOriginY);
            id = resizingWindowComponentId;
            if (finish) {
                resizingWindowComponentId = null;
            }
        } else {
            return false;
        }
        windows.persistBounds(id, WindowStates.clamp(target, lastRenderBounds));
        return true;
    }

    /** True for anything inside the open window (its content gets the wheel first — sliders nudge or, when the rows overflow, the content scrolls); collapsed tabs ignore scroll. */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (picker.mouseScrolled(mouseX, mouseY)) {
            return true;
        }
        if (lastOpenWindow == null || !lastOpenWindow.windowBounds().contains((int) mouseX, (int) mouseY)) {
            return false;
        }
        HostedWindow.mouseScrolled(lastOpenWindow.content(), lastOpenWindow.windowBounds(), mouseX, mouseY, scrollX, scrollY);
        return true;
    }

    /** Opens {@code entry}'s window (see {@link WindowStates#open}), placing a never-opened one clear of the collapsed-tab stack. */
    private void openEntry(ScaleConfigEntry entry) {
        windows.open(entry.componentId(), () -> HostedWindow.fit(contents.get(entry.componentId()), defaultWindowBounds()));
    }

    /**
     * Default position/size for an entry's window the first time it opens: the card size, offset clear
     * of the currently-rendered collapsed-tab stack ({@link #lastPanelBounds}) rather than placed
     * directly on top of it. Bottom-anchored stacks grow upward from the screen edge, so the window
     * goes above them instead of below.
     */
    private Bounds defaultWindowBounds() {
        int y = AnchorStack.stacksUpward(anchor)
                ? lastPanelBounds.y() - CARD_GAP - CARD_HEIGHT
                : lastPanelBounds.y() + lastPanelBounds.height() + CARD_GAP;
        return new Bounds(AnchorStack.anchorX(lastRenderBounds, anchor), y, CARD_WIDTH, CARD_HEIGHT);
    }

    /**
     * Whether {@code componentId}'s "Move Text and Icons" toggle is currently on — a host panel
     * polls this each frame (while its own edit mode/scale-config panel is visible) to decide
     * whether dragging its content should translate the content instead of the whole component.
     * Storage and key convention are owned by {@link MoveFlags}, which the toolbox's move toggles
     * write through too, so this getter reads back whatever they set.
     */
    public boolean isMoveContentEnabled(String componentId) {
        return MoveFlags.isOn(persistence, componentId);
    }

    private record TabLayout(ScaleConfigEntry entry, Bounds tabBounds) {}

    private record WindowLayout(ScaleConfigEntry entry, MarieComponent content, int accentColor, Bounds windowBounds,
                                 Bounds headerBounds, Bounds collapseButtonBounds) {}
}
