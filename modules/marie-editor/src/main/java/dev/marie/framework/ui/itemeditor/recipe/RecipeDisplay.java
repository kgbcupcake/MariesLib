package dev.marie.framework.ui.itemeditor.recipe;

import dev.marie.framework.api.ApiStatus;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;

/**
 * One recipe found by {@link RecipeDisplayLookup} for display in {@link RecipeStationPreviewBox}.
 *
 * <p>Each grid cell is every item that would actually satisfy that ingredient (an {@code
 * Ingredient} matched by a tag like {@code c:crops/onion} can mean several different items — a raw
 * onion, onion seeds, ... — and showing only the first one is misleading about what the recipe
 * actually accepts), not a single representative stack; {@link RecipeStationPreviewBox} cycles
 * through a cell's alternatives over time the way JEI/EMI do.
 *
 * @param stationType which station block/layout to render
 * @param shapedGrid   for a {@link RecipeStationType#CRAFTING_TABLE} recipe with a known shape,
 *                     exactly 9 entries (row-major, 3x3), an empty list for unused cells; {@code
 *                     null} for every other recipe, which instead lists its ingredients in {@link
 *                     #ingredients} with no implied layout
 * @param ingredients  every distinct ingredient slot, each as its full list of accepted items, in
 *                     recipe order; used directly when {@link #shapedGrid} is {@code null}, and
 *                     kept here too (redundantly) even for a shaped recipe so a caller that doesn't
 *                     care about shape can ignore {@link #shapedGrid} entirely
 * @param result       the recipe's output stack
 */
@ApiStatus.Internal
public record RecipeDisplay(
        RecipeStationType stationType,
        @Nullable List<List<ItemStack>> shapedGrid,
        List<List<ItemStack>> ingredients,
        ItemStack result
) {
}
