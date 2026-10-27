package dev.marie.framework.ui.hub;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.Theme;
import dev.marie.framework.ui.ThemeKey;
import dev.marie.framework.ui.api.ContentWrapper;
import dev.marie.framework.ui.component.ComponentState;
import dev.marie.framework.ui.component.Constraint;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.drag.DraggableResizable;
import dev.marie.framework.ui.geometry.Anchor;
import dev.marie.framework.ui.geometry.Bounds;
import dev.marie.framework.ui.geometry.Insets;
import dev.marie.framework.ui.geometry.Size;
import dev.marie.framework.ui.scaleconfig.colorpicker.PickerWindow;
import dev.marie.framework.ui.toolbox.OptionLayout;
import dev.marie.framework.ui.toolbox.colorpicker.ColorSlot;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * A resizable/draggable hub window: a sidebar list of {@link HubSidebarEntry} rows and a content pane
 * showing whichever one is selected — visually modeled on {@code CommandCenterScreen} (window chrome
 * via {@link RenderContext#drawWindowChrome}, drag/resize via {@link DraggableResizable}), but NOT a
 * {@code Screen}: like {@code ScaleConfigPanel}, this is a plain object a host screen owns and
 * renders/forwards input to only while visible, so the host (and whatever it's editing) stays visible
 * and interactive underneath.
 *
 * <p>A {@link HubEntry} selected in the sidebar renders its content inline in the content pane, full
 * remaining body size, every mouse event forwarded straight into it. A {@link HubGroupEntry} selected
 * instead shows the current result of its child supplier as a plain list; clicking a child opens
 * {@link HubChildPopup} above the hub. Only one level of nesting is supported by design — a group's
 * children are leaves (they open a popup, they don't themselves contain groups).
 */
@ApiStatus.Experimental
public final class HubPanel {

    private static final int CONTENT_PADDING = 8;

    private static final int DEFAULT_WIDTH = 460;
    private static final int DEFAULT_HEIGHT = 320;
    /** Small enough that the sidebar/content pane genuinely need their own scroll (see {@link #drawSidebar}/{@link #drawContentPane}) rather than this floor alone keeping everything visible. */
    private static final int MIN_WIDTH = 200;
    private static final int MIN_HEIGHT = 140;
    private static final int MAX_WIDTH = 900;
    private static final int MAX_HEIGHT = 700;
    private static final int SCROLL_STEP = 20;
    private static final int TITLE_COLOR = 0xFFFFFF;

    private final String id;
    private final Component title;
    private final PersistenceProvider persistence;
    private final List<HubSidebarEntry> entries;
    /** Chrome color overrides — background/border/title-text/accent — each {@code null} until a host supplies one (see the 8-arg constructor), falling back to the current {@link Theme}'s colors so a caller with nothing to customize sees identical output to before these existed. */
    private final java.util.function.IntSupplier backgroundColor;
    private final java.util.function.IntSupplier borderColor;
    private final java.util.function.IntSupplier titleColor;
    private final java.util.function.IntSupplier accentColor;
    /** Overrides the header's static right-side label (default: {@code marieslib.hub.editor_label}, "Editor") — e.g. a host whose hub IS its primary screen, not a secondary editor overlay, wants its own name there instead. {@code null} keeps today's default. */
    private final java.util.function.Supplier<String> editorLabelOverride;
    /** Opt-in colors for the sidebar tabs (and a group's child rows) and the header's right-side label; {@code null} keeps the old look. */
    private java.util.function.IntSupplier tabTextColor;
    private java.util.function.IntSupplier labelColor;
    private final HubChildPopup popup = new HubChildPopup();
    /** The one color-picker window this hub can show — same mechanism {@code ScaleConfigPanel} uses, opened from a color slot in either a leaf's inline content or an open child popup's content. */
    private final PickerWindow picker = new PickerWindow();
    private final DraggableResizable panelDrag;

    private String selectedId;
    private Bounds panelBounds;
    /** Whether the hub is minimized to a small clickable strip instead of its full window — persisted alongside position/size. */
    private boolean collapsed;
    private Bounds lastRenderBounds = new Bounds(0, 0, 0, 0);
    private Bounds lastPanelBounds = new Bounds(0, 0, 0, 0);
    private Bounds lastContentBodyBounds = new Bounds(0, 0, 0, 0);
    private Bounds lastMinimizeButtonBounds = new Bounds(0, 0, 0, 0);
    private final HubSidebar sidebar = new HubSidebar();
    private final HubCollapsedStrip collapsedStrip = new HubCollapsedStrip();

    private final HubChildList childList = new HubChildList();

    public HubPanel(Component title, String id, PersistenceProvider persistence, List<HubSidebarEntry> entries) {
        this(title, id, persistence, entries, null, null, null, null);
    }

    /** Same, with the hub's own chrome colors overridden instead of following the current {@link Theme} — any of the four may be {@code null} to keep that one theme-derived. */
    public HubPanel(Component title, String id, PersistenceProvider persistence, List<HubSidebarEntry> entries,
                     java.util.function.IntSupplier backgroundColor, java.util.function.IntSupplier borderColor,
                     java.util.function.IntSupplier titleColor, java.util.function.IntSupplier accentColor) {
        this(title, id, persistence, entries, backgroundColor, borderColor, titleColor, accentColor, null);
    }

    /** Same, also overriding the header's static right-side label — see {@link #editorLabelOverride}. */
    public HubPanel(Component title, String id, PersistenceProvider persistence, List<HubSidebarEntry> entries,
                     java.util.function.IntSupplier backgroundColor, java.util.function.IntSupplier borderColor,
                     java.util.function.IntSupplier titleColor, java.util.function.IntSupplier accentColor,
                     java.util.function.Supplier<String> editorLabelOverride) {
        this.title = title;
        this.id = id;
        this.persistence = persistence;
        this.entries = entries;
        this.backgroundColor = backgroundColor;
        this.borderColor = borderColor;
        this.titleColor = titleColor;
        this.accentColor = accentColor;
        this.editorLabelOverride = editorLabelOverride;
        if (!entries.isEmpty()) {
            this.selectedId = entries.get(0).id();
        }
        Constraint constraint = new Constraint(new Size(DEFAULT_WIDTH, DEFAULT_HEIGHT), new Size(MIN_WIDTH, MIN_HEIGHT),
                new Size(MAX_WIDTH, MAX_HEIGHT), false, false, true, true, Anchor.TOP_LEFT, Insets.NONE, Insets.NONE);
        MarieComponent target = new MarieComponent() {
            @Override
            public String id() {
                return HubPanel.this.id;
            }

            @Override
            public Constraint constraint() {
                return constraint;
            }

            @Override
            public void render(RenderContext context, Bounds bounds) {
                // unused: HubPanel renders itself directly, this target only carries identity/constraint
            }
        };
        this.panelDrag = new DraggableResizable(target, constraint, (t, bounds) -> {
            panelBounds = bounds;
            persistence.save(id, new ComponentState(bounds.x(), bounds.y(), bounds.width(), bounds.height(), collapsed, false, false, 0));
        });
        // A leaf's content is a fixed, single MarieComponent for the hub's whole lifetime, so it's
        // wired once here — unlike a group's dynamically-rebuilt children, wired lazily each time
        // they're actually shown (see drawContentPane).
        for (HubSidebarEntry entry : entries) {
            if (entry instanceof HubEntry leaf && optionLayoutOf(leaf.content()) instanceof OptionLayout layout) {
                layout.setColorSlotListener(slot -> showPicker(leaf.id(), leaf.label(), slot, lastPanelBounds));
            }
        }
    }

    private int backgroundColor(Theme theme) {
        return backgroundColor != null ? backgroundColor.getAsInt() : theme.color(ThemeKey.PANEL_BACKGROUND);
    }

    private int borderColor(Theme theme) {
        return borderColor != null ? borderColor.getAsInt() : theme.color(ThemeKey.BORDER);
    }

    private int titleColor() {
        return titleColor != null ? titleColor.getAsInt() : TITLE_COLOR;
    }

    /** Colors the sidebar tabs (and a group's child rows) apart from the header title; until set they follow the title color. */
    public HubPanel tabTextColor(java.util.function.IntSupplier color) {
        this.tabTextColor = color;
        return this;
    }

    /** Colors the header's right-side label; until set it uses the theme's secondary text color. */
    public HubPanel labelColor(java.util.function.IntSupplier color) {
        this.labelColor = color;
        return this;
    }

    private int accentColor(Theme theme) {
        return accentColor != null ? accentColor.getAsInt() : theme.color(ThemeKey.BORDER_HOVER);
    }

    /** This hub's own rendered screen {@link Bounds} as of the last {@link #render} call — the collapsed strip's bounds while minimized, the full window's otherwise. {@code (0,0,0,0)} before the first render. Lets a host anchor its own chrome (e.g. an edit-mode toggle) to wherever this hub currently is, since it's free-draggable and the host doesn't otherwise know its position. */
    public Bounds panelBounds() {
        return lastPanelBounds;
    }

    /** The sidebar entry currently selected (the first one until the player picks another), or null if the hub has none. */
    @ApiStatus.Experimental
    public HubSidebarEntry selectedEntry() {
        return findSelected();
    }

    /** {@link #selectedEntry()}'s id, or null. */
    @ApiStatus.Experimental
    public String selectedEntryId() {
        HubSidebarEntry selected = findSelected();
        return selected != null ? selected.id() : null;
    }

    /** Selects the entry with {@code entryId}, as if its sidebar row were clicked; ignored if no entry has it. */
    @ApiStatus.Experimental
    public void selectEntry(String entryId) {
        for (HubSidebarEntry entry : entries) {
            if (entry.id().equals(entryId)) {
                selectedId = entryId;
                childList.resetScroll();
                return;
            }
        }
    }

    /** The {@link OptionLayout} {@code content} is or wraps (through {@link ContentWrapper}s), or null. */
    private static OptionLayout optionLayoutOf(MarieComponent content) {
        MarieComponent current = content;
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (current instanceof OptionLayout layout) {
                return layout;
            }
            current = current instanceof ContentWrapper wrapper ? wrapper.wrappedContent() : null;
        }
        return null;
    }

    private String editorLabel() {
        return editorLabelOverride != null ? editorLabelOverride.get()
                : Component.translatable("marieslib.hub.editor_label").getString();
    }

    /** Opens or retargets the color-picker window on {@code slot}, owned by {@code ownerId} — see {@link #currentContentOwnerId()} for how the picker knows when to close itself again. */
    private void showPicker(String ownerId, Component ownerLabel, ColorSlot slot, Bounds ownerWindow) {
        picker.show(slot, ownerId, slot.label() + " - " + ownerLabel.getString(), ownerWindow, lastRenderBounds);
    }

    /** The id of the content showing now (the selected leaf, or a group's open child; null if none), so the color picker closes once its owner stops showing. */
    private String currentContentOwnerId() {
        HubSidebarEntry selected = findSelected();
        if (selected instanceof HubEntry leaf) {
            return leaf.id();
        }
        if (selected instanceof HubGroupEntry) {
            return popup.currentChildId();
        }
        return null;
    }

    public void render(RenderContext context, Bounds screen) {
        lastRenderBounds = screen;
        if (panelBounds == null) {
            panelBounds = persistence.load(id)
                    .map(s -> {
                        collapsed = s.collapsed();
                        return new Bounds(s.x(), s.y(), s.width(), s.height());
                    })
                    .orElseGet(() -> new Bounds((screen.width() - DEFAULT_WIDTH) / 2, (screen.height() - DEFAULT_HEIGHT) / 2, DEFAULT_WIDTH, DEFAULT_HEIGHT));
        }
        if (collapsed) {
            drawCollapsed(context);
            // A minimized hub shows nothing else — no sidebar/content/popup/picker to interact with
            // until it's restored, matching the "small clickable module" the collapsed strip is.
            return;
        }
        lastPanelBounds = panelBounds;
        Bounds content = drawHeader(context, panelBounds);
        drawSidebar(context, content);
        drawContentPane(context, content);
        // Visible resize affordance in the corner — the hub was already resizable via panelDrag's
        // corner hitbox, but with nothing drawn there a player had no way to know where to grab.
        context.drawResizeHandle(panelBounds.x() + panelBounds.width() - DraggableResizable.RESIZE_HANDLE_SIZE,
                panelBounds.y() + panelBounds.height() - DraggableResizable.RESIZE_HANDLE_SIZE, false, panelDrag.isResizing());

        HubSidebarEntry selected = findSelected();
        String openGroupId = selected instanceof HubGroupEntry group ? group.id() : null;
        popup.beginFrame(openGroupId);
        popup.render(context, screen);
        picker.beginFrame(currentContentOwnerId());
        picker.render(context, screen);
    }

    private Bounds drawHeader(RenderContext context, Bounds bounds) {
        Theme theme = context.theme();
        HubSidebarEntry selected = findSelected();
        int label = labelColor != null ? labelColor.getAsInt() : theme.color(ThemeKey.TEXT_SECONDARY);
        HubHeader.Layout header = HubHeader.draw(context, bounds, backgroundColor(theme), borderColor(theme), titleColor(),
                label, accentColor(theme), editorLabel(), selected != null ? selected.label().getString() : title.getString());
        lastMinimizeButtonBounds = header.minimizeButton();
        return header.content();
    }

    private void drawCollapsed(RenderContext context) {
        Theme theme = context.theme();
        lastPanelBounds = collapsedStrip.draw(context, panelBounds, backgroundColor(theme), borderColor(theme),
                titleColor(), accentColor(theme), editorLabel());
    }

    private void drawSidebar(RenderContext context, Bounds content) {
        // A host's tab color wins; otherwise its title color, so a hub with its own chrome colors restyles its sidebar list too.
        Integer tabs = tabTextColor != null ? Integer.valueOf(tabTextColor.getAsInt()) : titleColor != null ? Integer.valueOf(titleColor()) : null;
        sidebar.draw(context, content, entries, selectedId, accentColor(context.theme()), tabs, CONTENT_PADDING);
    }

    private void drawContentPane(RenderContext context, Bounds content) {
        childList.clearHits();
        int areaX = content.x() + HubSidebar.WIDTH + CONTENT_PADDING;
        int areaWidth = Math.max(0, content.x() + content.width() - areaX - CONTENT_PADDING);
        int areaY = content.y() + CONTENT_PADDING;
        int areaHeight = Math.max(0, content.y() + content.height() - CONTENT_PADDING - areaY);
        Bounds body = new Bounds(areaX, areaY, areaWidth, areaHeight);
        lastContentBodyBounds = body;

        HubSidebarEntry selected = findSelected();
        if (selected == null) {
            return;
        }
        context.pushClip(body.x(), body.y(), body.width(), body.height());
        try {
            if (selected instanceof HubEntry leaf) {
                leaf.content().render(context, body);
            } else if (selected instanceof HubGroupEntry group) {
                Theme theme = context.theme();
                childList.draw(context, body, group, backgroundColor(theme), borderColor(theme),
                        tabTextColor != null ? tabTextColor.getAsInt() : titleColor(), CONTENT_PADDING);
            }
        } finally {
            context.popClip();
        }
    }

    /** Writes the current {@code collapsed} flag alongside the hub's existing position/size — called on every minimize/restore, not just a drag/resize commit. */
    private void persistCollapsedState() {
        persistence.save(id, new ComponentState(panelBounds.x(), panelBounds.y(), panelBounds.width(), panelBounds.height(), collapsed, false, false, 0));
    }

    private HubSidebarEntry findSelected() {
        for (HubSidebarEntry entry : entries) {
            if (entry.id().equals(selectedId)) {
                return entry;
            }
        }
        return entries.isEmpty() ? null : entries.get(0);
    }

    /**
     * The popup is checked first: it renders above everything else, so a click meant for it must
     * never also register against the sidebar/content pane underneath it. Sidebar rows are checked
     * next, then the selected entry's content/child list, then the panel's own header-drag/resize as
     * the final fallback (matching {@code CommandCenterScreen}'s exact click-priority order).
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (collapsed) {
            return button == 0 && collapsedStrip.press(mx, my, lastPanelBounds, panelBounds);
        }
        if (picker.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (popup.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (!lastPanelBounds.contains(mx, my)) {
            return false;
        }
        if (button == 0 && lastMinimizeButtonBounds.contains(mx, my)) {
            collapsed = true;
            persistCollapsedState();
            return true;
        }
        if (button == 0) {
            HubSidebarEntry clicked = sidebar.entryAt(mx, my);
            if (clicked != null) {
                selectedId = clicked.id();
                childList.resetScroll();
                return true;
            }
            if (lastContentBodyBounds.contains(mx, my)) {
                HubSidebarEntry selected = findSelected();
                if (selected instanceof HubEntry leaf) {
                    if (leaf.content().mouseClicked(mouseX, mouseY, button)) {
                        return true;
                    }
                } else if (selected instanceof HubGroupEntry group) {
                    HubChildEntry child = childList.childAt(mx, my);
                    if (child != null) {
                        if (optionLayoutOf(child.content()) instanceof OptionLayout layout) {
                            layout.setColorSlotListener(slot -> showPicker(child.id(), child.label(), slot, lastPanelBounds));
                        }
                        popup.show(child, group.id(), lastPanelBounds, lastRenderBounds);
                        return true;
                    }
                }
            }
            if (panelDrag.mouseClicked(mx, my, lastPanelBounds)) {
                return true;
            }
        }
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (collapsed) {
            Bounds moved = collapsedStrip.drag((int) mouseX, (int) mouseY, panelBounds);
            if (moved != null) {
                panelBounds = moved;
            }
            return moved != null;
        }
        if (picker.mouseDragged(mouseX, mouseY, button)) {
            return true;
        }
        if (popup.mouseDragged(mouseX, mouseY, button)) {
            return true;
        }
        if (panelDrag.isDragging() || panelDrag.isResizing()) {
            Bounds preview = panelDrag.mouseDragged((int) mouseX, (int) mouseY);
            if (preview != null) {
                panelBounds = preview;
            }
            return true;
        }
        if (findSelected() instanceof HubEntry leaf) {
            return leaf.content().mouseDragged(mouseX, mouseY, button, 0, 0);
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (collapsed) {
            if (!collapsedStrip.isDragging()) {
                return false;
            }
            // A pure click with no movement in between restores the hub.
            collapsed = collapsedStrip.release();
            persistCollapsedState();
            return true;
        }
        if (picker.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (popup.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (panelDrag.isDragging() || panelDrag.isResizing()) {
            panelDrag.mouseReleased((int) mouseX, (int) mouseY);
            return true;
        }
        if (findSelected() instanceof HubEntry leaf) {
            return leaf.content().mouseReleased(mouseX, mouseY, button);
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (collapsed) {
            return false;
        }
        if (picker.mouseScrolled(mouseX, mouseY)) {
            return true;
        }
        if (popup.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (sidebar.scroll(mx, my, scrollY, SCROLL_STEP)) {
            return true;
        }
        if (!lastContentBodyBounds.contains(mx, my)) {
            return false;
        }
        HubSidebarEntry selected = findSelected();
        if (selected instanceof HubEntry leaf) {
            return leaf.content().mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (selected instanceof HubGroupEntry && scrollY != 0) {
            childList.scroll(scrollY, SCROLL_STEP);
            return true;
        }
        return false;
    }

    /**
     * Forwards a key press to the open child popup's content, else the selected leaf's content. An open
     * popup holds the keyboard: a key it doesn't use is left unhandled (so the screen still sees e.g.
     * Escape), never passed to the leaf hidden behind it.
     */
    @ApiStatus.Experimental
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (collapsed) {
            return false;
        }
        if (popup.isLive()) {
            return popup.keyPressed(keyCode, scanCode, modifiers);
        }
        return findSelected() instanceof HubEntry leaf && leaf.content().keyPressed(keyCode, scanCode, modifiers);
    }

    /** Forwards a typed character the same way as {@link #keyPressed}. */
    @ApiStatus.Experimental
    public boolean charTyped(char codePoint, int modifiers) {
        if (collapsed) {
            return false;
        }
        if (popup.isLive()) {
            return popup.charTyped(codePoint, modifiers);
        }
        return findSelected() instanceof HubEntry leaf && leaf.content().charTyped(codePoint, modifiers);
    }

}
