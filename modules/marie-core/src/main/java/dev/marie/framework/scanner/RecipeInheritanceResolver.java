package dev.marie.framework.scanner;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.util.MarieRegistryUtils;
import dev.marie.framework.classification.ClassificationTraceStep;
import dev.marie.framework.classification.TraceStepId;
import dev.marie.framework.classification.TraceStepStatus;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Resolves recipe ingredient inheritance for source classification.
 * Server-side only component with strict performance constraints.
 *
 * <p>Constraints per spec:</p>
 * <ul>
 *   <li>Hard limit: depth 2, max 8 ingredients per recipe</li>
 *   <li>Skip recipes with more than 8 ingredients</li>
 *   <li>Cache all recipe lookups in HashMap</li>
 *   <li>Apply 0.5x confidence decay per level</li>
 * </ul>
 */
@ApiStatus.Experimental
public final class RecipeInheritanceResolver {

    /**
     * One ingredient lookup during recipe inheritance (for debug / telemetry).
     *
     * @param ingredientId    ingredient item id
     * @param depth           recursion depth (0 = direct recipe ingredients)
     * @param decayFactor     {@code pow(0.5, depth + 1)} applied to this hop
     * @param valueContributions per-value weighted contribution from this hop (after decay and ingredient count split)
     */
    public record RecipeInheritanceStep(
            ResourceLocation ingredientId,
            int depth,
            float decayFactor,
            Map<String, Float> valueContributions
    ) {
        public RecipeInheritanceStep {
            valueContributions = Map.copyOf(valueContributions);
        }
    }

    /**
     * One classified node encountered while walking the recipe-ingredient graph.
     *
     * @param nodeId ingredient item id that was classified
     * @param depth  walk depth at which this node was discovered (0 = direct ingredient of the root item)
     * @param value  non-null classifier result for this node
     * @param <T>    caller-defined classification type
     */
    public record NodeContribution<T>(ResourceLocation nodeId, int depth, T value) {}

    private static final int MAX_DEPTH = 2;
    private static final int MAX_INGREDIENTS = 8;
    private static final float DECAY_PER_LEVEL = 0.5f;

    private Map<ResourceLocation, List<ResourceLocation>> recipeIndex;
    private Map<ResourceLocation, List<List<ResourceLocation>>> recipesByOutput = Map.of();
    @Nullable
    private volatile RecipeManager recipeManager;
    @Nullable
    private final BiFunction<Map<String, Float>, Float, Map<String, Float>> inheritanceFilter;

    public RecipeInheritanceResolver(@Nullable RecipeManager recipeManager) {
        this(recipeManager, null);
    }

    public RecipeInheritanceResolver(
            @Nullable RecipeManager recipeManager,
            @Nullable BiFunction<Map<String, Float>, Float, Map<String, Float>> inheritanceFilter
    ) {
        this.recipeManager = recipeManager;
        this.inheritanceFilter = inheritanceFilter;
        this.recipeIndex = buildIndex();
    }

