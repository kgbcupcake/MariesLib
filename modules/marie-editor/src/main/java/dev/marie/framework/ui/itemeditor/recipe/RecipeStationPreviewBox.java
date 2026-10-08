package dev.marie.framework.ui.itemeditor.recipe;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.Theme;
import dev.marie.framework.ui.ThemeKey;
import dev.marie.framework.ui.geometry.Bounds;
import dev.marie.framework.ui.render.GuiGraphicsRenderContext;
import dev.marie.framework.util.MarieRegistryUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A framed box rendering a {@link RecipeDisplay} as a JEI/EMI-style recipe grid — bordered input
 * slots (the recipe's actual shape when known, e.g. a crafting-table pattern), an arrow, and a
 * bordered result slot — rather than a rendered 3D station: a player who's used any recipe-viewer
 * mod already reads this layout instantly, which a 3D crafting-table model (with no indication of
 * *which* station — crafting table, furnace, cooking pot — actually mattered to the recipe) didn't
 * buy anything over. Hovering a filled slot shows that item's normal tooltip plus a trailing mod
 * name line, matching how JEI/EMI attribute an ingredient's source mod.
 *
 * <p>A slot whose ingredient accepts more than one item (a tag like {@code c:crops/onion} can mean
 * a raw onion <em>or</em> onion seeds) cycles through all of them over time, the same "rotating
 * ingredient" convention JEI/EMI use — showing only the first match would otherwise misrepresent
 * what the recipe actually accepts.
 */
@ApiStatus.Experimental
public final class RecipeStationPreviewBox {

    private static final int BOX_BACKGROUND = 0xFF15171C;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_GAP = 1;
    private static final int GRID_COLUMNS = 3;
    private static final int BOX_PADDING = 4;
    /** How long each alternative shows before a multi-item slot advances to the next one — slow enough to read, not a flicker. */
    private static final long CYCLE_PERIOD_MS = 1000L;
    private static final String ARROW = "->";
    private static final String EMPTY_HINT = "No known recipe";
    /** Height of the "No known recipe" placeholder — just enough for one centered line. */
    private static final int EMPTY_HEIGHT = 24;

    private RecipeStationPreviewBox() {
    }

    /** Height a host must give {@link #render} for {@code recipe} to draw without clipping. */
    public static int requiredHeight(@Nullable RecipeDisplay recipe) {
        if (recipe == null) {
            return EMPTY_HEIGHT;
        }
        return contentHeight(gridRows(recipe));
    }

    public static void render(RenderContext context, Bounds box, @Nullable RecipeDisplay recipe) {
        Theme theme = context.theme();
        int accent = theme.color(ThemeKey.BORDER_HOVER);
        context.drawRoundedRect(box.x(), box.y(), box.width(), box.height(), 1, BOX_BACKGROUND, accent);

        if (recipe == null) {
            String text = EMPTY_HINT;
            context.drawText(text, box.x() + (box.width() - context.textWidth(text, 0.75f)) / 2,
                    box.y() + (box.height() - 6) / 2, theme.color(ThemeKey.TEXT_SECONDARY), 0.75f);
            return;
        }

        boolean shaped = recipe.shapedGrid() != null;
        List<List<ItemStack>> cells = shaped ? recipe.shapedGrid() : recipe.ingredients();
        int rows = gridRows(recipe);

        int gridWidth = GRID_COLUMNS * SLOT_SIZE + (GRID_COLUMNS - 1) * SLOT_GAP;
        int gridHeight = rows * SLOT_SIZE + (rows - 1) * SLOT_GAP;
        int gridX = box.x() + Math.max(0, (box.width() - gridWidth) / 2);
        int gridY = box.y() + BOX_PADDING;

        List<Bounds> hoverBounds = new ArrayList<>();
        List<ItemStack> hoverItems = new ArrayList<>();
        for (int i = 0; i < rows * GRID_COLUMNS; i++) {
            int col = i % GRID_COLUMNS;
            int row = i / GRID_COLUMNS;
            int slotX = gridX + col * (SLOT_SIZE + SLOT_GAP);
            int slotY = gridY + row * (SLOT_SIZE + SLOT_GAP);
            List<ItemStack> options = i < cells.size() ? cells.get(i) : List.of();
            ItemStack stack = currentAlternative(options);
            drawSlot(context, slotX, slotY, stack);
            if (!stack.isEmpty()) {
                hoverBounds.add(new Bounds(slotX, slotY, SLOT_SIZE, SLOT_SIZE));
                hoverItems.add(stack);
            }
        }

        int resultRowY = gridY + gridHeight + BOX_PADDING;
        int arrowWidth = context.textWidth(ARROW, 0.8f);
        int rowWidth = arrowWidth + SLOT_GAP * 2 + SLOT_SIZE;
        int rowX = box.x() + Math.max(0, (box.width() - rowWidth) / 2);
        context.drawText(ARROW, rowX, resultRowY + (SLOT_SIZE - 8) / 2, theme.color(ThemeKey.TEXT_SECONDARY), 0.8f);
        int resultX = rowX + arrowWidth + SLOT_GAP * 2;
        drawSlot(context, resultX, resultRowY, recipe.result());
        if (!recipe.result().isEmpty()) {
            hoverBounds.add(new Bounds(resultX, resultRowY, SLOT_SIZE, SLOT_SIZE));
            hoverItems.add(recipe.result());
        }

        renderHoveredTooltip(context, hoverBounds, hoverItems);
    }

    private static int gridRows(RecipeDisplay recipe) {
        if (recipe.shapedGrid() != null) {
            return 3;
        }
        int count = recipe.ingredients().size();
        return Math.max(1, (int) Math.ceil(count / (double) GRID_COLUMNS));
    }

    private static int contentHeight(int gridRows) {
        int gridHeight = gridRows * SLOT_SIZE + (gridRows - 1) * SLOT_GAP;
        return BOX_PADDING + gridHeight + BOX_PADDING + SLOT_SIZE + BOX_PADDING;
    }

    /** The alternative a multi-item slot is currently showing — a plain time-based rotation shared by every slot (no per-slot offset), so a glance at the whole grid shows a consistent "frame" rather than slots drifting out of sync with each other. */
    private static ItemStack currentAlternative(List<ItemStack> options) {
        if (options.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (options.size() == 1) {
            return options.get(0);
        }
        int index = (int) ((System.currentTimeMillis() / CYCLE_PERIOD_MS) % options.size());
        return options.get(index);
    }

    /** A bordered slot cell — same visual family as {@code ItemSlotComponent}, just smaller (18px to match a vanilla inventory slot rather than that component's 20px). */
    private static void drawSlot(RenderContext context, int x, int y, ItemStack stack) {
        Theme theme = context.theme();
        context.drawRoundedRect(x, y, SLOT_SIZE, SLOT_SIZE, 0,
                theme.color(ThemeKey.PANEL_BACKGROUND), theme.color(ThemeKey.BORDER));
        if (!stack.isEmpty()) {
            context.drawItem(stack, x + 1, y + 1, 1f);
        }
    }

    /**
     * Drawn last (after every slot), so it layers on top of them regardless of draw order — safe
     * here specifically because nothing this host renders afterward shares screen space with this
     * box's narrow left column (the item editor's body column sits well to the right of it).
     */
    private static void renderHoveredTooltip(RenderContext context, List<Bounds> hoverBounds, List<ItemStack> hoverItems) {
        if (!(context instanceof GuiGraphicsRenderContext graphicsContext)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        double guiScale = (double) minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
        int mouseX = (int) (minecraft.mouseHandler.xpos() * guiScale);
        int mouseY = (int) (minecraft.mouseHandler.ypos() * guiScale);
        for (int i = 0; i < hoverBounds.size(); i++) {
            if (!hoverBounds.get(i).contains(mouseX, mouseY)) {
                continue;
            }
            ItemStack stack = hoverItems.get(i);
            List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(minecraft, stack));
            String modName = modDisplayName(stack);
            if (modName != null) {
                lines.add(Component.literal(modName).withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
            }
            graphicsContext.graphics().renderTooltip(minecraft.font, lines, stack.getTooltipImage(), mouseX, mouseY);
            return;
        }
    }

    /** The owning mod's display name (e.g. "Farmer's Delight"), "Minecraft" for a vanilla item, or {@code null} if the item has no registry id or its mod isn't resolvable. */
    @Nullable
    private static String modDisplayName(ItemStack stack) {
        ResourceLocation id = MarieRegistryUtils.itemKey(stack);
        if (id == null) {
            return null;
        }
        String namespace = id.getNamespace();
        if ("minecraft".equals(namespace)) {
            return "Minecraft";
        }
        return ModList.get().getModContainerById(namespace)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(namespace);
    }
}
