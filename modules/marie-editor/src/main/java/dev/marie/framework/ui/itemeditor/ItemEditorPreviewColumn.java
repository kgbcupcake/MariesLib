package dev.marie.framework.ui.itemeditor;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.Theme;
import dev.marie.framework.ui.ThemeKey;
import dev.marie.framework.ui.component.widgets.ItemSlotComponent;
import dev.marie.framework.ui.geometry.Bounds;
import dev.marie.framework.ui.itemeditor.recipe.RecipeDisplay;
import dev.marie.framework.ui.itemeditor.recipe.RecipeDisplayLookup;
import dev.marie.framework.ui.itemeditor.recipe.RecipeStationBlockBox;
import dev.marie.framework.ui.itemeditor.recipe.RecipeStationPreviewBox;
import dev.marie.framework.ui.toolbox.OptionStyle;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;

/**
 * {@link ItemEditorPanel}'s fixed-width left column: the item icon/name/id card, the {@link
 * RecipeStationPreviewBox} ingredient grid beneath it, and (once a recipe is actually known) the
 * {@link RecipeStationBlockBox} 3D station render beneath that — or, with no item targeted yet, the
 * centered empty-state drop target. Pulled out of {@code ItemEditorPanel} itself since this column
 * is a fully self-contained rendering concern (nothing here is read by the panel's body/menu-bar
 * logic), the same reasoning that already split the menu bar out into {@link ItemEditorMenuBar}.
 *
 * <p>When {@link RecipeDisplayLookup} finds more than one way to craft the targeted item (e.g. a
 * plain crafting-table recipe <em>and</em> a mod's automated press), only the highest-priority one
 * ({@link RecipeStationType}'s own declared order — every "special" station outranks a plain
 * crafting-table recipe) is shown; this column used to slowly rotate both boxes through every known
 * recipe in turn, but that "slideshow" read as more confusing than useful, so it's back to a single
 * steady recipe per item.
 */
@ApiStatus.Internal
final class ItemEditorPreviewColumn {

    /** Fixed width of the whole column — unlike the Hub Home page's entity box, this doesn't scale with window width: a flat item icon doesn't need to grow just because the window got wider. */
    static final int WIDTH = 72;

    private static final int GAP = 4;
    private static final int BOX_PADDING = 5;
    /** Same preview-box background the Hub Home page's {@code EntityPreviewBox} uses, so this reads as the same family of "preview box" rather than a different style. */
    private static final int BOX_BACKGROUND = 0xFF15171C;
    private static final int LINE_GAP = 3;
    private static final int NAME_TEXT_HEIGHT = 8;
    private static final int ID_TEXT_HEIGHT = 7;
    private static final float NAME_TEXT_SCALE = 0.8f;
    private static final float ID_TEXT_SCALE = 0.65f;
    /** Height of {@link RecipeStationBlockBox}'s 3D station render — fixed rather than content-driven, since a single block model always wants roughly the same amount of room regardless of which station it is. */
    private static final int STATION_BLOCK_BOX_HEIGHT = 44;

    private ItemEditorPreviewColumn() {
    }

    /** Height of the icon/name/id card alone: padding, the fixed-size slot, the name line, and (when known) the resource-id line below it — content-driven, not stretched to fill the column like the Hub Home page's entity box. */
    static int previewBoxHeight(@Nullable String sourceId) {
        int height = BOX_PADDING + ItemSlotComponent.SIZE + LINE_GAP + NAME_TEXT_HEIGHT;
        if (sourceId != null) {
            height += LINE_GAP + ID_TEXT_HEIGHT;
        }
        return height + BOX_PADDING;
    }

    /**
     * Total column height: the icon card, plus the ingredient grid and (once a recipe is known)
     * the 3D station box beneath it, each separated by {@link #GAP}.
     */
    static int totalHeight(@Nullable String sourceId, List<RecipeDisplay> recipes) {
        int height = previewBoxHeight(sourceId);
        if (sourceId != null) {
            RecipeDisplay recipe = bestRecipe(recipes);
            height += GAP + RecipeStationPreviewBox.requiredHeight(recipe);
            if (wantsStationBox(recipe)) {
                height += GAP + STATION_BLOCK_BOX_HEIGHT;
            }
        }
        return height;
    }

    /**
     * Whether {@code recipe}'s station is one {@link RecipeStationBlockBox} can actually show
     * something meaningful for — a station like Create's Mixer/Press/Millstone/Deployer, whose
     * moving parts are drawn by a {@code BlockEntityRenderer} this box has no block entity to
     * drive, has nothing worth a third box at all (not even its flat icon, which {@link
     * RecipeStationBlockBox} would otherwise fall back to): the icon card above already shows the
     * targeted item, and a second static icon of the station block right below the grid (which
     * already names/shows it via its ingredients) didn't earn its own box.
     */
    private static boolean wantsStationBox(@Nullable RecipeDisplay recipe) {
        return recipe != null && !recipe.stationType().prefersFlatIcon();
    }

    /** The highest-priority known way to craft the item ({@link RecipeDisplayLookup#findAll} is already sorted by {@link RecipeStationType}'s own declared priority), or {@code null} if none is known. */
    @Nullable
    private static RecipeDisplay bestRecipe(List<RecipeDisplay> recipes) {
        return recipes.isEmpty() ? null : recipes.get(0);
    }

