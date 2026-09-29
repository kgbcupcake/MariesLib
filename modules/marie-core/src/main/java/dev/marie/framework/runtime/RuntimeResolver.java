package dev.marie.framework.runtime;

import com.mojang.logging.LogUtils;
import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.cache.BoundedLRU;
import dev.marie.framework.cache.RunningAverage;
import dev.marie.framework.classification.ClassificationPipeline;
import dev.marie.framework.classification.ClassificationTrace;
import dev.marie.framework.classification.ClassificationTraceStep;
import dev.marie.framework.classification.TraceStepId;
import dev.marie.framework.classification.TraceStepStatus;
import dev.marie.framework.core.MarieContext;
import dev.marie.framework.core.MarieModRegistry;
import dev.marie.framework.diagnostics.MarieUnknownItemLogger;
import dev.marie.framework.scan.CacheStats;
import dev.marie.framework.scan.ResolutionResult;
import dev.marie.framework.scan.ResolutionStageHandler;
import dev.marie.framework.scan.RuntimeCascadeStage;
import dev.marie.framework.scan.RuntimeResolutionMerge;
import dev.marie.framework.scan.StageContext;
import dev.marie.framework.scan.StageMath;
import dev.marie.framework.scanner.ExcludedItemsRegistry;
import dev.marie.framework.scanner.RecipeInheritanceResolver;
import dev.marie.framework.scanner.ScannerSpecRegistry;
import dev.marie.framework.util.MarieRegistryUtils;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApiStatus.Internal
public final class RuntimeResolver {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int STAGE_COMMUNITY_TAG = 0;
    private static final int STAGE_KEYWORD_SUFFIX = 1;
    private static final int STAGE_RECIPE_INHERITANCE = 2;
    private static final int STAGE_NAMESPACE_PEER = 3;
    private static final int STAGE_HARD_FALLBACK = 4;

    private static final RuntimeResolver INSTANCE = new RuntimeResolver();

    public static RuntimeResolver getInstance() {
        return INSTANCE;
    }

    private final BoundedLRU<ResourceLocation, ResolutionResult> resolvedCache = new BoundedLRU<>();
    private final RecipeInheritanceResolver recipeInheritanceResolver = new RecipeInheritanceResolver(null);
    private final ConcurrentHashMap<String, RunningAverage> namespacePeers = new ConcurrentHashMap<>();
    private final RuntimeResolverStats stats = new RuntimeResolverStats();

    private RuntimeResolver() {}

    public RecipeInheritanceResolver recipeInheritanceResolver() {
        return recipeInheritanceResolver;
    }

    public void buildRecipeIndex(RecipeManager recipeManager) {
        recipeInheritanceResolver.clearCache();
        recipeInheritanceResolver.buildIndex(recipeManager);
    }

    public Map<String, Float> resolve(ItemStack stack, @Nullable RecipeManager recipeManager) {
        if (!MarieContext.isRegistered()) {
            return Map.of();
        }
        if (stack.isEmpty() || stack.getItem() == null) return Map.of();

        List<String> valueKeys = MarieContext.get().valueKeys();
        if (valueKeys.isEmpty()) return Map.of();

        Item item = stack.getItem();
        ResourceLocation itemId = MarieRegistryUtils.itemKey(item);
        if (itemId == null) return Map.of();

        if (!MarieContext.isSourceItemAllowed(stack)) return Map.of();

        ResolutionResult cached = resolvedCache.get(itemId);
        if (cached != null) {
            stats.recordHit();
            return cached.toValueMap();
        }

        stats.recordMiss();
        return resolveUncached(stack, itemId, recipeManager).toValueMap();
    }

    public @Nullable ResolutionResult resolveWithResult(ItemStack stack, @Nullable RecipeManager recipeManager) {
        if (!MarieContext.isRegistered()) {
            return null;
        }
        if (stack.isEmpty() || stack.getItem() == null) return null;

        List<String> valueKeys = MarieContext.get().valueKeys();
        if (valueKeys.isEmpty()) return null;

        Item item = stack.getItem();
        ResourceLocation itemId = MarieRegistryUtils.itemKey(item);
        if (itemId == null) return null;

        if (!MarieContext.isSourceItemAllowed(stack)) return null;

        ResolutionResult cached = resolvedCache.get(itemId);
        if (cached != null) {
            stats.recordHit();
            return cached.withCacheHit(true);
        }

        stats.recordMiss();
        return resolveUncached(stack, itemId, recipeManager);
    }

