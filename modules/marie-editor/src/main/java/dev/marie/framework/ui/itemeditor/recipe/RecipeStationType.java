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
    // Create's own processing machines — e.g. its "createfood" addon crafts plated food items via
    // these (a compacting/mixing/pressing/deploying recipe alongside a plain crafting-table one for
    // the same output is common there), not Create's JEI-only "Automated Shapeless/Shaped Crafting"
    // categories: those aren't a distinct Recipe/RecipeType at all, just Create's own 3D rendering of
    // an ordinary minecraft:crafting recipe for its mechanical crafter, so there's nothing beyond the
    // CRAFTING_TABLE entry below for RecipeDisplayLookup to find for those.
    // Create's kinetic machines (unlike every entry above) split their model in two: the blockstate
    // only bakes the static frame, and the part that actually makes the block recognizable — the
    // Mixer's/Press's rotating head, the Millstone's grinding wheel, the Deployer's arm — is a
    // separate PartialModel drawn by a BlockEntityRenderer, which needs a real placed block entity
    // to animate and is invisible to BakedModel#getQuads entirely. RecipeStationBlockBox has no
    // level/block entity to give it, so a 3D render of these would only ever show the bare frame —
    // not wrong-sized so much as simply not the block a player would recognize — so these are
    // marked {@link #prefersFlatIcon()} to use the flat item icon (the complete, correct sprite)
    // instead of attempting a 3D render that structurally can't show the whole thing.
    CREATE_MIXING("create:mixing", "create:mechanical_mixer", true),
    CREATE_COMPACTING("create:compacting", "create:mechanical_press", true),
    CREATE_PRESSING("create:pressing", "create:mechanical_press", true),
    CREATE_MILLING("create:milling", "create:millstone", true),
    CREATE_DEPLOYING("create:deploying", "create:deployer", true),
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
