package dev.marie.framework.ui.commandcenter;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.core.MarieCore;
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
import dev.marie.framework.ui.persistence.MarieConfigPersistenceProvider;
import dev.marie.framework.ui.render.GuiGraphicsRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Shared, domain-agnostic screen rendering every registered {@link CommandCenterRegistry} category/card — sidebar-navigated, no knowledge of what any card actually does. */
@ApiStatus.Experimental
public final class CommandCenterScreen extends Screen {

    private static final int SIDEBAR_WIDTH = 100;
    private static final int CARD_WIDTH = 150;
    private static final int CARD_HEIGHT = 48;
    private static final int CARD_GAP = 8;

    /** Default chrome size — used only as the first-run fallback and the drag/resize preferred size. */
    private static final int DEFAULT_PANEL_WIDTH = 460;
    private static final int DEFAULT_PANEL_HEIGHT = 320;
    private static final int MIN_PANEL_WIDTH = 300;
    private static final int MIN_PANEL_HEIGHT = 200;
    private static final int MAX_PANEL_WIDTH = 1000;
    private static final int MAX_PANEL_HEIGHT = 800;
    /** Bright title color for the chrome's centered title row. */
    private static final int TITLE_COLOR = 0xFFFFFF;
    /** Gap between the chrome's divider/edges and the sidebar/card grid content. */
    private static final int CONTENT_PADDING = 8;

    /** Persistence id for this screen's drag/resize bounds. */
    private static final String ID = "marieslib.commandcenter.panel";
    private static final PersistenceProvider PERSISTENCE = new MarieConfigPersistenceProvider(MarieCore.MOD_ID);
    private static final Constraint PANEL_CONSTRAINT = new Constraint(
            new Size(DEFAULT_PANEL_WIDTH, DEFAULT_PANEL_HEIGHT),
            new Size(MIN_PANEL_WIDTH, MIN_PANEL_HEIGHT),
            new Size(MAX_PANEL_WIDTH, MAX_PANEL_HEIGHT),
            false, false, true, true, Anchor.TOP_LEFT, Insets.NONE, Insets.NONE
    );

    private static final int SIDEBAR_ROW_HEIGHT = 22;
    private static final int SIDEBAR_SELECTION_BAR_WIDTH = 4;
    /** Space reserved for the selection bar + gap, whether or not it's drawn this row, so the dot/label never shift on selection change. */
    private static final int SIDEBAR_ROW_INSET = 10;
    private static final int SIDEBAR_DOT_SIZE = 5;

    private static final int CARD_PILL_WIDTH = 18;
    private static final int CARD_PILL_HEIGHT = 10;
    private static final int CARD_PADDING = 6;

    /** Pixels the card grid scrolls per wheel notch — no existing scrollable-list precedent in marie-ui to match, so this is a plain clamped offset. */
    private static final int CARD_SCROLL_STEP = 24;

    private final Screen parent;
    private List<CommandCenterCategory> categories = List.of();
    private String selectedCategoryId;

    /** This frame's rendered sidebar row hit regions, rebuilt every render() pass, for click forwarding. */
    private final List<CategoryHit> categoryHits = new ArrayList<>();

    /** This frame's rendered card hit regions, rebuilt every render() pass, for click/scroll forwarding. */
    private final List<CardHit> cardHits = new ArrayList<>();

    /** Current scroll position within the selected category's card list, in pixels; clamped to content height every drawCards() pass. */
    private int cardScrollOffset;

    /** This frame's card-grid clip region, rebuilt every render() pass, for hover-to-scroll hit testing. */
    private Bounds lastCardAreaBounds = new Bounds(0, 0, 0, 0);

    /** Live/persisted chrome position and size — loaded on first init(), updated on every panelDrag commit. */
    private Bounds panelBounds;

    /** Identity token DraggableResizable passes through to onCommit; this screen isn't itself a MarieComponent. */
    private final MarieComponent panelTarget = new MarieComponent() {
        @Override
        public String id() {
            return ID;
        }

        @Override
        public Constraint constraint() {
            return PANEL_CONSTRAINT;
        }

        @Override
        public void render(RenderContext context, Bounds bounds) {
            // unused: CommandCenterScreen renders itself directly, this target only carries identity/constraint
        }
    };

    private final DraggableResizable panelDrag = new DraggableResizable(panelTarget, PANEL_CONSTRAINT,
            (target, bounds) -> {
                panelBounds = bounds;
                PERSISTENCE.save(ID, new ComponentState(bounds.x(), bounds.y(), bounds.width(), bounds.height(), false, false, false, 0));
            });

