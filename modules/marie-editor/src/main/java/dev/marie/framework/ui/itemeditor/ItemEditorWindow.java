package dev.marie.framework.ui.itemeditor;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.ThemeKey;
import dev.marie.framework.ui.api.MarieModuleSettings;
import dev.marie.framework.ui.api.MarieScaleConfig;
import dev.marie.framework.ui.component.ComponentState;
import dev.marie.framework.ui.component.Constraint;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.drag.DraggableResizable;
import dev.marie.framework.ui.geometry.Anchor;
import dev.marie.framework.ui.geometry.Bounds;
import dev.marie.framework.ui.geometry.Insets;
import dev.marie.framework.ui.geometry.Size;
import dev.marie.framework.ui.persistence.MarieConfigPersistenceProvider;
import dev.marie.framework.ui.scaleconfig.ScaleConfigEntry;
import dev.marie.framework.ui.scaleconfig.ScaleConfigPanel;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The draggable/resizable chrome (background/border/Glow/Pulse via {@link
 * MarieModuleSettings#standardPanel}, title bar, gear-opened Style panel) plus the one {@link
 * ItemEditorPanel} it hosts — everything {@link ItemEditorScreen} and {@link ItemEditorScreen} drew
 * and handled input for directly, now host-agnostic so the same window can also be driven by {@link
 * ItemEditorOverlay} (drawn on top of whatever screen is already open, e.g. the inventory with JEI's
 * list, instead of replacing it). Neither host owns rendering logic of its own; each just forwards
 * {@code render}/mouse events here and supplies the surrounding screen's width/height.
 */
@ApiStatus.Internal
final class ItemEditorWindow {

    static final int DEFAULT_WIDTH = 260;
    static final int DEFAULT_HEIGHT = 260;
    private static final int MIN_WIDTH = 220;
    private static final int MIN_HEIGHT = 200;
    private static final int MAX_WIDTH = 600;
    private static final int MAX_HEIGHT = 600;
    private static final int TITLE_COLOR = 0xFFFFFF;
    private static final int CONTENT_PADDING = 6;
    private static final int GEAR_SIZE = 10;

    private static final Constraint PANEL_CONSTRAINT = new Constraint(
            new Size(DEFAULT_WIDTH, DEFAULT_HEIGHT), new Size(MIN_WIDTH, MIN_HEIGHT), new Size(MAX_WIDTH, MAX_HEIGHT),
            false, false, true, true, Anchor.TOP_LEFT, Insets.NONE, Insets.NONE);

    private final Component title;
    private final ItemEditorPanel panel;
    private final PersistenceProvider store;
    /** Persistence id for this window's own drag/resize bounds. */
    private final String windowId;
    /** Persistence id for this window's Style (background/border/Glow/Pulse) settings. */
    private final String panelId;
    private final ScaleConfigPanel scaleConfigPanel;

    private final MarieComponent panelTarget = new MarieComponent() {
        @Override
        public String id() {
            return windowId;
        }

        @Override
        public Constraint constraint() {
            return PANEL_CONSTRAINT;
        }

        @Override
        public void render(RenderContext context, Bounds bounds) {
            // unused: this window renders itself directly, this target only carries identity/constraint
        }
    };

    private final DraggableResizable panelDrag;

    private Bounds panelBounds;
    private Bounds contentBounds = new Bounds(0, 0, 0, 0);
    private Bounds gearBounds = new Bounds(0, 0, 0, 0);
    private boolean scaleConfigVisible;

    ItemEditorWindow(String modId, ItemStack initial, @Nullable RecipeManager recipeManager, Component title) {
        this.title = title;
        this.panel = new ItemEditorPanel("item-editor", modId, initial, recipeManager);
        this.store = new MarieConfigPersistenceProvider(modId);
        this.windowId = modId + ".item_editor";
        this.panelId = windowId + ".style";
        this.panelDrag = new DraggableResizable(panelTarget, PANEL_CONSTRAINT,
                (target, bounds) -> {
                    panelBounds = bounds;
                    store.save(windowId, new ComponentState(bounds.x(), bounds.y(), bounds.width(), bounds.height(), false, false, false, 0));
                });
        MarieComponent stylePanel = MarieModuleSettings.standardPanel(title.getString(), store, panelId)
                .withoutBars()
                .withoutIcons()
                .withoutSizes()
                .withoutMoveAndHide()
                .withoutPadding()
                .withOwnStyle()
                .withGlow()
                .build();
        this.scaleConfigPanel = MarieScaleConfig.create(
                List.of(new ScaleConfigEntry(panelId, title).withContent(stylePanel)),
                store, Anchor.TOP_RIGHT);
    }

    /** Sets the initial position/size the first time this is called, from the saved state or centered in {@code screenWidth}x{@code screenHeight}. A no-op on later calls. */
    void init(int screenWidth, int screenHeight) {
        if (panelBounds == null) {
            panelBounds = store.load(windowId)
                    .map(s -> new Bounds(s.x(), s.y(), s.width(), s.height()))
                    .orElseGet(() -> new Bounds((screenWidth - DEFAULT_WIDTH) / 2, (screenHeight - DEFAULT_HEIGHT) / 2, DEFAULT_WIDTH, DEFAULT_HEIGHT));
        }
    }

    /** The slot's current screen-space area, for a JEI/REI/EMI ghost-ingredient handler to target. Null before {@link #init} has run. */
    @Nullable
    Rect2i slotScreenArea() {
        Bounds b = panel.slot().bounds();
        if (b.width() <= 0 || b.height() <= 0) {
            return null;
        }
        return new Rect2i(b.x(), b.y(), b.width(), b.height());
    }

    /** This window's own occupied area, e.g. for a host to report to JEI as the one region it shouldn't lay its ingredient list over. Falls back to a centered default before {@link #init} has run. */
    Rect2i occupiedArea(int screenWidth, int screenHeight) {
        Bounds b = panelBounds != null ? panelBounds
                : new Bounds((screenWidth - DEFAULT_WIDTH) / 2, (screenHeight - DEFAULT_HEIGHT) / 2, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        return new Rect2i(b.x(), b.y(), b.width(), b.height());
    }

    /** Called by a ghost-ingredient/drag-and-drop host when an item is dropped onto this window's slot. */
    void acceptDroppedItem(ItemStack stack) {
        panel.retarget(stack);
    }

    void render(RenderContext context, int mouseX, int mouseY) {
        Bounds livePreview = (panelDrag.isDragging() || panelDrag.isResizing()) ? panelDrag.mouseDragged(mouseX, mouseY) : null;
        Bounds activeBounds = livePreview != null ? livePreview : panelBounds;

        drawChrome(context, activeBounds);

        Bounds inner = new Bounds(contentBounds.x() + CONTENT_PADDING, contentBounds.y() + CONTENT_PADDING,
                Math.max(0, contentBounds.width() - 2 * CONTENT_PADDING), Math.max(0, contentBounds.height() - 2 * CONTENT_PADDING));
        context.pushClip(inner.x(), inner.y(), inner.width(), inner.height());
        try {
            panel.render(context, inner);
        } finally {
            context.popClip();
        }

        context.drawResizeHandle(activeBounds.x() + activeBounds.width() - DraggableResizable.RESIZE_HANDLE_SIZE,
                activeBounds.y() + activeBounds.height() - DraggableResizable.RESIZE_HANDLE_SIZE, false, panelDrag.isResizing());

        if (scaleConfigVisible) {
            scaleConfigPanel.render(context, new Bounds(0, 0, context.screenWidth(), context.screenHeight()));
        }
    }

    /** Background/border (Style-tab opacity+shade), Glow/Pulse ring, title and divider — everything {@code drawWindowChrome} draws, but through the player's own Style settings instead of fixed theme colors. */
    private void drawChrome(RenderContext context, Bounds b) {
        MarieModuleSettings.drawBoxGlow(context, store, panelId, b.x(), b.y(), b.width(), b.height());
        int background = MarieModuleSettings.styledBackground(context.theme().color(ThemeKey.PANEL_BACKGROUND), store, panelId);
        int border = MarieModuleSettings.styledBorder(context.theme().color(ThemeKey.BORDER), store, panelId);
        context.drawRoundedRect(b.x(), b.y(), b.width(), b.height(), 1, background, border);

        String titleText = title.getString();
        int titleX = b.x() + (b.width() - context.textWidth(titleText, 1f)) / 2;
        context.drawText(titleText, titleX, b.y() + RenderContext.WINDOW_CHROME_TITLE_TEXT_Y, TITLE_COLOR, 1f);

        gearBounds = new Bounds(b.x() + b.width() - RenderContext.WINDOW_CHROME_TITLE_ROW_HEIGHT + 6, b.y() + 3, GEAR_SIZE, GEAR_SIZE);
        context.drawText("⚙", gearBounds.x(), gearBounds.y(), scaleConfigVisible ? 0xFFFFFFFF : 0xFFAAAAAA, 0.9f);

        int dividerY = b.y() + RenderContext.WINDOW_CHROME_TITLE_ROW_HEIGHT;
        context.fillRect(b.x() + 1, dividerY, Math.max(0, b.width() - 2), 1, RenderContext.WINDOW_CHROME_DIVIDER_COLOR);
        contentBounds = new Bounds(b.x(), dividerY + 1, b.width(), Math.max(0, b.y() + b.height() - RenderContext.WINDOW_CHROME_TITLE_ROW_HEIGHT - 1));
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (scaleConfigVisible && scaleConfigPanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && gearBounds.contains((int) mouseX, (int) mouseY)) {
            scaleConfigVisible = !scaleConfigVisible;
            return true;
        }
        if (contentBounds.contains((int) mouseX, (int) mouseY) && panel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return panelDrag.mouseClicked((int) mouseX, (int) mouseY, panelBounds);
    }

    boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scaleConfigVisible && scaleConfigPanel.mouseDragged(mouseX, mouseY, button)) {
            return true;
        }
        if (panelDrag.isDragging() || panelDrag.isResizing()) {
            panelDrag.mouseDragged((int) mouseX, (int) mouseY);
            return true;
        }
        return panel.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (scaleConfigVisible && scaleConfigPanel.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (panelDrag.isDragging() || panelDrag.isResizing()) {
            panelDrag.mouseReleased((int) mouseX, (int) mouseY);
            return true;
        }
        return panel.mouseReleased(mouseX, mouseY, button);
    }

    boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scaleConfigVisible && scaleConfigPanel.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return panel.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Whether {@code (mouseX, mouseY)} falls within this window's own box, for a host that only wants to claim/cancel input it's actually over (the Style panel, when open, is handled by its own mouseClicked/Dragged/Released/Scrolled return value instead, since it can extend outside the box). */
    boolean isMouseOverBox(double mouseX, double mouseY) {
        return panelBounds != null && panelBounds.contains((int) mouseX, (int) mouseY);
    }

    boolean isScaleConfigVisible() {
        return scaleConfigVisible;
    }
}