    public @Nullable ClassificationTrace resolveWithTrace(ItemStack stack, @Nullable RecipeManager recipeManager) {
        if (!MarieContext.isRegistered()) {
            return null;
        }
        if (stack.isEmpty() || stack.getItem() == null) return null;

        List<String> valueKeys = MarieContext.get().valueKeys();
        if (valueKeys.isEmpty()) return null;

        Item item = stack.getItem();
        ResourceLocation itemId = MarieRegistryUtils.itemKey(item);
        if (itemId == null) return null;

        boolean isResolvable = MarieContext.isSourceItemAllowed(stack);

        List<ClassificationTraceStep> traceOut = new ArrayList<>();

        Map<String, Object> discoveryDetail = new LinkedHashMap<>();
        discoveryDetail.put("itemId", itemId.toString());
        discoveryDetail.put("hasTag", false);
        if (!isResolvable) {
            discoveryDetail.put("errorCode", "NOT_RESOLVABLE");
        }
        traceOut.add(new ClassificationTraceStep(
                TraceStepId.ITEM_DISCOVERY,
                isResolvable ? TraceStepStatus.SUCCESS : TraceStepStatus.FAILURE,
                isResolvable ? "Source is resolvable" : "Source not resolvable",
                discoveryDetail));

        if (!isResolvable) {
            return ClassificationTrace.builder(itemId.toString(), ClassificationPipeline.RUNTIME)
                    .addStep(traceOut.get(0))
                    .summaryReason("Source not resolvable")
                    .build();
        }

        Map<String, Float> liveOverride = SourceRegistry.getExternalClassification(itemId);
        if (liveOverride != null && !liveOverride.isEmpty()) {
            boolean isApiClassification = SourceRegistry.hasApiClassification(itemId);
            String overrideSource = isApiClassification
                    ? "EXTERNAL_CLASSIFICATIONS (API/KubeJS)"
                    : "SCANNER_CLASSIFICATIONS (cached scan result)";
            Map<String, Object> overrideDetail = new LinkedHashMap<>();
            overrideDetail.put("source", overrideSource);
            overrideDetail.put("values", liveOverride);
            traceOut.add(new ClassificationTraceStep(
                    TraceStepId.EXTERNAL_CLASSIFICATION,
                    TraceStepStatus.SUCCESS,
                    "Gameplay will use this cached/external result, NOT the live inference below: " + liveOverride,
                    overrideDetail));
        }

        ResolutionResult cached = resolvedCache.get(itemId);
        if (cached != null) {
            stats.recordHit();
            Map<String, Object> cacheDetail = new LinkedHashMap<>();
            cacheDetail.put("cacheKey", itemId.toString());
            cacheDetail.put("hit", true);
            traceOut.add(new ClassificationTraceStep(
                    TraceStepId.RESOLVER_CACHE,
                    TraceStepStatus.SUCCESS,
                    "Cache hit",
                    cacheDetail));

            return buildTraceFromResult(itemId.toString(), cached.withCacheHit(true), traceOut);
        }

        stats.recordMiss();
        ResolutionResult result = resolveUncached(stack, itemId, recipeManager, traceOut);
        return buildTraceFromResult(itemId.toString(), result, traceOut);
    }