    /**
     * Renders the whole column for {@code slot}'s current item (or the centered empty-state drop
     * target when nothing's targeted yet), returning the icon card's own bounds — the caller uses
     * this only as the slot's live screen-space bounds for external drop-target exposure (e.g. a JEI
     * ghost-ingredient area), since {@link ItemSlotComponent} already tracks its own bounds.
     */
    static Bounds render(RenderContext context, Bounds bounds, ItemSlotComponent slot, @Nullable String sourceId, List<RecipeDisplay> recipes) {
        ItemStack stack = slot.item();
        if (stack == null || stack.isEmpty()) {
            return renderEmptyState(context, bounds, slot);
        }

        int boxHeight = Math.min(bounds.height(), previewBoxHeight(sourceId));
        Bounds headerBounds = new Bounds(bounds.x(), bounds.y(), WIDTH, boxHeight);
        renderPreviewBox(context, headerBounds, slot, stack, sourceId);

        RecipeDisplay recipe = bestRecipe(recipes);
        int recipeBoxY = headerBounds.y() + headerBounds.height() + GAP;
        int recipeBoxHeight = Math.min(RecipeStationPreviewBox.requiredHeight(recipe),
                Math.max(0, bounds.y() + bounds.height() - recipeBoxY));
        if (recipeBoxHeight > 0) {
            RecipeStationPreviewBox.render(context, new Bounds(bounds.x(), recipeBoxY, WIDTH, recipeBoxHeight), recipe);
        }

        if (wantsStationBox(recipe)) {
            int stationBoxY = recipeBoxY + recipeBoxHeight + GAP;
            int stationBoxHeight = Math.min(STATION_BLOCK_BOX_HEIGHT, Math.max(0, bounds.y() + bounds.height() - stationBoxY));
            if (stationBoxHeight > 0) {
                RecipeStationBlockBox.render(context, new Bounds(bounds.x(), stationBoxY, WIDTH, stationBoxHeight), recipe);
            }
        }
        return headerBounds;
    }

    /**
     * The icon card: this item's icon, name, and resource id in a small framed card — same visual
     * family as the Hub Home page's {@code EntityPreviewBox} (rounded rect, dark background, accent
     * border) but sized for a flat icon rather than a stretched 3D render: fixed width, content-driven
     * height, centered text instead of a name strip beneath a stage.
     */
    private static void renderPreviewBox(RenderContext context, Bounds box, ItemSlotComponent slot, ItemStack stack, @Nullable String sourceId) {
        Theme theme = context.theme();
        int accent = theme.color(ThemeKey.BORDER_HOVER);
        context.drawRoundedRect(box.x(), box.y(), box.width(), box.height(), 1, BOX_BACKGROUND, accent);

        int innerWidth = Math.max(0, box.width() - 2 * BOX_PADDING);
        int slotX = box.x() + (box.width() - ItemSlotComponent.SIZE) / 2;
        int slotY = box.y() + BOX_PADDING;
        slot.render(context, new Bounds(slotX, slotY, ItemSlotComponent.SIZE, ItemSlotComponent.SIZE));

        int nameY = slotY + ItemSlotComponent.SIZE + LINE_GAP;
        String name = OptionStyle.fit(context, stack.getHoverName().getString(), NAME_TEXT_SCALE, innerWidth);
        context.drawText(name, box.x() + (box.width() - context.textWidth(name, NAME_TEXT_SCALE)) / 2, nameY,
                theme.color(ThemeKey.TEXT_PRIMARY), NAME_TEXT_SCALE);

        if (sourceId != null) {
            int idY = nameY + NAME_TEXT_HEIGHT + LINE_GAP;
            String fittedId = OptionStyle.fit(context, sourceId, ID_TEXT_SCALE, innerWidth);
            context.drawText(fittedId, box.x() + (box.width() - context.textWidth(fittedId, ID_TEXT_SCALE)) / 2, idY,
                    theme.color(ThemeKey.TEXT_SECONDARY), ID_TEXT_SCALE);
        }
    }

    /**
     * No item targeted yet: the slot has nothing to sit beside (no name, no recipe — both the preview
     * card and the recipe grid need a real item), so pinning it at the usual top-left spot just
     * strands a tiny drop target above an otherwise empty column. Centers the slot (and its label) in
     * the whole column instead, both as a visually balanced empty state and as a bigger, easier-to-hit
     * target for dragging an item in from JEI/EMI.
     */
    private static Bounds renderEmptyState(RenderContext context, Bounds bounds, ItemSlotComponent slot) {
        String label = "No item selected";
        int labelHeight = 9;
        int slotX = bounds.x() + (bounds.width() - ItemSlotComponent.SIZE) / 2;
        int slotY = bounds.y() + (bounds.height() - ItemSlotComponent.SIZE - GAP - labelHeight) / 2;
        Bounds slotBounds = new Bounds(slotX, slotY, ItemSlotComponent.SIZE, ItemSlotComponent.SIZE);
        slot.render(context, slotBounds);
        int labelX = bounds.x() + (bounds.width() - context.textWidth(label, 0.9f)) / 2;
        context.drawText(label, labelX, slotY + ItemSlotComponent.SIZE + GAP,
                context.theme().color(ThemeKey.TEXT_PRIMARY), 0.9f);
        return slotBounds;
    }
}
