package dev.marie.framework.runtime;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.core.MarieContext;
import dev.marie.framework.scanner.ClassificationResult;
import dev.marie.framework.scanner.RecipeInheritanceResolver;
import dev.marie.framework.scanner.ScannerSpecRegistry;
import dev.marie.framework.scanner.stages.RecipeInheritanceStage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a snapshot of all sources with known value scores:
 * datapack tags (authoritative), then API or scanner cache entries.
 */
@ApiStatus.Experimental
public final class SourceCollector {

    private SourceCollector() {}

    /**
     * @return full classification results for every classified source; empty when nothing is classified
     */
    public static List<ClassificationResult> collectAllClassifiedSources() {
        return collectAllClassifiedSources(null);
    }

    /**
     * @param recipeManager when non-null, already-tagged items are merged with qualifying
     *                       recipe-inherited secondary scores; when null, behaves exactly like
     *                       {@link #collectAllClassifiedSources()}
     * @return full classification results for every classified source; empty when nothing is classified
     */
    public static List<ClassificationResult> collectAllClassifiedSources(@Nullable RecipeManager recipeManager) {
        List<ClassificationResult> out = new ArrayList<>();
        Map<ResourceLocation, ClassificationResult> classified = new HashMap<>();
        var excluded = ScannerSpecRegistry.get().excludedItems();
        // Merge every attached mod's tag-score provider instead of only the last-attached mod's.
        java.util.List<java.util.function.Function<Item, Map<String, Float>>> tagScoreProviders =
                dev.marie.framework.core.MarieModRegistry.getAll().stream()
                        .map(MarieContext::valueTagScoresProvider)
                        .toList();
        java.util.function.Function<Item, Map<String, Float>> tagScoresProvider = item -> {
            Map<String, Float> merged = new HashMap<>();
            for (var provider : tagScoreProviders) {
                merged.putAll(provider.apply(item));
            }
            return merged;
        };
        RecipeInheritanceResolver recipeResolver =
                recipeManager != null ? new RecipeInheritanceResolver(recipeManager) : null;

        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (!MarieContext.isSourceItemAllowed(stack)) {
                continue;
            }
            ResourceLocation itemId = item.builtInRegistryHolder().key().location();
            if (excluded.contains(itemId.toString())) {
                continue;
            }

            Map<String, Float> tagScores = tagScoresProvider.apply(item);
            if (!tagScores.isEmpty()) {
                Map<String, Float> mergedScores = recipeResolver != null
                        ? RecipeInheritanceStage.mergeTagAndRecipeScores(item, tagScores, recipeResolver, classified::get, tagScoresProvider)
                        : tagScores;
                ClassificationResult result = toClassificationResult(itemId, mergedScores, true);
                classified.put(itemId, result);
                out.add(result);
                continue;
            }

            Map<String, Float> external = SourceRegistry.getExternalClassification(itemId);
            if (external != null && !external.isEmpty()) {
                ClassificationResult result = toClassificationResult(itemId, Map.copyOf(external), false);
                classified.put(itemId, result);
                out.add(result);
            }
        }

        return List.copyOf(out);
    }

    private static ClassificationResult toClassificationResult(
            ResourceLocation itemId,
            Map<String, Float> scores,
            boolean tagClassified
    ) {
        List<Map.Entry<String, Float>> sorted = scores.entrySet().stream()
                .sorted(Comparator
                        .comparing(Map.Entry<String, Float>::getValue, Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .toList();

        if (sorted.isEmpty()) {
            return ClassificationResult.empty(itemId, "");
        }

        String dominant = sorted.get(0).getKey();
        String secondary = sorted.size() > 1 ? sorted.get(1).getKey() : null;
        float topScore = sorted.get(0).getValue();
        float secondScore = sorted.size() > 1 ? sorted.get(1).getValue() : 0f;
        float spread = topScore - secondScore;

        return new ClassificationResult(
                itemId,
                scores,
                dominant,
                secondary,
                spread,
                List.of(),
                false,
                tagClassified
        );
    }
}
