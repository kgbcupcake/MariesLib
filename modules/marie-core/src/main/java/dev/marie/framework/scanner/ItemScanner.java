package dev.marie.framework.scanner;

// High-level orchestrator — the correct entry point for scanning all loaded items.

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.api.value.ValueDefinition;
import dev.marie.framework.api.registry.ValueRegistry;
import dev.marie.framework.core.MarieCore;
import dev.marie.framework.core.IMarieConfig;
import dev.marie.framework.core.MarieContext;
import dev.marie.framework.runtime.SourceRegistry;
import dev.marie.framework.util.MarieRegistryUtils;
import dev.marie.framework.classification.ClassificationTraceStep;
import dev.marie.framework.scanner.analysis.MultiValueAnalysisPipeline;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Production-grade heuristic source classification engine for NeoForge 1.21.1.
 *
 * <p>Pipeline stages:</p>
 * <ol>
 *   <li>Registry Scan - Find items matching {@link MarieContext#sourceItemFilter()}</li>
 *   <li>Signal Analysis - Multi-signal, weighted classification</li>
 *   <li>Confidence Validation - Spread-based, not threshold-based</li>
 *   <li>Tag Recommendation - Auto-generate JSON entries for high-confidence hits</li>
 *   <li>Report Output - Human-readable .txt AND machine-readable .json</li>
 * </ol>
 *
 * <p>This is a developer-facing tool, not player-facing. Zero impact on normal gameplay.</p>
 */
@ApiStatus.Experimental
public final class ItemScanner {

    private static final float DEFAULT_SPREAD_THRESHOLD = 0f;
    private static final boolean DEFAULT_ENABLE_RECIPE_INHERITANCE = false;
    private static final float SCANNER_CLASSIFICATION_AMOUNT = 0.15f;

    private static volatile ScanCache cache;
    private static volatile boolean initialized = false;

    /**
     * Represents a single scan hit for an unassigned source item.
     *
     * @param itemId The item's registry ID
     * @param fallbackValue The fallback value category assigned
     * @param result The full classification result (nullable for backward compat)
     * @param trace The classification trace steps (nullable)
     */
    public record ScanHit(
            ResourceLocation itemId,
            String fallbackValue,
            @Nullable ClassificationResult result,
            @Nullable List<ClassificationTraceStep> trace
    ) {
        public ScanHit(ResourceLocation itemId, String fallbackValue) {
            this(itemId, fallbackValue, null, null);
        }

        public ScanHit(ResourceLocation itemId, String fallbackValue, @Nullable ClassificationResult result) {
            this(itemId, fallbackValue, result, null);
        }
    }

    /**
     * Scan options for customizing behavior.
     */
    public record ScanOptions(
            boolean enableRecipeInheritance,
            float confidenceSpreadThreshold,
            boolean writeReports,
            boolean writeRecommendations,
            boolean collectTraces,
            @Nullable RecipeManager recipeManager,
            @Nullable Consumer<String> progressCallback
    ) {
        public static ScanOptions defaults() {
            boolean recipeInheritance = DEFAULT_ENABLE_RECIPE_INHERITANCE;
            float spreadThreshold = DEFAULT_SPREAD_THRESHOLD;
            try {
                recipeInheritance = IMarieConfig.get().scannerEnableRecipeInheritance();
                spreadThreshold = IMarieConfig.get().scannerConfidenceSpreadThreshold();
            } catch (IllegalStateException ignored) {
                // Config not initialized yet; keep safe zero-default behavior.
            }
            return new ScanOptions(
                    recipeInheritance,
                    spreadThreshold,
                    true,
                    true,
                    false,
                    null,
                    null
            );
        }

        public ScanOptions withRecipeManager(RecipeManager rm) {
            return new ScanOptions(enableRecipeInheritance, confidenceSpreadThreshold,
                    writeReports, writeRecommendations, collectTraces, rm, progressCallback);
        }

        public ScanOptions withProgressCallback(Consumer<String> callback) {
            return new ScanOptions(enableRecipeInheritance, confidenceSpreadThreshold,
                    writeReports, writeRecommendations, collectTraces, recipeManager, callback);
        }

        public ScanOptions withThreshold(float threshold) {
            return new ScanOptions(enableRecipeInheritance, threshold,
                    writeReports, writeRecommendations, collectTraces, recipeManager, progressCallback);
        }

        public ScanOptions withRecipeInheritance(boolean enabled) {
            return new ScanOptions(enabled, confidenceSpreadThreshold,
                    writeReports, writeRecommendations, collectTraces, recipeManager, progressCallback);
        }

        public ScanOptions withReports(boolean enabled) {
            return new ScanOptions(enableRecipeInheritance, confidenceSpreadThreshold,
                    enabled, writeRecommendations, collectTraces, recipeManager, progressCallback);
        }

        public ScanOptions withRecommendations(boolean enabled) {
            return new ScanOptions(enableRecipeInheritance, confidenceSpreadThreshold,
                    writeReports, enabled, collectTraces, recipeManager, progressCallback);
        }

        public ScanOptions withTraces(boolean enabled) {
            return new ScanOptions(enableRecipeInheritance, confidenceSpreadThreshold,
                    writeReports, writeRecommendations, enabled, recipeManager, progressCallback);
        }
    }

    /**
     * Full scan result including all classification data.
     */
    public record ScanResult(
            List<ScanHit> hits,
            List<ClassificationResult> allResults,
            ScanCache.ScanSummary summary,
            @Nullable ScanCache.ScanDiff diff,
            Map<ResourceLocation, List<ClassificationTraceStep>> traces
    ) {
        public ScanResult(
                List<ScanHit> hits,
                List<ClassificationResult> allResults,
                ScanCache.ScanSummary summary,
                @Nullable ScanCache.ScanDiff diff
        ) {
            this(hits, allResults, summary, diff, Map.of());
        }
    }

    private ItemScanner() {}

    // ─────────────────────────────────────────────────────────────────────────────
    // Public API - Backward Compatible
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Simple scan for unassigned sources (backward compatible).
     * Returns items that have no value tag.
     */
    public static List<ScanHit> scan() {
        ScanResult result = scanFull(ScanOptions.defaults().withReports(false).withRecommendations(false));
        return result.hits();
    }

    /**
     * Check if an item stack has an authoritative datapack value tag or classification.
     */
    public static boolean hasValueTag(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation itemId = MarieRegistryUtils.itemKey(stack);
        if (itemId == null) {
            return false;
        }
        if (SourceRegistry.hasAuthoritativeClassification(itemId)) {
            return true;
        }
        String modId = IMarieConfig.get().modId();
        for (ValueDefinition valueDef : ValueRegistry.getAll()) {
            String valueKey = valueDef.getId();
            TagKey<Item> tag = TagKey.create(
                    Registries.ITEM,
                    ResourceLocation.fromNamespaceAndPath(modId, "values/" + valueKey));
            if (stack.is(tag)) {
                return true;
            }
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Extended API
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Run a full scan with custom options, asynchronously.
     */
    public static CompletableFuture<ScanResult> scanAsync(ScanOptions options) {
        return CompletableFuture.supplyAsync(() -> scanFull(options));
    }

    /**
     * Runs a full scan off-thread, then applies only confident classifications via
     * {@link SourceRegistry#applyFromScanner(Map)}.
     */
    public static void scanAndApply(RecipeManager recipeManager) {
        ScanOptions options = ScanOptions.defaults()
                .withRecipeManager(recipeManager)
                .withReports(false)
                .withRecommendations(false);
        scanAsync(options)
                .thenAccept(ItemScanner::applyScanResult)
                .exceptionally(ex -> {
                    MarieCore.LOGGER.warn("[ItemScanner] scanAndApply failed", ex);
                    return null;
                });
    }

    /**
     * Runs and applies the scanner on the calling thread. Intended for commands that
     * need a report based on current classifications rather than a later async pass.
     */
    public static ScanResult scanAndApplyNow(RecipeManager recipeManager) {
        ScanOptions options = ScanOptions.defaults()
                .withRecipeManager(recipeManager)
                .withReports(false)
                .withRecommendations(false);
        ScanResult result = scanFull(options);
        applyScanResult(result);
        return result;
    }

    private static void applyScanResult(ScanResult result) {
        Map<ResourceLocation, Map<String, Float>> valueMap = new HashMap<>();
        int confident = 0;
        int uncertain = 0;
        for (ClassificationResult r : result.allResults()) {
            if (r.uncertain()) {
                uncertain++;
            } else {
                confident++;
                Map<String, Float> toApply;
                if (isComposite(r) && r.values() != null && !r.values().isEmpty()) {
                    float top = r.values().values().stream().max(Float::compare).orElse(0f);
                    float threshold = top * IMarieConfig.get().compositeRatioThreshold();
                    Map<String, Float> composite = new HashMap<>();
                    for (Map.Entry<String, Float> e : r.values().entrySet()) {
                        if (e.getValue() >= threshold) {
                            composite.put(e.getKey(), e.getValue());
                        }
                    }
                    toApply = composite.isEmpty()
                            ? Map.of(r.dominant(), SCANNER_CLASSIFICATION_AMOUNT)
                            : composite;
                } else if (r.dominant() != null) {
                    toApply = Map.of(r.dominant(), SCANNER_CLASSIFICATION_AMOUNT);
                } else {
                    continue;
                }
                valueMap.put(r.itemId(), toApply);
            }
        }
        SourceRegistry.applyFromScanner(valueMap);
        MarieCore.LOGGER.info(
                "[ItemScanner] scanAndApply complete: {} confident, {} uncertain",
                confident,
                uncertain);
    }

    private static boolean isComposite(ClassificationResult result) {
        Map<String, Float> values = result.values();
        if (values == null || values.size() < 2) {
            return false;
        }

        float top = 0f;
        float second = 0f;
        for (Float value : values.values()) {
            if (value == null || value <= 0f) {
                continue;
            }
            if (value > top) {
                second = top;
                top = value;
            } else if (value > second) {
                second = value;
            }
        }

        return top > 0f
                && second > 0f
                && second / top >= IMarieConfig.get().compositeRatioThreshold();
    }

    /**
     * Run a full scan with custom options, synchronously.
     */
    public static ScanResult scanFull(ScanOptions options) {
        ensureInitialized();

        Consumer<String> progress = options.progressCallback() != null
                ? options.progressCallback()
                : msg -> {};

        progress.accept("Starting source scan...");

        List<String> valueKeys = ValueRegistry.getAll().stream().map(ValueDefinition::getId).toList();
        String fallbackKey = valueKeys.isEmpty() ? "" : valueKeys.get(0);

        RecipeInheritanceResolver recipeResolver = null;
        if (options.enableRecipeInheritance() && options.recipeManager() != null) {
            recipeResolver = new RecipeInheritanceResolver(options.recipeManager());
        }

        ItemClassifier classifier = new ItemClassifier(
                valueKeys,
                options.enableRecipeInheritance() && recipeResolver != null,
                options.confidenceSpreadThreshold(),
                recipeResolver
        );

        progress.accept("Scanning item registry...");

        List<Item> sourceItems = new ArrayList<>();
        int alreadyTagged = 0;

        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (!passesSourceFilter(stack)) {
                continue;
            }

            if (hasValueTag(stack)) {
                alreadyTagged++;
                continue;
            }

            sourceItems.add(item);
        }

        progress.accept("Found " + sourceItems.size() + " untagged source items...");

        Map<ResourceLocation, ClassificationResult> classifiedResults = new ConcurrentHashMap<>();
        Map<ResourceLocation, List<ClassificationTraceStep>> traceMap = options.collectTraces() ? new ConcurrentHashMap<>() : Map.of();
        Map<String, Map<String, Float>> namespaceAverages = new HashMap<>();

        progress.accept("Running classification pass 1 (without namespace peers)...");
        for (Item item : sourceItems) {
            ResourceLocation itemId = MarieRegistryUtils.itemKey(item);
            if (itemId == null) continue;

            ClassificationResult cached = cache.get(itemId);
            if (cached != null) {
                classifiedResults.put(itemId, cached);
                continue;
            }

            ClassificationResult result = classifier.classify(
                    item,
                    classifiedResults::get,
                    Map.of()
            );
            classifiedResults.put(itemId, result);
            cache.put(itemId, result);
        }

        progress.accept("Computing namespace averages...");
        namespaceAverages = ItemClassifier.computeNamespaceAverages(classifiedResults);

        progress.accept("Running classification pass 2 (with namespace peers)...");
        for (Item item : sourceItems) {
            ResourceLocation itemId = MarieRegistryUtils.itemKey(item);
            if (itemId == null) continue;

            List<ClassificationTraceStep> traceOut = options.collectTraces() ? new ArrayList<>() : null;
            Optional<List<ClassificationTraceStep>> traceOpt = traceOut != null ? Optional.of(traceOut) : Optional.empty();

            ClassificationResult result = classifier.classify(
                    item,
                    classifiedResults::get,
                    namespaceAverages,
                    traceOpt
            );
            classifiedResults.put(itemId, result);
            cache.put(itemId, result);

            if (traceOut != null && !traceOut.isEmpty()) {
                traceMap.put(itemId, traceOut);
            }
        }

        List<ClassificationResult> allResults = new ArrayList<>(classifiedResults.values());
        List<ScanHit> hits = new ArrayList<>();

        int autoClassified = 0;
        int uncertain = 0;

        ConcurrentHashMap<ResourceLocation, String> dominantCategories = new ConcurrentHashMap<>();

        for (ClassificationResult r : allResults) {
            String dominant = r.dominant() != null ? r.dominant() : fallbackKey;
            dominantCategories.put(r.itemId(), dominant);

            if (r.uncertain()) {
                uncertain++;
            } else {
                autoClassified++;
            }

            hits.add(new ScanHit(r.itemId(), dominant, r));
        }

        ScanCache.ScanSummary summary = new ScanCache.ScanSummary(
                cache.getModListHash(),
                System.currentTimeMillis(),
                sourceItems.size(),
                autoClassified,
                uncertain,
                alreadyTagged,
                dominantCategories
        );

        ScanCache.ScanDiff diff = summary.diffFrom(cache.getLastSummary());
        cache.setLastSummary(summary);

        progress.accept("Scan complete: " + autoClassified + " classified, " + uncertain + " uncertain");

        if (options.writeReports()) {
            progress.accept("Writing reports...");
            for (ScanReportSink sink : ScanReportSinkRegistry.getSinks()) {
                try {
                    sink.writeReports(allResults, summary, diff);
                } catch (IOException e) {
                    MarieCore.LOGGER.error("[ItemScanner] Failed to write reports", e);
                }
            }
        }

        if (options.writeRecommendations()) {
            progress.accept("Writing tag recommendations...");
            for (TagRecommendationSink sink : TagRecommendationSinkRegistry.getSinks()) {
                try {
                    sink.writeRecommendations(allResults, options.confidenceSpreadThreshold());
                } catch (IOException e) {
                    MarieCore.LOGGER.error("[ItemScanner] Failed to write recommendations", e);
                }
            }
            MultiValueAnalysisPipeline.run(allResults, 0.15f, 0.35f, 0.10f);
        }

        progress.accept("Done.");

        return new ScanResult(hits, allResults, summary, diff, traceMap);
    }

    /**
     * Get the scan cache. May be null if not initialized.
     */
    @Nullable
    public static ScanCache getCache() {
        ensureInitialized();
        return cache;
    }

    /**
     * Invalidate the cache, forcing a full rescan next time.
     */
    public static void invalidateCache() {
        if (cache != null) {
            cache.invalidate();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Initialization
    // ─────────────────────────────────────────────────────────────────────────────

    private static synchronized void ensureInitialized() {
        if (!initialized) {
            cache = new ScanCache();
            initialized = true;
        }
    }

    private static boolean passesSourceFilter(ItemStack stack) {
        if (MarieContext.isRegistered()) {
            return MarieContext.isSourceItemAllowed(stack);
        }
        return true;
    }
}