    private ClassificationTrace buildTraceFromResult(String itemId, ResolutionResult result,
                                                     List<ClassificationTraceStep> steps) {
        String dominant = null;
        float maxValue = 0f;
        float secondValue = 0f;
        for (Map.Entry<String, Float> entry : result.values().entrySet()) {
            float v = entry.getValue();
            if (v > maxValue) {
                secondValue = maxValue;
                maxValue = v;
                dominant = entry.getKey();
            } else if (v > secondValue) {
                secondValue = v;
            }
        }

        Map<String, Float> signalScores = result.rawScores().isEmpty() ? result.values() : result.rawScores();
        if (!signalScores.isEmpty()) {
            Map<String, Object> aggDetail = new LinkedHashMap<>();
            aggDetail.put("mergedScores", new LinkedHashMap<>(signalScores));
            aggDetail.put("stage", result.stage().name());
            if (!result.rejectedSignals().isEmpty()) {
                aggDetail.put("rejectedSignals", new LinkedHashMap<>(result.rejectedSignals()));
            }
            steps.add(new ClassificationTraceStep(
                    TraceStepId.SIGNAL_AGGREGATION,
                    TraceStepStatus.SUCCESS,
                    "Signals aggregated via " + result.stage().displayName(),
                    aggDetail));
        }

        if (dominant != null) {
            Map<String, Object> winnerDetail = new LinkedHashMap<>();
            winnerDetail.put("winner", dominant);
            winnerDetail.put("winnerScore", (double) maxValue);
            if (secondValue > 0f) {
                winnerDetail.put("runnerUpScore", (double) secondValue);
                winnerDetail.put("margin", (double) (maxValue - secondValue));
            }
            if (!result.rejectedSignals().isEmpty()) {
                winnerDetail.put("rejectedSignals", new LinkedHashMap<>(result.rejectedSignals()));
            }
            steps.add(new ClassificationTraceStep(
                    TraceStepId.WINNER_SELECTION,
                    TraceStepStatus.SUCCESS,
                    String.format(Locale.ROOT, "%s selected (score=%.2f)", dominant, maxValue),
                    winnerDetail));
        }

        float confidence = result.confidence();
        float spreadThreshold = StageMath.scannerConfidenceSpreadThreshold();
        boolean uncertain = dominant != null
                && result.stage() != RuntimeCascadeStage.HARD_FALLBACK
                && spreadThreshold > 0f
                && confidence < spreadThreshold;
        if (dominant != null) {
            Map<String, Object> confDetail = new LinkedHashMap<>();
            confDetail.put("spread", (double) confidence);
            confDetail.put("threshold", (double) spreadThreshold);
            confDetail.put("stage", result.stage().name());
            if (uncertain) confDetail.put("uncertain", true);
            steps.add(new ClassificationTraceStep(
                    TraceStepId.CONFIDENCE,
                    uncertain ? TraceStepStatus.WARNING : TraceStepStatus.SUCCESS,
                    uncertain
                            ? String.format(Locale.ROOT, "Confidence below threshold (spread=%.2f)", confidence)
                            : String.format(Locale.ROOT, "Confidence above threshold (spread=%.2f)", confidence),
                    confDetail));
        }

        return ClassificationTrace.builder(itemId, ClassificationPipeline.RUNTIME)
                .finalBars(result.values())
                .dominant(dominant)
                .cascadeStage(result.stage())
                .tagClassified(false)
                .uncertain(uncertain)
                .summaryReason(result.debugReason())
                .addSteps(steps)
                .build();
    }

    private ResolutionResult resolveUncached(ItemStack stack, ResourceLocation itemId, @Nullable RecipeManager recipeManager) {
        return resolveUncached(stack, itemId, recipeManager, null);
    }

