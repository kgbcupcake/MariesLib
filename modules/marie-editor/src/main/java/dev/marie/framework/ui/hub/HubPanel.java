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

    private final String id;
    private final Component title;
    private final PersistenceProvider persistence;
    private final List<HubSidebarEntry> entries;
    private final HubChildPopup popup = new HubChildPopup();
    private final DraggableResizable panelDrag;

    private String selectedId;
    private Bounds panelBounds;
    private Bounds lastRenderBounds = new Bounds(0, 0, 0, 0);
    private Bounds lastPanelBounds = new Bounds(0, 0, 0, 0);
    private Bounds lastContentBodyBounds = new Bounds(0, 0, 0, 0);

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
            persistence.save(id, new ComponentState(bounds.x(), bounds.y(), bounds.width(), bounds.height(), false, false, false, 0));
        });
    }

    public void render(RenderContext context, Bounds screen) {
        lastRenderBounds = screen;
        if (panelBounds == null) {
            panelBounds = persistence.load(id)
                    .map(s -> new Bounds(s.x(), s.y(), s.width(), s.height()))
                    .orElseGet(() -> new Bounds((screen.width() - DEFAULT_WIDTH) / 2, (screen.height() - DEFAULT_HEIGHT) / 2, DEFAULT_WIDTH, DEFAULT_HEIGHT));
        }
        lastPanelBounds = panelBounds;
        Bounds content = context.drawWindowChrome(panelBounds.x(), panelBounds.y(), panelBounds.width(), panelBounds.height(), title.getString(), TITLE_COLOR);
        drawSidebar(context, content);
        drawContentPane(context, content);

        HubSidebarEntry selected = findSelected();
        String openGroupId = selected instanceof HubGroupEntry group ? group.id() : null;
        popup.beginFrame(openGroupId);
        popup.render(context, screen);
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
        if (popup.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (!lastPanelBounds.contains(mx, my)) {
            return false;
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
                            popup.show(hit.child(), group.id(), lastPanelBounds, lastRenderBounds);
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
