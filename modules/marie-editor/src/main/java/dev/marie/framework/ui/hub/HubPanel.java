package dev.marie.framework.ui.hub;

import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.Theme;
import dev.marie.framework.ui.ThemeKey;
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

import java.util.ArrayList;
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
public final class HubPanel {

    private static final int SIDEBAR_WIDTH = 120;
    private static final int SIDEBAR_ROW_HEIGHT = 20;
    private static final int SIDEBAR_SELECTION_BAR_WIDTH = 3;
    private static final int SIDEBAR_ROW_INSET = 8;
    private static final int CONTENT_PADDING = 8;
    private static final int CHILD_ROW_HEIGHT = 20;
    private static final int CHILD_ROW_GAP = 2;

    private static final int DEFAULT_WIDTH = 460;
    private static final int DEFAULT_HEIGHT = 320;
    private static final int MIN_WIDTH = 320;
    private static final int MIN_HEIGHT = 220;
    private static final int MAX_WIDTH = 900;
    private static final int MAX_HEIGHT = 700;
    private static final int TITLE_COLOR = 0xFFFFFF;

    private static final int HEADER_HEIGHT = 16;
    private static final int HEADER_PADDING = 6;
    private static final int MINIMIZE_BUTTON_SIZE = 9;
    /** Gap either side of the vertical divider separating the dynamic left label from the static "Editor" label. */
    private static final int DIVIDER_GAP = 6;

    private static final int COLLAPSED_WIDTH = 180;
    private static final int COLLAPSED_HEIGHT = 20;

    private final String id;
    private final Component title;
    private final PersistenceProvider persistence;
    private final List<HubSidebarEntry> entries;
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

    private final List<SidebarHit> sidebarHits = new ArrayList<>();
    private final List<ChildHit> childHits = new ArrayList<>();