    ResolutionResult resolveUncached(ItemStack stack, ResourceLocation itemId,
                                     @Nullable RecipeManager recipeManager,
                                     @Nullable List<ClassificationTraceStep> traceOut) {
        if (!MarieContext.isRegistered()) {
            return new ResolutionResult(
                    Map.of(), Map.of(), List.of(), Map.of(), Map.of(),
                    false, 0f, RuntimeCascadeStage.HARD_FALLBACK, "context_not_registered");
        }
        if (ScannerSpecRegistry.get().excludedItems().contains(itemId.toString())
                || ExcludedItemsRegistry.isExcluded(itemId.toString())) {
            return new ResolutionResult(
                    Map.of(), Map.of(), List.of(), Map.of(), Map.of(),
                    false, 0f, RuntimeCascadeStage.HARD_FALLBACK, "excluded_items");
        }
        long start = System.nanoTime();

        LOGGER.debug("[RuntimeResolver] cache miss entering inference pipeline: {}", itemId);

        if (traceOut != null) {
            Map<String, Object> cacheDetail = new LinkedHashMap<>();
            cacheDetail.put("cacheKey", itemId.toString());
            cacheDetail.put("hit", false);
            traceOut.add(new ClassificationTraceStep(
                    TraceStepId.RESOLVER_CACHE,
                    TraceStepStatus.SKIPPED,
                    "Cache miss — entering inference",
                    cacheDetail));
        }

        ResolutionStageHandler[] stages = mergedRuntimeResolverStages();

        List<String> valueKeys = MarieContext.get().valueKeys();
        Holder<Item> holder = stack.getItemHolder();
        Set<String> validKeys = Set.copyOf(valueKeys);
        StageContext ctx = new StageContext(holder, itemId, recipeManager, namespacePeers, validKeys, traceOut);

        if (stages.length > STAGE_COMMUNITY_TAG && stages[STAGE_COMMUNITY_TAG] != null) {
            stages[STAGE_COMMUNITY_TAG].resolve(itemId, ctx);
        }

        if (traceOut != null) {
            boolean communityContributed = !ctx.communityTagSignal().isEmpty();
            Map<String, Object> communityDetail = new LinkedHashMap<>();
            communityDetail.put("matched", new ArrayList<>(ctx.communityTagSignal().keySet()));
            communityDetail.put("contributions", new LinkedHashMap<>(ctx.communityTagSignal()));
            traceOut.add(new ClassificationTraceStep(
                    TraceStepId.COMMUNITY_TAG_SIGNAL,
                    communityContributed ? TraceStepStatus.SUCCESS : TraceStepStatus.SKIPPED,
                    communityContributed
                            ? "Community tags contributed " + ctx.communityTagSignal().size() + " signal(s)"
                            : "No community tag signals",
                    communityDetail));
        }

        ResolutionResult primary = stages.length > STAGE_KEYWORD_SUFFIX && stages[STAGE_KEYWORD_SUFFIX] != null
                ? stages[STAGE_KEYWORD_SUFFIX].resolve(itemId, ctx)
                : null;

        if (traceOut != null) {
            if (primary != null) {
                Map<String, Object> keywordDetail = new LinkedHashMap<>();
                keywordDetail.put("tokens", new ArrayList<>(primary.tokens()));
                keywordDetail.put("scores", new LinkedHashMap<>(primary.rawScores()));
                keywordDetail.put("spread", primary.confidence());
                keywordDetail.put("spreadThreshold", (double) StageMath.scannerConfidenceSpreadThreshold());
                keywordDetail.put("cascadeStage", primary.stage().name());
                boolean isComposite = primary.stage() == RuntimeCascadeStage.COMPOSITE;
                if (isComposite) {
                    keywordDetail.put("composite", true);
                }
                if (ctx.hasArchetypeMatches()) {
                    keywordDetail.put("archetypeMatches", new ArrayList<>(ctx.archetypeMatches()));
                }
                if (ctx.hasNegativeContributions()) {
                    keywordDetail.put("negativeContributions", new LinkedHashMap<>(ctx.negativeContributions()));
                }
                if (ctx.hasTokenDemotions()) {
                    keywordDetail.put("tokenDemotions", new LinkedHashMap<>(ctx.tokenDemotions()));
                }
                traceOut.add(new ClassificationTraceStep(
                        TraceStepId.KEYWORD_SUFFIX_SCORING,
                        TraceStepStatus.SUCCESS,
                        primary.debugReason(),
                        keywordDetail));
            } else {
                Map<String, Object> keywordDetail = new LinkedHashMap<>();
                keywordDetail.put("tokens", List.of());
                keywordDetail.put("scores", Map.of());
                keywordDetail.put("spread", 0.0);
                keywordDetail.put("spreadThreshold", (double) StageMath.scannerConfidenceSpreadThreshold());
                keywordDetail.put("rejectionReason", "NO_SIGNAL_MATCH");
                if (ctx.hasArchetypeMatches()) {
                    keywordDetail.put("archetypeMatches", new ArrayList<>(ctx.archetypeMatches()));
                }
                if (ctx.hasNegativeContributions()) {
                    keywordDetail.put("negativeContributions", new LinkedHashMap<>(ctx.negativeContributions()));
                }
                if (ctx.hasTokenDemotions()) {
                    keywordDetail.put("tokenDemotions", new LinkedHashMap<>(ctx.tokenDemotions()));
                }
                traceOut.add(new ClassificationTraceStep(
                        TraceStepId.KEYWORD_SUFFIX_SCORING,
                        TraceStepStatus.FAILURE,
                        "No keyword match",
                        keywordDetail));
            }
        }

        ResolutionResult recipeSupplement = stages.length > STAGE_RECIPE_INHERITANCE && stages[STAGE_RECIPE_INHERITANCE] != null
                ? stages[STAGE_RECIPE_INHERITANCE].resolve(itemId, ctx)
                : null;

        if (traceOut != null) {
            if (recipeSupplement != null) {
                Map<String, Object> recipeDetail = new LinkedHashMap<>();
                recipeDetail.put("recipeFound", true);
                recipeDetail.put("ingredientCount", recipeSupplement.values().size());
                recipeDetail.put("timeout", false);
                traceOut.add(new ClassificationTraceStep(
                        TraceStepId.RECIPE_LOOKUP,
                        TraceStepStatus.SUCCESS,
                        "Recipe supplement found",
                        recipeDetail));
            } else {
                Map<String, Object> recipeDetail = new LinkedHashMap<>();
                recipeDetail.put("recipeFound", false);
                recipeDetail.put("ingredientCount", 0);
                String failureReason = ctx.recipeFailureReason();
                recipeDetail.put("errorCode", failureReason != null ? failureReason : "UNKNOWN");
                recipeDetail.put("timeout", "RECIPE_TIMEOUT".equals(failureReason));
                traceOut.add(new ClassificationTraceStep(
                        TraceStepId.RECIPE_LOOKUP,
                        TraceStepStatus.SKIPPED,
                        "No recipe supplement: " + (failureReason != null ? failureReason : "UNKNOWN"),
                        recipeDetail));
            }
        }

        ResolutionResult result = RuntimeResolutionMerge.mergePrimaryWithRecipeSupplement(
                primary, recipeSupplement, ScannerSpecRegistry.contestableValues());

        if (traceOut != null) {
            if (result != null) {
                Map<String, Object> mergeDetail = new LinkedHashMap<>();
                List<String> keysAdded = new ArrayList<>();
                if (recipeSupplement != null && primary != null) {
                    for (String key : result.values().keySet()) {
                        if (!primary.values().containsKey(key)) {
                            keysAdded.add(key);
                        }
                    }
                }
                mergeDetail.put("keysAdded", keysAdded);
                if (recipeSupplement != null) {
                    mergeDetail.put("recipeContribution", new LinkedHashMap<>(recipeSupplement.values()));
                }
                traceOut.add(new ClassificationTraceStep(
                        TraceStepId.PRIMARY_RECIPE_MERGE,
                        TraceStepStatus.SUCCESS,
                        result.stage().displayName(),
                        mergeDetail));
            } else {
                Map<String, Object> mergeDetail = new LinkedHashMap<>();
                mergeDetail.put("keysAdded", List.of());
                traceOut.add(new ClassificationTraceStep(
                        TraceStepId.PRIMARY_RECIPE_MERGE,
                        TraceStepStatus.SKIPPED,
                        "No primary or recipe result to merge",
                        mergeDetail));
            }
        }

        if (result == null) {
            result = stages.length > STAGE_NAMESPACE_PEER && stages[STAGE_NAMESPACE_PEER] != null
                    ? stages[STAGE_NAMESPACE_PEER].resolve(itemId, ctx)
                    : null;
            if (traceOut != null) {
                String ns = itemId.getNamespace();
                RunningAverage peerAvg = namespacePeers.get(ns);
                int peerCount = peerAvg != null ? peerAvg.count() : 0;
                Map<String, Float> avg = peerAvg != null ? peerAvg.average() : Map.of();
                float peerSpread = RuntimeResolverStats.computeSpread(avg);
                Map<String, Object> peerDetail = new LinkedHashMap<>();
                peerDetail.put("peerCount", peerCount);
                peerDetail.put("peerSpread", (double) peerSpread);
                peerDetail.put("minPeerCount", 5);
                peerDetail.put("minSpread", 2.0);
                if (result != null) {
                    peerDetail.put("applied", true);
                    traceOut.add(new ClassificationTraceStep(
                            TraceStepId.NAMESPACE_PEER,
                            TraceStepStatus.SUCCESS,
                            "Namespace peer used",
                            peerDetail));
                } else {
                    peerDetail.put("applied", false);
                    String rejectionReason = peerCount < 5 ? "NAMESPACE_PEER_UNAVAILABLE: count < 5"
                            : peerSpread < 2.0f ? "NAMESPACE_PEER_UNAVAILABLE: spread < 2.0" : "NAMESPACE_PEER_UNAVAILABLE";
                    peerDetail.put("rejectionReason", rejectionReason);
                    traceOut.add(new ClassificationTraceStep(
                            TraceStepId.NAMESPACE_PEER,
                            TraceStepStatus.SKIPPED,
                            "Namespace peer unavailable",
                            peerDetail));
                }
            }
        }
        if (result == null) {
            result = stages.length > STAGE_HARD_FALLBACK && stages[STAGE_HARD_FALLBACK] != null
                    ? stages[STAGE_HARD_FALLBACK].resolve(itemId, ctx)
                    : new ResolutionResult(
                            Map.of(), Map.of(), List.of(), Map.of(), Map.of(),
                            false, 0f, RuntimeCascadeStage.HARD_FALLBACK, "unclassified");
            if (traceOut != null) {
                Map<String, Object> fallbackDetail = new LinkedHashMap<>();
                fallbackDetail.put("terminated", true);
                fallbackDetail.put("reason", "unclassified");
                fallbackDetail.put("errorCode", "UNCLASSIFIED");
                traceOut.add(new ClassificationTraceStep(
                        TraceStepId.HARD_FALLBACK,
                        TraceStepStatus.FAILURE,
                        "No classification path — unclassified",
                        fallbackDetail));
            }
        }

        resolvedCache.put(itemId, result);
        MarieUnknownItemLogger.log(result, itemId);

        if (result.stage() == RuntimeCascadeStage.COMMUNITY_TAG
                || result.stage() == RuntimeCascadeStage.KEYWORD_SUFFIX
                || result.stage() == RuntimeCascadeStage.COMPOSITE
                || result.stage() == RuntimeCascadeStage.COMPOSITE_RECIPE
                || result.stage() == RuntimeCascadeStage.KEYWORD_SUFFIX_RECIPE
                || result.stage() == RuntimeCascadeStage.RECIPE_INHERITANCE) {
            namespacePeers.computeIfAbsent(itemId.getNamespace(), k -> new RunningAverage())
                    .add(result.values());
        }

        LOGGER.debug("[RuntimeResolver] {} resolved via {} (confidence={}): {}",
                itemId, result.stage().displayName(), String.format("%.2f", result.confidence()), result.debugReason());

        long elapsed = System.nanoTime() - start;
        stats.recordTiming(elapsed, itemId);

        return result;
    }

