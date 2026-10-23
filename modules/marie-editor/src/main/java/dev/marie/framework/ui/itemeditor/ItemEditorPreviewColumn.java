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
 * plain crafting-table recipe <em>and</em> a mod's automated press), both boxes slowly cycle
 * through every known recipe in turn — slower than {@link RecipeStationPreviewBox}'s own
 * within-recipe ingredient-alternatives cycling, so the two read as distinct things changing for
 * distinct reasons, not one flickery mess — with a small "N/M" tell in the corner so a player
 * knows it's rotating between recipes rather than glitching.
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
    /** How long each recipe shows before the boxes advance to the next known way to craft the item — several times {@link RecipeStationPreviewBox}'s own ingredient-alternatives period, so the two cycles read as separate things rather than competing for attention. */
    private static final long RECIPE_SOURCE_CYCLE_MS = 4500L;
    private static final float SOURCE_INDEX_SCALE = 0.6f;

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
     * the 3D station box beneath it, each separated by {@link #GAP}. Sized to the <em>tallest</em>
     * of {@code recipes} (not just whichever one is currently showing) so the column's own height
     * — and everything laid out around it — doesn't jump every time {@link #render} cycles to a
     * recipe with a differently-shaped ingredient grid.
     */
    static int totalHeight(@Nullable String sourceId, List<RecipeDisplay> recipes) {
        int height = previewBoxHeight(sourceId);
        if (sourceId != null) {
            height += GAP + tallestGridHeight(recipes);
            if (anyWantsStationBox(recipes)) {
                height += GAP + STATION_BLOCK_BOX_HEIGHT;
            }
        }
        return height;
    }

    /**
     * Whether {@code recipes} contains at least one station {@link RecipeStationBlockBox} can
     * actually show something meaningful for — a station like Create's Mixer/Press/Millstone/
     * Deployer, whose moving parts are drawn by a {@code BlockEntityRenderer} this box has no block
     * entity to drive, has nothing worth a third box at all (not even its flat icon, which {@link
     * RecipeStationBlockBox} would otherwise fall back to): the icon card above already shows the
     * targeted item, and a second static icon of the station block right below the grid (which
     * already names/shows it via its ingredients) didn't earn its own box. Checked across the whole
     * list, not just the recipe currently showing, so the box's reserved height — like {@link
     * #tallestGridHeight} — doesn't appear/disappear as the slideshow cycles between a station that
     * wants it and one that doesn't.
     */
    private static boolean anyWantsStationBox(List<RecipeDisplay> recipes) {
        for (RecipeDisplay recipe : recipes) {
            if (wantsStationBox(recipe)) {
                return true;
            }
        }
        return false;
    }

    private static boolean wantsStationBox(@Nullable RecipeDisplay recipe) {
        return recipe != null && !recipe.stationType().prefersFlatIcon();
    }

    private static int tallestGridHeight(List<RecipeDisplay> recipes) {
        if (recipes.isEmpty()) {
            return RecipeStationPreviewBox.requiredHeight(null);
        }
        int tallest = 0;
        for (RecipeDisplay recipe : recipes) {
            tallest = Math.max(tallest, RecipeStationPreviewBox.requiredHeight(recipe));
        }
        return tallest;
    }

    /** Which of {@code recipes} to show right now — a plain time-based rotation shared by both boxes, so the ingredient grid and the 3D station always show the same recipe at the same moment. */
    @Nullable
    private static RecipeDisplay currentRecipe(List<RecipeDisplay> recipes) {
        if (recipes.isEmpty()) {
            return null;
        }
        if (recipes.size() == 1) {
            return recipes.get(0);
        }
        int index = (int) ((System.currentTimeMillis() / RECIPE_SOURCE_CYCLE_MS) % recipes.size());
        return recipes.get(index);
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

        RecipeDisplay recipe = currentRecipe(recipes);
        int recipeBoxY = headerBounds.y() + headerBounds.height() + GAP;
        int recipeBoxHeight = Math.min(tallestGridHeight(recipes),
                Math.max(0, bounds.y() + bounds.height() - recipeBoxY));
        if (recipeBoxHeight > 0) {
            RecipeStationPreviewBox.render(context, new Bounds(bounds.x(), recipeBoxY, WIDTH, recipeBoxHeight), recipe);
            if (recipes.size() > 1) {
                renderSourceIndex(context, bounds.x(), recipeBoxY, recipes.indexOf(recipe) + 1, recipes.size());
            }
        }

        // Reserved whenever any recipe in the slideshow wants it (see #anyWantsStationBox) — there's
        // no station block to show for "No known recipe" either, same as before.
        if (anyWantsStationBox(recipes)) {
            int stationBoxY = recipeBoxY + recipeBoxHeight + GAP;
            int stationBoxHeight = Math.min(STATION_BLOCK_BOX_HEIGHT, Math.max(0, bounds.y() + bounds.height() - stationBoxY));
            // The reserved slot stays blank on a frame where the currently-showing recipe is one
            // that doesn't want the box (e.g. the slideshow landed on a Create station) rather than
            // drawing nothing-useful into it — simpler than reflowing the layout for one frame.
            if (stationBoxHeight > 0 && wantsStationBox(recipe)) {
                RecipeStationBlockBox.render(context, new Bounds(bounds.x(), stationBoxY, WIDTH, stationBoxHeight), recipe);
            }
        }
        return headerBounds;
    }

    /** The small "1/3"-style tell in the recipe grid's top-right corner, shown only while there's more than one known way to craft the item — without it, the slow rotation between entirely different recipes would just look like an unrelated flicker rather than an intentional slideshow. */
    private static void renderSourceIndex(RenderContext context, int columnX, int boxY, int oneBasedIndex, int total) {
        String text = oneBasedIndex + "/" + total;
        int x = columnX + WIDTH - context.textWidth(text, SOURCE_INDEX_SCALE) - 2;
        context.drawText(text, x, boxY + 2, context.theme().color(ThemeKey.TEXT_SECONDARY), SOURCE_INDEX_SCALE);
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
