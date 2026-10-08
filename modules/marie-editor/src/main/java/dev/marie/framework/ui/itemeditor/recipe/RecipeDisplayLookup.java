package dev.marie.framework.ui.itemeditor.recipe;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.util.MarieRegistryUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds the recipe that produces a given item, for {@link RecipeStationPreviewBox} to render.
 *
 * <p>Scans every recipe once per call rather than maintaining a cache (unlike {@link
 * dev.marie.framework.scanner.RecipeInheritanceResolver}'s index): this only runs when the item
 * editor's target item changes, which is rare compared to a per-frame or per-tick cost, so a fresh
 * scan is simpler and never goes stale across a resource/datapack reload.
 */
@ApiStatus.Internal
public final class RecipeDisplayLookup {

    private RecipeDisplayLookup() {
    }

    /**
     * @return the first recognized recipe (see {@link RecipeStationType}) whose output is {@code
     * target}, preferring a crafting-table recipe when more than one station can produce it
     * (enum declaration order), or {@code null} if none is found or {@code recipeManager} is
     * {@code null}.
     */
    @Nullable
    public static RecipeDisplay find(@Nullable ItemStack target, @Nullable RecipeManager recipeManager) {
        if (recipeManager == null || target == null || target.isEmpty()) {
            return null;
        }
        ResourceLocation targetId = MarieRegistryUtils.itemKey(target);
        if (targetId == null) {
            return null;
        }

        HolderLookup.Provider registries = registryAccess();
        RecipeDisplay best = null;
        int bestPriority = Integer.MAX_VALUE;
        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            Recipe<?> recipe = holder.value();
            RecipeStationType station = stationOf(recipe);
            if (station == null || station.ordinal() >= bestPriority) {
                continue;
            }
            ItemStack result = resultOf(recipe, registries);
            if (result == null || result.isEmpty() || !targetId.equals(MarieRegistryUtils.itemKey(result))) {
                continue;
            }
            best = build(station, recipe, result);
            bestPriority = station.ordinal();
        }
        return best;
    }

    @Nullable
    private static RecipeStationType stationOf(Recipe<?> recipe) {
        ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        return typeId == null ? null : RecipeStationType.byRecipeTypeId(typeId);
    }

    @Nullable
    private static ItemStack resultOf(Recipe<?> recipe, @Nullable HolderLookup.Provider registries) {
        try {
            return recipe.getResultItem(registries);
        } catch (Exception ignored) {
            // A recipe whose getResultItem needs a container-aware registry lookup this editor
            // doesn't provide (e.g. a suspicious-stew-style recipe) just isn't displayable here.
            return null;
        }
    }

    @Nullable
    private static HolderLookup.Provider registryAccess() {
        try {
            return Minecraft.getInstance().level != null ? Minecraft.getInstance().level.registryAccess() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static RecipeDisplay build(RecipeStationType station, Recipe<?> recipe, ItemStack result) {
        List<Ingredient> rawIngredients = recipe.getIngredients();
        List<List<ItemStack>> shapedGrid = station == RecipeStationType.CRAFTING_TABLE && recipe instanceof ShapedRecipe shaped
                ? buildShapedGrid(shaped, rawIngredients)
                : null;

        List<List<ItemStack>> ingredients = new ArrayList<>();
        for (Ingredient ingredient : rawIngredients) {
            List<ItemStack> alternatives = alternativesOf(ingredient);
            if (!alternatives.isEmpty()) {
                ingredients.add(alternatives);
            }
        }
        return new RecipeDisplay(station, shapedGrid, ingredients, result.copy());
    }

    /** Row-major 3x3 grid (an empty list for unused cells), placed at the pattern's own top-left. */
    @Nullable
    private static List<List<ItemStack>> buildShapedGrid(ShapedRecipe shaped, List<Ingredient> rawIngredients) {
        int width = shaped.getWidth();
        int height = shaped.getHeight();
        List<List<ItemStack>> grid = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            grid.add(List.of());
        }
        if (width <= 0 || height <= 0 || rawIngredients.size() != width * height) {
            // Pattern shape didn't match the ingredient list's size (an odd custom ShapedRecipe
            // subclass) — fall back to an unshaped flat list rather than guessing positions.
            return null;
        }
        for (int row = 0; row < height && row < 3; row++) {
            for (int col = 0; col < width && col < 3; col++) {
                grid.set(row * 3 + col, alternativesOf(rawIngredients.get(row * width + col)));
            }
        }
        return grid;
    }

    /** Every item the ingredient would actually accept — e.g. a tag like {@code c:crops/onion} can mean several different items (a raw onion, onion seeds, ...), not just one "representative" pick. */
    private static List<ItemStack> alternativesOf(Ingredient ingredient) {
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) {
            return List.of();
        }
        List<ItemStack> alternatives = new ArrayList<>(items.length);
        for (ItemStack item : items) {
            alternatives.add(item.copy());
        }
        return alternatives;
    }
}