    /**
     * {@code runtimeResolverStages()} is a fixed 5-slot pipeline override (community tag, keyword
     * suffix, recipe inheritance, namespace peer, hard fallback — see the {@code STAGE_*}
     * constants), one handler per slot, not an arbitrary list. For each slot, the first attached
     * mod (in registration order) that supplies a non-null handler wins, instead of only the
     * last-attached mod's array.
     */
    private static ResolutionStageHandler[] mergedRuntimeResolverStages() {
        ResolutionStageHandler[] merged = new ResolutionStageHandler[STAGE_HARD_FALLBACK + 1];
        for (MarieContext modCtx : MarieModRegistry.getAll()) {
            ResolutionStageHandler[] stages = modCtx.runtimeResolverStages();
            for (int i = 0; i < stages.length && i < merged.length; i++) {
                if (merged[i] == null && stages[i] != null) {
                    merged[i] = stages[i];
                }
            }
        }
        return merged;
    }

    public static void recordRecipeTimeout() {
        getInstance().stats.recordRecipeTimeout();
    }

    public void invalidateCache() {
        int size = resolvedCache.size();
        resolvedCache.clear();
        recipeInheritanceResolver.clearCache();
        namespacePeers.clear();
        stats.reset();
        LOGGER.info("[RuntimeResolver] Cache invalidated. Was: {} entries", size);
    }

    public CacheStats getCacheStats() {
        return stats.getCacheStats(resolvedCache.size());
    }
}
