package dev.marie.framework.ui.itemeditor.recipe;

import dev.marie.framework.api.ApiStatus;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import javax.annotation.Nullable;

/**
 * A crafting station {@link RecipeDisplayLookup} knows how to recognize — one entry per {@code
 * RecipeType} registry id this feature supports, carrying the block {@link
 * RecipeStationBlockBox} renders for it.
 *
 * <p>An entry for an optional mod's recipe type/block (Farmer's Delight, or its "Dungeon's
 * Delight" addon) needs no {@code compileOnly} dependency on that mod: {@link
 * RecipeDisplayLookup} matches purely by the recipe type's registry id, and the block is resolved
 * purely by its registry id too (see {@link #block()}) — both are simply never registered when the
 * owning mod isn't installed, so neither is ever reached in the first place.
 */
@ApiStatus.Internal
public enum RecipeStationType {
    // Declaration order is also RecipeDisplayLookup's match priority (see its own doc) — every
    // "special" station comes before CRAFTING_TABLE deliberately: a plain crafting-table recipe is
    // the generic/ubiquitous one (most items have some crafting-table path, even a trivial one, as
    // JEI/EMI's own multi-recipe list for an item often shows), so it's the least informative choice
    // whenever an item *also* has a more specific recipe — a cooking pot, a furnace, a monster pot —
    // that actually says something about what that item is.
    FURNACE("minecraft:smelting", "minecraft:furnace"),
    SMOKER("minecraft:smoking", "minecraft:smoker"),
    BLAST_FURNACE("minecraft:blasting", "minecraft:blast_furnace"),
    CAMPFIRE("minecraft:campfire_cooking", "minecraft:campfire"),
    STONECUTTER("minecraft:stonecutting", "minecraft:stonecutter"),
    COOKING_POT("farmersdelight:cooking", "farmersdelight:cooking_pot"),
    CUTTING_BOARD("farmersdelight:cutting", "farmersdelight:cutting_board"),
    /** Dungeon's Delight's own cooking-pot-alike recipe type and block (its "monster pot"), not Farmer's Delight's own {@link #COOKING_POT} — a separate {@code RecipeType}/block even though the mod is an addon for Farmer's Delight. */
    MONSTER_COOKING("dungeonsdelight:monster_cooking", "dungeonsdelight:monster_pot"),
    CRAFTING_TABLE("minecraft:crafting", "minecraft:crafting_table");

    private final ResourceLocation recipeTypeId;
    private final ResourceLocation blockId;
    private final boolean prefersFlatIcon;

    RecipeStationType(String recipeTypeId, String blockId) {
        this(recipeTypeId, blockId, false);
    }

    RecipeStationType(String recipeTypeId, String blockId, boolean prefersFlatIcon) {
        this.recipeTypeId = ResourceLocation.parse(recipeTypeId);
        this.blockId = ResourceLocation.parse(blockId);
        this.prefersFlatIcon = prefersFlatIcon;
    }

    public ResourceLocation recipeTypeId() {
        return recipeTypeId;
    }

    /** Whether {@link RecipeStationBlockBox} should skip the 3D render and go straight to the flat item-icon fallback for this station's block — see the Create entries above for why. */
    public boolean prefersFlatIcon() {
        return prefersFlatIcon;
    }

    /** The station block, or {@code null} if it isn't registered (the owning mod isn't installed). */
    @Nullable
    public Block block() {
        Block block = BuiltInRegistries.BLOCK.get(blockId);
        return block == Blocks.AIR ? null : block;
    }

    @Nullable
    public static RecipeStationType byRecipeTypeId(ResourceLocation id) {
        for (RecipeStationType type : values()) {
            if (type.recipeTypeId.equals(id)) {
                return type;
            }
        }
        return null;
    }
}