    private Map<ResourceLocation, List<ResourceLocation>> buildIndex() {
        if (recipeManager == null) {
            return new HashMap<>();
        }
        Map<ResourceLocation, List<ResourceLocation>> index = new HashMap<>();
        Map<ResourceLocation, java.util.TreeMap<String, List<ResourceLocation>>> perRecipe = new HashMap<>();
        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            Recipe<?> recipe = holder.value();
            try {
                ItemStack result = recipe.getResultItem(null);
                if (result == null || result.isEmpty()) {
                    continue;
                }
                ResourceLocation outputId = MarieRegistryUtils.itemKey(result);
                if (outputId == null) {
                    continue;
                }
                List<Ingredient> recipeIngredients = recipe.getIngredients();
                if (recipeIngredients.size() > MAX_INGREDIENTS) {
                    continue;
                }
                List<ResourceLocation> single = new ArrayList<>();
                for (Ingredient ingredient : recipeIngredients) {
                    ItemStack[] items = ingredient.getItems();
                    if (items.length == 0) {
                        continue;
                    }
                    ResourceLocation id = MarieRegistryUtils.itemKey(items[0]);
                    if (id == null || id.equals(outputId)) {
                        continue;
                    }
                    index.computeIfAbsent(outputId, k -> new ArrayList<>()).add(id);
                    single.add(id);
                }
                if (!single.isEmpty()) {
                    perRecipe.computeIfAbsent(outputId, k -> new java.util.TreeMap<>())
                            .put(holder.id().toString(), List.copyOf(single));
                }
            } catch (Exception ignored) {
            }
        }
        index.replaceAll((k, v) -> v.size() > MAX_INGREDIENTS ? v.subList(0, MAX_INGREDIENTS) : v);
        Map<ResourceLocation, List<List<ResourceLocation>>> byOutput = new HashMap<>();
        perRecipe.forEach((k, v) -> byOutput.put(k, List.copyOf(v.values())));
        recipesByOutput = byOutput;
        return index;
    }

    /**
     * Resolve recipe inheritance for an item.
     *
     * @param item The item to analyze
     * @param classifiedLookup Function to lookup already-classified items
     * @return Value contributions from recipe ingredients
     */
    public Map<String, Float> resolve(
            Item item,
            Function<ResourceLocation, ClassificationResult> classifiedLookup
    ) {
        return resolve(item, classifiedLookup, null);
    }

    /**
     * Same as {@link #resolve(Item, Function)}; when {@code traceOut} is non-null, appends a
     * {@link RecipeInheritanceStep} for each ingredient branch that resolves from a confident classification.
     */
    public Map<String, Float> resolve(
            Item item,
            Function<ResourceLocation, ClassificationResult> classifiedLookup,
            @Nullable List<RecipeInheritanceStep> traceOut
    ) {
        return resolve(item, classifiedLookup, traceOut, null);
    }

    /**
     * Same as above but also emits ClassificationTraceStep for trace diagnostics.
     */
    public Map<String, Float> resolve(
            Item item,
            Function<ResourceLocation, ClassificationResult> classifiedLookup,
            @Nullable List<RecipeInheritanceStep> traceOut,
            @Nullable List<ClassificationTraceStep> classificationTraceOut
    ) {
        if (recipeManager == null) {
            return Map.of();
        }

        ResourceLocation itemId = MarieRegistryUtils.itemKey(item);
        if (itemId == null) {
            return Map.of();
        }

        List<ResourceLocation> ingredients = getIngredients(itemId);

        if (classificationTraceOut != null) {
            Map<String, Object> recipeDetail = new LinkedHashMap<>();
            recipeDetail.put("recipeFound", !ingredients.isEmpty());
            recipeDetail.put("ingredientCount", ingredients.size());
            recipeDetail.put("timeout", false);
            classificationTraceOut.add(new ClassificationTraceStep(
                    TraceStepId.RECIPE_LOOKUP,
                    ingredients.isEmpty() ? TraceStepStatus.FAILURE : TraceStepStatus.SUCCESS,
                    ingredients.isEmpty() ? "No recipe found" : "Found recipe with " + ingredients.size() + " ingredient(s)",
                    recipeDetail));
        }

        Map<String, Float> aggregated = resolveRecursive(itemId, classifiedLookup, 0, new HashMap<>(), traceOut, classificationTraceOut);
        return inheritanceFilter != null ? inheritanceFilter.apply(aggregated, 0f) : aggregated;
    }

    private Map<String, Float> resolveRecursive(
            ResourceLocation itemId,
            Function<ResourceLocation, ClassificationResult> classifiedLookup,
            int depth,
            Map<ResourceLocation, Boolean> visited,
            @Nullable List<RecipeInheritanceStep> traceOut
    ) {
        return resolveRecursive(itemId, classifiedLookup, depth, visited, traceOut, null);
    }

    private Map<String, Float> resolveRecursive(
            ResourceLocation itemId,
            Function<ResourceLocation, ClassificationResult> classifiedLookup,
            int depth,
            Map<ResourceLocation, Boolean> visited,
            @Nullable List<RecipeInheritanceStep> traceOut,
            @Nullable List<ClassificationTraceStep> classificationTraceOut
    ) {
        if (depth >= MAX_DEPTH) {
            return Map.of();
        }

        if (visited.containsKey(itemId)) {
            if (classificationTraceOut != null) {
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("ingredientId", itemId.toString());
                detail.put("source", "NONE");
                detail.put("skipped", true);
                detail.put("skipReason", "CYCLE_DETECTED");
                classificationTraceOut.add(new ClassificationTraceStep(
                        TraceStepId.INGREDIENT_RESOLUTION,
                        TraceStepStatus.SKIPPED,
                        "Ingredient skipped: cycle detected",
                        detail));
            }
            return Map.of();
        }
        visited.put(itemId, true);

        List<ResourceLocation> ingredients = getIngredients(itemId);
        if (ingredients.isEmpty()) {
            return Map.of();
        }

        Map<String, Float> contributions = new HashMap<>();
        float decayFactor = (float) Math.pow(DECAY_PER_LEVEL, depth + 1);
        int n = ingredients.size();

        for (ResourceLocation ingredientId : ingredients) {
            ClassificationResult result = classifiedLookup.apply(ingredientId);
            if (result != null && !result.uncertain()) {
                Map<String, Float> stepContribs = new HashMap<>();
                for (Map.Entry<String, Float> e : result.scores().entrySet()) {
                    float contribution = e.getValue() * decayFactor / n;
                    contributions.merge(e.getKey(), contribution, Float::sum);
                    stepContribs.put(e.getKey(), contribution);
                }
                if (traceOut != null) {
                    traceOut.add(new RecipeInheritanceStep(ingredientId, depth, decayFactor, stepContribs));
                }
                if (classificationTraceOut != null) {
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("ingredientId", ingredientId.toString());
                    detail.put("source", result.tagClassified() ? "TAG" : "SCANNER");
                    detail.put("values", new LinkedHashMap<>(stepContribs));
                    if (result.uncertain()) {
                        detail.put("uncertain", true);
                    }
                    classificationTraceOut.add(new ClassificationTraceStep(
                            TraceStepId.INGREDIENT_RESOLUTION,
                            TraceStepStatus.SUCCESS,
                            "Ingredient classified via " + (result.tagClassified() ? "TAG" : "SCANNER"),
                            detail));
                }
            } else {
                if (classificationTraceOut != null && result == null) {
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("ingredientId", ingredientId.toString());
                    detail.put("source", "NONE");
                    detail.put("warningCode", "INGREDIENT_UNCLASSIFIED");
                    classificationTraceOut.add(new ClassificationTraceStep(
                            TraceStepId.INGREDIENT_RESOLUTION,
                            TraceStepStatus.FAILURE,
                            "Ingredient unclassified",
                            detail));
                } else if (classificationTraceOut != null && result != null && result.uncertain()) {
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("ingredientId", ingredientId.toString());
                    detail.put("source", "SCANNER");
                    detail.put("uncertain", true);
                    detail.put("warningCode", "INGREDIENT_UNCLASSIFIED");
                    classificationTraceOut.add(new ClassificationTraceStep(
                            TraceStepId.INGREDIENT_RESOLUTION,
                            TraceStepStatus.WARNING,
                            "Ingredient classification uncertain",
                            detail));
                }
                Map<String, Float> inherited = resolveRecursive(ingredientId, classifiedLookup, depth + 1, visited, traceOut, classificationTraceOut);
                for (Map.Entry<String, Float> e : inherited.entrySet()) {
                    contributions.merge(e.getKey(), e.getValue() / n, Float::sum);
                }
            }
        }

        return contributions;
    }

    /**
     * Walks the recipe-ingredient graph rooted at {@code rootItemId} to {@link #MAX_DEPTH} and invokes
     * {@code nodeClassifier} once per distinct node in range, collecting every non-null result.
     *
     * <p>Unlike {@link #resolve(Item, Function)} — which delegates to {@code resolveRecursive} and stops
     * descending a branch as soon as a node classifies confidently (a non-null, non-uncertain result) —
     * this method never short-circuits. It always visits every node within {@code MAX_DEPTH}. That
     * short-circuit is the confirmed root cause of the "calzone" class of bug: a weak keyword-fallback
     * match on an intermediate node (e.g. a generic dough / bread ingredient) classified "confidently
     * enough" to end the branch, so a stronger tag-based match one hop further down (the real filling)
     * was never visited and never contributed. Callers that need the full picture — and want to choose
     * the strongest signal themselves — use this method instead of {@code resolve}.</p>
     *
     * <p>The root item is not classified: only the graph <em>below</em> {@code rootItemId} is walked,
     * matching {@code resolveRecursive}'s convention where the root is the caller's own responsibility.</p>
     *
     * <p>A single global visited guard — the same {@code Map<ResourceLocation, Boolean>} pattern as the
     * {@code resolveRecursive} cycle guard — is checked before {@code nodeClassifier} runs and marked
     * immediately after. One mechanism covers both a node that appears multiple times in one ingredient
     * list and a node reachable through more than one branch: each distinct node is classified exactly
     * once, at the shallowest depth it is reached. When {@code nodeClassifier} returns {@code null} for a
     * node, that node is still walked through — its own ingredients are visited, subject to depth and the
     * visited guard — but nothing is recorded for it.</p>
     *
     * <p>Entries are returned in walk order (depth 0 before depth 1; ingredient-list order within a
     * node). The list is empty when no recipe index is available.</p>
     *
     * <pre>{@code
     * List<NodeContribution<ClassificationResult>> hits =
     *         resolver.collectContributions(calzoneId, classifier::classify);
     * // every confident match in range is present - pick the strongest yourself instead of
     * // taking whichever one happened to appear first
     * for (NodeContribution<ClassificationResult> hit : hits) {
     *     if (!hit.value().uncertain()) {
     *         merge(hit.value().scores(), hit.depth());
     *     }
     * }
     * }</pre>
     *
     * @param rootItemId     item whose ingredient graph is walked; not classified itself
     * @param nodeClassifier caller-supplied per-node classifier; may return {@code null} to skip recording
     * @param <T>            caller-defined classification type
     * @return non-null classifier results in walk order, one per distinct classified node; never {@code null}
     */
    public <T> List<NodeContribution<T>> collectContributions(
            ResourceLocation rootItemId,
            Function<ResourceLocation, T> nodeClassifier
    ) {
        List<NodeContribution<T>> out = new ArrayList<>();
        if (rootItemId == null) {
            return out;
        }
        // root is the caller's responsibility: seed it as visited so it is never classified or revisited
        Map<ResourceLocation, Boolean> visited = new HashMap<>();
        visited.put(rootItemId, true);
        collectRecursive(rootItemId, nodeClassifier, 0, visited, out);
        return out;
    }

    private <T> void collectRecursive(
            ResourceLocation itemId,
            Function<ResourceLocation, T> nodeClassifier,
            int depth,
            Map<ResourceLocation, Boolean> visited,
            List<NodeContribution<T>> out
    ) {
        // same depth gate as resolveRecursive: depth 0 sees the root's direct ingredients, depth 1 theirs
        if (depth >= MAX_DEPTH) {
            return;
        }
        for (ResourceLocation ingredientId : getIngredients(itemId)) {
            // global visited guard: covers duplicate ingredients and cross-branch repeats in one check
            if (visited.containsKey(ingredientId)) {
                continue;
            }
            visited.put(ingredientId, true);
            T value = nodeClassifier.apply(ingredientId);
            if (value != null) {
                out.add(new NodeContribution<>(ingredientId, depth, value));
            }
            // walk through even on a null result so a strong match further down is still visited
            collectRecursive(ingredientId, nodeClassifier, depth + 1, visited, out);
        }
    }

    /** All recipes' ingredients pooled together; see {@link #getRecipes} to count each recipe on its own. */
    public List<ResourceLocation> getIngredients(ResourceLocation itemId) {
        return recipeIndex.getOrDefault(itemId, List.of());
    }

    /** One ingredient list per recipe for {@code itemId}, ordered by recipe id; repeats within a recipe are kept. */
    public List<List<ResourceLocation>> getRecipes(ResourceLocation itemId) {
        return recipesByOutput.getOrDefault(itemId, List.of());
    }

    /**
     * Finds what the given raw item cooks into by checking furnace, smoker, and campfire recipes.
     * Returns the first cooked output found, or {@code null} if no cooking recipe exists.
     *
     * @param rawItemId the raw item's registry ID
     * @return the cooked output item ID, or {@code null} if no cooking recipe found
     */
    @Nullable
    public ResourceLocation findCookedOutput(ResourceLocation rawItemId) {
        if (recipeManager == null) {
            return null;
        }

        Item rawItem = BuiltInRegistries.ITEM.get(rawItemId);
        if (rawItem == null) {
            return null;
        }

        ItemStack rawStack = new ItemStack(rawItem);

        // Check smelting recipes (furnace)
        for (RecipeHolder<SmeltingRecipe> holder : recipeManager.getAllRecipesFor(RecipeType.SMELTING)) {
            try {
                SmeltingRecipe recipe = holder.value();
                if (recipe.getIngredients().stream().anyMatch(ing -> ing.test(rawStack))) {
                    ItemStack result = recipe.getResultItem(null);
                    if (result != null && !result.isEmpty()) {
                        return MarieRegistryUtils.itemKey(result);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // Check smoking recipes
        for (RecipeHolder<SmokingRecipe> holder : recipeManager.getAllRecipesFor(RecipeType.SMOKING)) {
            try {
                SmokingRecipe recipe = holder.value();
                if (recipe.getIngredients().stream().anyMatch(ing -> ing.test(rawStack))) {
                    ItemStack result = recipe.getResultItem(null);
                    if (result != null && !result.isEmpty()) {
                        return MarieRegistryUtils.itemKey(result);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // Check campfire recipes
        for (RecipeHolder<CampfireCookingRecipe> holder : recipeManager.getAllRecipesFor(RecipeType.CAMPFIRE_COOKING)) {
            try {
                CampfireCookingRecipe recipe = holder.value();
                if (recipe.getIngredients().stream().anyMatch(ing -> ing.test(rawStack))) {
                    ItemStack result = recipe.getResultItem(null);
                    if (result != null && !result.isEmpty()) {
                        return MarieRegistryUtils.itemKey(result);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    /**
     * Sets the active recipe manager and prepares this resolver for use.
     * Does not clear the cache — call {@link #clearCache()} first if needed.
     */
    public void buildIndex(RecipeManager recipeManager) {
        this.recipeManager = recipeManager;
        this.recipeIndex = buildIndex();
    }

    /**
     * Clear the recipe cache. Call when mod list changes.
     */
    public void clearCache() {
        // index is immutable; construct a new instance to refresh
    }

    /**
     * Get the current index size for diagnostics.
     */
    public int cacheSize() {
        return recipeIndex.size();
    }
}