    public HubPanel(Component title, String id, PersistenceProvider persistence, List<HubSidebarEntry> entries) {
        this.title = title;
        this.id = id;
        this.persistence = persistence;
        this.entries = entries;
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
            if (entry instanceof HubEntry leaf && leaf.content() instanceof OptionLayout layout) {
                layout.setColorSlotListener(slot -> showPicker(leaf.id(), leaf.label(), slot, lastPanelBounds));
            }
        }
    }

    /** Opens or retargets the color-picker window on {@code slot}, owned by {@code ownerId} — see {@link #currentContentOwnerId()} for how the picker knows when to close itself again. */
    private void showPicker(String ownerId, Component ownerLabel, ColorSlot slot, Bounds ownerWindow) {
        picker.show(slot, ownerId, slot.label() + " - " + ownerLabel.getString(), ownerWindow, lastRenderBounds);
    }

    /**
     * The id of whatever content is actually showing right now — the selected leaf's id, or, for a
     * selected group, the id of whichever child its popup currently has open (or {@code null} if no
     * popup is open) — so the color-picker window closes itself the moment that content is no longer
     * showing (switching leaves, switching groups, or closing/switching the child popup), the same
     * "close when your owner is no longer the open one" contract {@code ScaleConfigPanel}'s picker
     * already uses, generalized past this hub's own two content levels.
     */
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

    /**
     * The window chrome, with the single centered title split into two: the currently selected
     * entry's own name on the left (updates as the sidebar selection changes) and a static "Editor"
     * label on the right, separated by a vertical divider — plus a minimize button in the corner.
     * Returns the content area below the header, same contract as {@code RenderContext#drawWindowChrome}.
     */
    private Bounds drawHeader(RenderContext context, Bounds bounds) {
        Theme theme = context.theme();
        context.drawRoundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 1,
                theme.color(ThemeKey.PANEL_BACKGROUND), theme.color(ThemeKey.BORDER));

        lastMinimizeButtonBounds = new Bounds(bounds.x() + bounds.width() - HEADER_PADDING - MINIMIZE_BUTTON_SIZE,
                bounds.y() + (HEADER_HEIGHT - MINIMIZE_BUTTON_SIZE) / 2, MINIMIZE_BUTTON_SIZE, MINIMIZE_BUTTON_SIZE);
        int accent = theme.color(ThemeKey.BORDER_HOVER);
        context.drawRoundedRect(lastMinimizeButtonBounds.x(), lastMinimizeButtonBounds.y(),
                lastMinimizeButtonBounds.width(), lastMinimizeButtonBounds.height(), 1, (0x40 << 24) | (accent & 0x00FFFFFF), accent);
        context.drawText("-", lastMinimizeButtonBounds.x() + 3, lastMinimizeButtonBounds.y(), accent, 0.8f);

        String editorLabel = Component.translatable("marieslib.hub.editor_label").getString();
        int editorWidth = context.textWidth(editorLabel, 1f);
        int editorX = lastMinimizeButtonBounds.x() - HEADER_PADDING - editorWidth;
        int textY = bounds.y() + (HEADER_HEIGHT - 8) / 2;
        context.drawText(editorLabel, editorX, textY, theme.color(ThemeKey.TEXT_SECONDARY), 1f);

        int dividerX = editorX - DIVIDER_GAP;
        context.fillRect(dividerX, bounds.y() + 3, 1, HEADER_HEIGHT - 6, theme.color(ThemeKey.BORDER));

        HubSidebarEntry selected = findSelected();
        String dynamicLabel = selected != null ? selected.label().getString() : title.getString();
        int dynamicMaxWidth = Math.max(0, dividerX - DIVIDER_GAP - (bounds.x() + HEADER_PADDING));
        context.drawText(dev.marie.framework.ui.toolbox.OptionStyle.fit(context, dynamicLabel, 1f, dynamicMaxWidth),
                bounds.x() + HEADER_PADDING, textY, TITLE_COLOR, 1f);

        int dividerY = bounds.y() + HEADER_HEIGHT;
        context.fillRect(bounds.x() + 1, dividerY, Math.max(0, bounds.width() - 2), 1, theme.color(ThemeKey.BORDER));

        return new Bounds(bounds.x(), dividerY + 1, bounds.width(), Math.max(0, bounds.height() - HEADER_HEIGHT - 1));
    }

    /** The minimized strip: the currently selected entry's name plus a restore affordance, at the hub's last position — click anywhere on it to restore. */
    private void drawCollapsed(RenderContext context) {
        Bounds strip = new Bounds(panelBounds.x(), panelBounds.y(), Math.min(COLLAPSED_WIDTH, panelBounds.width()), COLLAPSED_HEIGHT);
        lastPanelBounds = strip;
        Theme theme = context.theme();
        context.drawRoundedRect(strip.x(), strip.y(), strip.width(), strip.height(), 1,
                theme.color(ThemeKey.PANEL_BACKGROUND), theme.color(ThemeKey.BORDER));
        HubSidebarEntry selected = findSelected();
        String label = selected != null ? selected.label().getString() : title.getString();
        int maxWidth = Math.max(0, strip.width() - 2 * HEADER_PADDING - 10);
        context.drawText(dev.marie.framework.ui.toolbox.OptionStyle.fit(context, label, 1f, maxWidth),
                strip.x() + HEADER_PADDING, strip.y() + (strip.height() - 8) / 2, TITLE_COLOR, 1f);
        int accent = theme.color(ThemeKey.BORDER_HOVER);
        context.drawText("+", strip.x() + strip.width() - HEADER_PADDING - 6, strip.y() + (strip.height() - 8) / 2, accent, 0.8f);
    }

    private void drawSidebar(RenderContext context, Bounds content) {
        sidebarHits.clear();
        Theme theme = context.theme();
        int areaY = content.y();
        int areaHeight = content.height();
        context.pushClip(content.x(), areaY, SIDEBAR_WIDTH, areaHeight);
        try {
            int y = areaY + CONTENT_PADDING;
            for (HubSidebarEntry entry : entries) {
                if (y + SIDEBAR_ROW_HEIGHT > areaY && y < areaY + areaHeight) {
                    Bounds rowBounds = new Bounds(content.x(), y, SIDEBAR_WIDTH, SIDEBAR_ROW_HEIGHT);
                    boolean selected = entry.id().equals(selectedId);
                    if (selected) {
                        context.fillRect(content.x(), y, SIDEBAR_WIDTH, SIDEBAR_ROW_HEIGHT, theme.color(ThemeKey.HANDLE_BACKGROUND));
                        context.fillRect(content.x(), y, SIDEBAR_SELECTION_BAR_WIDTH, SIDEBAR_ROW_HEIGHT, theme.color(ThemeKey.BORDER_HOVER));
                    }
                    int textColor = theme.color(selected ? ThemeKey.TEXT_PRIMARY : ThemeKey.TEXT_SECONDARY);
                    context.drawText(entry.label().getString(), content.x() + SIDEBAR_ROW_INSET, y + SIDEBAR_ROW_HEIGHT / 2 - 4, textColor, 1f);
                    sidebarHits.add(new SidebarHit(entry, rowBounds));
                }
                y += SIDEBAR_ROW_HEIGHT;
            }
        } finally {
            context.popClip();
        }
    }

    private void drawContentPane(RenderContext context, Bounds content) {
        childHits.clear();
        int areaX = content.x() + SIDEBAR_WIDTH + CONTENT_PADDING;
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
                int y = body.y();
                for (HubChildEntry child : group.children().get()) {
                    Bounds rowBounds = new Bounds(body.x(), y, body.width(), CHILD_ROW_HEIGHT);
                    if (rowBounds.y() + rowBounds.height() > body.y() && rowBounds.y() < body.y() + body.height()) {
                        context.drawRoundedRect(rowBounds.x(), rowBounds.y(), rowBounds.width(), rowBounds.height(), 1,
                                theme.color(ThemeKey.PANEL_BACKGROUND), theme.color(ThemeKey.BORDER));
                        context.drawText(child.label().getString(), rowBounds.x() + CONTENT_PADDING,
                                rowBounds.y() + (CHILD_ROW_HEIGHT - 8) / 2, theme.color(ThemeKey.TEXT_PRIMARY), 0.9f);
                        childHits.add(new ChildHit(group, child, rowBounds));
                    }
                    y += CHILD_ROW_HEIGHT + CHILD_ROW_GAP;
                }
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
            if (button == 0 && lastPanelBounds.contains(mx, my)) {
                collapsed = false;
                persistCollapsedState();
                return true;
            }
            return false;
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
            for (SidebarHit hit : sidebarHits) {
                if (hit.bounds().contains(mx, my)) {
                    selectedId = hit.entry().id();
                    return true;
                }
            }
            if (lastContentBodyBounds.contains(mx, my)) {
                HubSidebarEntry selected = findSelected();
                if (selected instanceof HubEntry leaf) {
                    if (leaf.content().mouseClicked(mouseX, mouseY, button)) {
                        return true;
                    }
                } else if (selected instanceof HubGroupEntry group) {
                    for (ChildHit hit : childHits) {
                        if (hit.bounds().contains(mx, my)) {
                            HubChildEntry child = hit.child();
                            if (child.content() instanceof OptionLayout layout) {
                                layout.setColorSlotListener(slot -> showPicker(child.id(), child.label(), slot, lastPanelBounds));
                            }
                            popup.show(child, group.id(), lastPanelBounds, lastRenderBounds);
                            return true;
                        }
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
            return false;
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
            return false;
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
        if (!lastContentBodyBounds.contains((int) mouseX, (int) mouseY)) {
            return false;
        }
        if (findSelected() instanceof HubEntry leaf) {
            return leaf.content().mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return false;
    }

    private record SidebarHit(HubSidebarEntry entry, Bounds bounds) {}

    private record ChildHit(HubGroupEntry group, HubChildEntry child, Bounds bounds) {}
}