    public CommandCenterScreen(Screen parent) {
        super(Component.translatable("marieslib.commandcenter.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        categories = CommandCenterRegistry.categories();
        if (selectedCategoryId == null && !categories.isEmpty()) {
            selectedCategoryId = categories.get(0).id();
        }
        if (panelBounds == null) {
            panelBounds = PERSISTENCE.load(ID)
                    .map(s -> new Bounds(s.x(), s.y(), s.width(), s.height()))
                    .orElseGet(() -> new Bounds((this.width - DEFAULT_PANEL_WIDTH) / 2, (this.height - DEFAULT_PANEL_HEIGHT) / 2, DEFAULT_PANEL_WIDTH, DEFAULT_PANEL_HEIGHT));
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(this.width / 2 - 75, this.height - 28, 150, 20)
                .build());
    }

    /** Suppress the default dirt/blur background — the chrome is a small floating dashboard over the world, not a full-screen modal menu, same rationale as {@code EditOverlayScreen}. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // intentionally empty
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (minecraft == null) {
            return;
        }
        RenderContext context = new GuiGraphicsRenderContext(graphics, Minecraft.getInstance(), Theme.DARK, partialTick);
        Bounds livePreview = (panelDrag.isDragging() || panelDrag.isResizing()) ? panelDrag.mouseDragged(mouseX, mouseY) : null;
        Bounds activeBounds = livePreview != null ? livePreview : panelBounds;
        Bounds content = context.drawWindowChrome(activeBounds.x(), activeBounds.y(), activeBounds.width(), activeBounds.height(), this.title.getString(), TITLE_COLOR);
        drawSidebar(context, content);
        drawCards(context, content);
    }

    /** Full-width sidebar rows: color-dot + label, selected row gets a left accent bar and a lighter background fill. Clipped to the panel's content height so an oversized category list can't bleed past the border. */
    private void drawSidebar(RenderContext context, Bounds content) {
        categoryHits.clear();
        Theme theme = context.theme();
        int rowWidth = SIDEBAR_WIDTH;
        int neutralDot = theme.color(ThemeKey.TEXT_SECONDARY);
        int areaY = content.y();
        int areaHeight = content.height();
        context.pushClip(content.x(), areaY, rowWidth, areaHeight);
        try {
            int y = areaY + CONTENT_PADDING;
            for (CommandCenterCategory category : categories) {
                if (y + SIDEBAR_ROW_HEIGHT > areaY && y < areaY + areaHeight) {
                    Bounds rowBounds = new Bounds(content.x(), y, rowWidth, SIDEBAR_ROW_HEIGHT);
                    boolean selected = category.id().equals(selectedCategoryId);
                    if (selected) {
                        context.fillRect(content.x(), y, rowWidth, SIDEBAR_ROW_HEIGHT, theme.color(ThemeKey.HANDLE_BACKGROUND));
                        context.fillRect(content.x(), y, SIDEBAR_SELECTION_BAR_WIDTH, SIDEBAR_ROW_HEIGHT, theme.color(ThemeKey.BORDER_HOVER));
                    }
                    int dotX = content.x() + SIDEBAR_ROW_INSET;
                    int dotY = y + SIDEBAR_ROW_HEIGHT / 2 - SIDEBAR_DOT_SIZE / 2;
                    context.fillRect(dotX, dotY, SIDEBAR_DOT_SIZE, SIDEBAR_DOT_SIZE, neutralDot);
                    int textColor = theme.color(selected ? ThemeKey.TEXT_PRIMARY : ThemeKey.TEXT_SECONDARY);
                    context.drawText(category.label().getString(), dotX + SIDEBAR_DOT_SIZE + 6, y + SIDEBAR_ROW_HEIGHT / 2 - 4, textColor, 1f);
                    categoryHits.add(new CategoryHit(category, rowBounds));
                }
                y += SIDEBAR_ROW_HEIGHT;
            }
        } finally {
            context.popClip();
        }
    }

    /** Wrapping card grid, clipped and scrolled to the panel's current content bounds so neither an oversized category nor a shrunk panel can push cards past the border. */
    private void drawCards(RenderContext context, Bounds content) {
        cardHits.clear();
        if (selectedCategoryId == null) {
            lastCardAreaBounds = new Bounds(content.x(), content.y(), 0, 0);
            return;
        }
        Theme theme = context.theme();
        int panelColor = theme.color(ThemeKey.PANEL_BACKGROUND);
        int borderColor = theme.color(ThemeKey.BORDER);
        int textColor = theme.color(ThemeKey.TEXT_SECONDARY);

        int areaX = content.x() + SIDEBAR_WIDTH + CONTENT_PADDING;
        int areaWidth = Math.max(CARD_WIDTH, content.x() + content.width() - areaX - CONTENT_PADDING);
        int areaY = content.y() + CONTENT_PADDING;
        int areaHeight = Math.max(0, content.y() + content.height() - CONTENT_PADDING - areaY);
        lastCardAreaBounds = new Bounds(areaX, areaY, areaWidth, areaHeight);

        List<CommandCenterCardEntry> entries = CommandCenterRegistry.cardsFor(selectedCategoryId);
        int columns = Math.max(1, (areaWidth + CARD_GAP) / (CARD_WIDTH + CARD_GAP));
        int rows = (entries.size() + columns - 1) / columns;
        int totalContentHeight = rows == 0 ? 0 : rows * CARD_HEIGHT + (rows - 1) * CARD_GAP;
        int maxScroll = Math.max(0, totalContentHeight - areaHeight);
        cardScrollOffset = Math.max(0, Math.min(cardScrollOffset, maxScroll));

        context.pushClip(areaX, areaY, areaWidth, areaHeight);
        try {
            int x = areaX;
            int y = areaY - cardScrollOffset;
            int col = 0;
            for (CommandCenterCardEntry entry : entries) {
                Bounds bounds = new Bounds(x, y, CARD_WIDTH, CARD_HEIGHT);
                if (bounds.y() + bounds.height() > areaY && bounds.y() < areaY + areaHeight) {
                    drawCard(context, entry, bounds, panelColor, borderColor, textColor);
                    cardHits.add(new CardHit(entry, bounds));
                }
                col++;
                if (col >= columns) {
                    col = 0;
                    x = areaX;
                    y += CARD_HEIGHT + CARD_GAP;
                } else {
                    x += CARD_WIDTH + CARD_GAP;
                }
            }
        } finally {
            context.popClip();
        }
    }

    private void drawCard(RenderContext context, CommandCenterCardEntry entry, Bounds bounds,
                           int panelColor, int borderColor, int textColor) {
        context.drawRoundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 1, panelColor, borderColor);
        if (entry instanceof CommandCenterCard card) {
            context.drawText(card.title().getString(), bounds.x() + CARD_PADDING, bounds.y() + CARD_PADDING, card.accentColor(), 1.05f);
            if (card.subtitle() != null) {
                context.drawText(card.subtitle().getString(), bounds.x() + CARD_PADDING, bounds.y() + 18, textColor, 0.8f);
            }
            if (card.onClick() != null) {
                drawCardPill(context, bounds.x() + bounds.width() - CARD_PADDING - CARD_PILL_WIDTH, bounds.y() + CARD_PADDING, card.accentColor());
            }
        } else if (entry instanceof CustomCommandCenterCard custom) {
            context.drawText(custom.title().getString(), bounds.x() + CARD_PADDING, bounds.y() + CARD_PADDING, context.theme().color(ThemeKey.TEXT_PRIMARY), 1f);
            Bounds bodyBounds = new Bounds(bounds.x() + 4, bounds.y() + 18, bounds.width() - 8, bounds.height() - 22);
            context.pushClip(bodyBounds.x(), bodyBounds.y(), bodyBounds.width(), bodyBounds.height());
            try {
                custom.content().render(context, bodyBounds);
            } finally {
                context.popClip();
            }
        }
    }

    /** Muted-accent "Open" affordance pill, same fill/border convention as {@code ScaleConfigPanel}'s status pill. */
    private static void drawCardPill(RenderContext context, int x, int y, int accent) {
        int pillBg = (0x40 << 24) | (accent & 0x00FFFFFF);
        context.drawRoundedRect(x, y, CARD_PILL_WIDTH, CARD_PILL_HEIGHT, 1, pillBg, accent);
        context.drawText("→", x + 6, y + 1, 0xFFFFFF, 0.8f);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (CategoryHit hit : categoryHits) {
            if (hit.bounds().contains((int) mouseX, (int) mouseY)) {
                selectedCategoryId = hit.category().id();
                cardScrollOffset = 0;
                return true;
            }
        }
        for (CardHit hit : cardHits) {
            if (!hit.bounds().contains((int) mouseX, (int) mouseY)) {
                continue;
            }
            if (hit.entry() instanceof CustomCommandCenterCard custom) {
                if (custom.content().mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            } else if (hit.entry() instanceof CommandCenterCard card && card.onClick() != null) {
                card.onClick().run();
                return true;
            }
        }
        if (panelDrag.mouseClicked((int) mouseX, (int) mouseY, panelBounds)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (panelDrag.isDragging() || panelDrag.isResizing()) {
            panelDrag.mouseDragged((int) mouseX, (int) mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (panelDrag.isDragging() || panelDrag.isResizing()) {
            panelDrag.mouseReleased((int) mouseX, (int) mouseY);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        for (CardHit hit : cardHits) {
            if (hit.entry() instanceof CustomCommandCenterCard custom && hit.bounds().contains((int) mouseX, (int) mouseY)) {
                if (custom.content().mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                    return true;
                }
            }
        }
        if (scrollY != 0 && lastCardAreaBounds.contains((int) mouseX, (int) mouseY)) {
            cardScrollOffset = Math.max(0, cardScrollOffset - (int) Math.signum(scrollY) * CARD_SCROLL_STEP);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private record CategoryHit(CommandCenterCategory category, Bounds bounds) {}

    private record CardHit(CommandCenterCardEntry entry, Bounds bounds) {}
}
