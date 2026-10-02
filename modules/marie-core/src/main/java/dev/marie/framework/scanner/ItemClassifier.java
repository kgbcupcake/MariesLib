package dev.marie.framework.scanner;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.classification.ClassificationTraceStep;
import dev.marie.framework.classification.TraceStepId;
import dev.marie.framework.classification.TraceStepStatus;
import dev.marie.framework.core.MarieContext;
import dev.marie.framework.scan.ResolutionResult;
import dev.marie.framework.scan.ResolutionStageHandler;
import dev.marie.framework.scan.StageContext;
import dev.marie.framework.scan.StageMath;
import dev.marie.framework.scanner.ScannerSpecRegistry.Multipliers;
import dev.marie.framework.scanner.ScannerSpecRegistry.ScannerSpec;
import dev.marie.framework.scanner.stages.CommunityTagResolutionStage;
import dev.marie.framework.scanner.stages.KeywordResolutionStage;
import dev.marie.framework.scanner.stages.RecipeInheritanceStage;
import dev.marie.framework.scanner.stages.SuffixResolutionStage;
import dev.marie.framework.util.MarieRegistryUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

@ApiStatus.Internal
public final class ItemClassifier {
   private static final ResolutionStageHandler COMMUNITY_TAG_STAGE = new CommunityTagResolutionStage();
   private static final ResolutionStageHandler SUFFIX_STAGE = new SuffixResolutionStage();
   private static final ResolutionStageHandler KEYWORD_STAGE = new KeywordResolutionStage();

   /** Primary (non-recipe-inheritance) stages, used both for the direct scan path and as the
    * {@link dev.marie.framework.runtime.ComponentClassifier} fallback stage list for recipe ingredients. */
   private static final List<ResolutionStageHandler> PRIMARY_STAGES = List.of(COMMUNITY_TAG_STAGE, SUFFIX_STAGE, KEYWORD_STAGE);

   private final RecipeInheritanceResolver recipeResolver;
   private final boolean enableRecipeInheritance;
   private final float confidenceSpreadThreshold;
   private final List<String> valueKeys;

   public ItemClassifier(
      List<String> valueKeys, boolean enableRecipeInheritance, float confidenceSpreadThreshold, @Nullable RecipeInheritanceResolver recipeResolver
   ) {
      this.valueKeys = List.copyOf(valueKeys);
      this.enableRecipeInheritance = enableRecipeInheritance;
      this.confidenceSpreadThreshold = confidenceSpreadThreshold;
      this.recipeResolver = recipeResolver;
   }

   public ClassificationResult classify(
      Item item, Function<ResourceLocation, ClassificationResult> classifiedLookup, Map<String, Map<String, Float>> namespaceAverages
   ) {
      return this.classify(item, classifiedLookup, namespaceAverages, Optional.empty());
   }

   public ClassificationResult classify(
      Item item,
      Function<ResourceLocation, ClassificationResult> classifiedLookup,
      Map<String, Map<String, Float>> namespaceAverages,
      Optional<List<ClassificationTraceStep>> traceOut
   ) {
      ResourceLocation itemId = MarieRegistryUtils.itemKey(item);
      if (itemId == null) {
         return ClassificationResult.empty(ResourceLocation.withDefaultNamespace("unknown"), this.valueKeys.get(0));
      } else {
         ScannerSpec spec = ScannerSpecRegistry.get();
         Multipliers mult = spec.multipliers();
         ItemStack stack = new ItemStack(item);
         Holder<Item> holder = stack.getItemHolder();
         String namespace = itemId.getNamespace();
         String path = itemId.getPath();
         boolean hasSourceProperties = !MarieContext.isRegistered()
                 || MarieContext.isSourceItemAllowed(stack);
         if (traceOut.isPresent()) {
            Map<String, Object> discoveryDetail = new LinkedHashMap<>();
            discoveryDetail.put("itemId", itemId.toString());
            discoveryDetail.put("passesSourceFilter", hasSourceProperties);
            discoveryDetail.put("hasTag", false);
            if (!hasSourceProperties) {
               discoveryDetail.put("errorCode", "NOT_SOURCE_CAPABLE");
            }

            traceOut.get()
               .add(
                  new ClassificationTraceStep(
                     TraceStepId.ITEM_DISCOVERY,
                     hasSourceProperties ? TraceStepStatus.SUCCESS : TraceStepStatus.FAILURE,
                     hasSourceProperties ? "Source-capable item discovered" : "Item failed source filter",
                     discoveryDetail
                  )
               );
         }

         Map<String, Float> scores = new HashMap<>();

         for (String key : this.valueKeys) {
            scores.put(key, 0.0F);
         }

         Set<String> validKeys = Set.copyOf(this.valueKeys);
         StageContext ctx = new StageContext(holder, itemId, null, Map.of(), validKeys);

         List<ClassificationSignal> signals = new ArrayList<>();
         Map<String, Float> communityTagContribs = this.stageValues(COMMUNITY_TAG_STAGE, itemId, ctx);
         this.applySignal(scores, communityTagContribs, mult.communityTag());
         if (!communityTagContribs.isEmpty()) {
            signals.add(new ClassificationSignal("COMMUNITY_TAG", "c:" + CommunityTagResolutionStage.tagDirectory() + "*", this.scaleContributions(communityTagContribs, mult.communityTag())));
         }

         this.emitSignalStep(
            traceOut,
            TraceStepId.COMMUNITY_TAG_SIGNAL,
            "community_tag",
            communityTagContribs,
            this.scaleContributions(communityTagContribs, mult.communityTag())
         );
         Map<String, Float> namespaceContribs = ItemClassificationSignals.analyzeNamespace(namespace, spec.namespaceWeights());
         this.applySignal(scores, namespaceContribs, mult.namespace());
         if (!namespaceContribs.isEmpty()) {
            signals.add(new ClassificationSignal("NAMESPACE", namespace, this.scaleContributions(namespaceContribs, mult.namespace())));
         }

         Map<String, Float> suffixContribs = this.stageValues(SUFFIX_STAGE, itemId, ctx);
         this.applySignal(scores, suffixContribs, mult.suffix());
         if (!suffixContribs.isEmpty()) {
            signals.add(new ClassificationSignal("SUFFIX", this.extractTrailingToken(path), this.scaleContributions(suffixContribs, mult.suffix())));
         }

         Map<String, Float> keywordContribs = this.stageValues(KEYWORD_STAGE, itemId, ctx);
         this.applySignal(scores, keywordContribs, mult.keyword());
         if (!keywordContribs.isEmpty()) {
            signals.add(new ClassificationSignal("KEYWORD", path, this.scaleContributions(keywordContribs, mult.keyword())));
         }

         this.emitSignalStep(
            traceOut, TraceStepId.KEYWORD_SUFFIX_SCORING, "keyword_suffix", keywordContribs, this.scaleContributions(keywordContribs, mult.keyword())
         );
         Map<String, Float> negativeContribs = ItemClassificationSignals.analyzeNegativeKeywords(path, spec.negativeKeywords());
         this.applySignal(scores, negativeContribs, 1.0F);
         if (!negativeContribs.isEmpty()) {
            signals.add(new ClassificationSignal("NEGATIVE_KEYWORD", path, negativeContribs));
         }

         Map<String, Float> archetypeContribs = ItemClassificationSignals.analyzeArchetypes(path, spec.archetypes());
         this.applySignal(scores, archetypeContribs, mult.archetype());
         if (!archetypeContribs.isEmpty()) {
            signals.add(new ClassificationSignal("ARCHETYPE", path, this.scaleContributions(archetypeContribs, mult.archetype())));
         }

         Map<String, Float> sourcePropContribs = ItemClassificationSignals.analyzeSourcePropertySignals(stack);
         this.applySignal(scores, sourcePropContribs, 1.0F);
         if (!sourcePropContribs.isEmpty()) {
            signals.add(
               new ClassificationSignal(
                  ClassificationSignal.TYPE_SOURCE_PROPERTIES,
                  "source_property_signals",
                  sourcePropContribs
               )
            );
         }

         RecipeInheritanceStage.apply(
            item,
            itemId,
            scores,
            signals,
            false,
            this.enableRecipeInheritance ? this.recipeResolver : null,
            classifiedLookup,
            mult.recipeInheritance(),
            i -> this.communityTagScores(i, validKeys),
            validKeys,
            PRIMARY_STAGES
         );
         Map<String, Float> peerAvg = namespaceAverages.get(namespace);
         if (peerAvg != null && !peerAvg.isEmpty()) {
            Map<String, Float> peerContribs = ItemClassificationSignals.analyzeNamespacePeers(peerAvg, mult.namespacePeerAverageWeight());
            this.applySignal(scores, peerContribs, mult.namespacePeer());
            if (!peerContribs.isEmpty()) {
               signals.add(new ClassificationSignal("NAMESPACE_PEER", namespace + "_peers", this.scaleContributions(peerContribs, mult.namespacePeer())));
            }

            this.emitSignalStep(
               traceOut, TraceStepId.NAMESPACE_PEER, "namespace_peer", peerContribs, this.scaleContributions(peerContribs, mult.namespacePeer())
            );
         }

         if (traceOut.isPresent()) {
            Map<String, Object> aggregationDetail = new LinkedHashMap<>();
            aggregationDetail.put("mergedScores", new LinkedHashMap<>(scores));
            aggregationDetail.put("signalCount", signals.size());
            traceOut.get()
               .add(
                  new ClassificationTraceStep(
                     TraceStepId.SIGNAL_AGGREGATION, TraceStepStatus.SUCCESS, "Aggregated " + signals.size() + " signal(s)", aggregationDetail
                  )
               );
         }

         return this.buildResult(itemId, scores, signals, traceOut);
      }
   }

   private void emitSignalStep(
      Optional<List<ClassificationTraceStep>> traceOut,
      TraceStepId stepId,
      String signalType,
      Map<String, Float> rawContribs,
      Map<String, Float> scaledContribs
   ) {
      if (!traceOut.isEmpty()) {
         Map<String, Object> detail = new LinkedHashMap<>();
         detail.put("signalType", signalType);
         detail.put("contributions", new LinkedHashMap<>(scaledContribs));
         boolean hasSignal = !rawContribs.isEmpty();
         traceOut.get()
            .add(
               new ClassificationTraceStep(
                  stepId,
                  hasSignal ? TraceStepStatus.SUCCESS : TraceStepStatus.SKIPPED,
                  hasSignal ? signalType + " signal contributed scores" : "No " + signalType + " signal match",
                  detail
               )
            );
      }
   }

   /** Invokes {@code stage} against {@code ctx} and unwraps a null-safe contribution map. */
   private Map<String, Float> stageValues(ResolutionStageHandler stage, ResourceLocation itemId, StageContext ctx) {
      ResolutionResult result = stage.resolve(itemId, ctx);
      return result != null ? result.values() : Map.of();
   }

   /** Direct community-tag contribution scores for an arbitrary item, used as the
    * recipe-inheritance {@code valueTagScoresProvider}. */
   private Map<String, Float> communityTagScores(Item item, Set<String> validKeys) {
      ResourceLocation id = MarieRegistryUtils.itemKey(item);
      ResourceLocation safeId = id != null ? id : ResourceLocation.withDefaultNamespace("unknown");
      StageContext ingredientCtx = new StageContext(BuiltInRegistries.ITEM.wrapAsHolder(item), safeId, null, Map.of(), validKeys);
      return this.stageValues(COMMUNITY_TAG_STAGE, safeId, ingredientCtx);
   }

   private void applySignal(Map<String, Float> scores, Map<String, Float> contributions, float multiplier) {
      for (Entry<String, Float> e : contributions.entrySet()) {
         String key = e.getKey();
         if (scores.containsKey(key)) {
            scores.put(key, scores.get(key) + e.getValue() * multiplier);
         }
      }
   }

   private Map<String, Float> scaleContributions(Map<String, Float> contributions, float multiplier) {
      Map<String, Float> scaled = new HashMap<>();

      for (Entry<String, Float> e : contributions.entrySet()) {
         scaled.put(e.getKey(), e.getValue() * multiplier);
      }

      return scaled;
   }

   private String extractTrailingToken(String path) {
      List<String> raw = TokenStemmer.rawSegmentsForPath(path);
      if (!raw.isEmpty()) {
         return raw.get(raw.size() - 1);
      } else {
         String[] tokens = path.split("_");
         return tokens.length > 0 ? tokens[tokens.length - 1] : path;
      }
   }

   private ClassificationResult buildResult(ResourceLocation itemId, Map<String, Float> scores, List<ClassificationSignal> signals) {
      return this.buildResult(itemId, scores, signals, Optional.empty());
   }

   private ClassificationResult buildResult(
      ResourceLocation itemId, Map<String, Float> scores, List<ClassificationSignal> signals, Optional<List<ClassificationTraceStep>> traceOut
   ) {
      List<Entry<String, Float>> sorted = scores.entrySet().stream().sorted((a, b) -> Float.compare(b.getValue(), a.getValue())).toList();
      if (sorted.isEmpty()) {
         return ClassificationResult.empty(itemId, this.valueKeys.get(0));
      } else {
         String dominant = sorted.get(0).getKey();
         String secondary = sorted.size() > 1 ? sorted.get(1).getKey() : null;
         // Confidence is the top-two spread relative to the top weight (StageMath.confidenceRatio),
         // the same [0,1] scale RuntimeResolutionMerge now reports — one comparable measure across
         // the runtime and scanner paths. Computed over the final combined score vector, so any
         // contested recipe mass merged in by RecipeInheritanceStage moves it (previously the
         // inline topScore - secondScore ran before, and independent of, that recipe merge).
         float spread = StageMath.confidenceRatio(scores);
         boolean uncertain = spread < this.confidenceSpreadThreshold;
         if (traceOut.isPresent()) {
            Map<String, Object> winnerDetail = new LinkedHashMap<>();
            winnerDetail.put("dominant", dominant);
            if (secondary != null) {
               winnerDetail.put("secondary", secondary);
            }

            winnerDetail.put("spread", (double)spread);
            traceOut.get()
               .add(
                  new ClassificationTraceStep(
                     TraceStepId.WINNER_SELECTION,
                     TraceStepStatus.SUCCESS,
                     "Selected " + dominant + " as dominant (spread=" + String.format("%.2f", spread) + ")",
                     winnerDetail
                  )
               );
            Map<String, Object> confidenceDetail = new LinkedHashMap<>();
            confidenceDetail.put("spread", (double)spread);
            confidenceDetail.put("threshold", (double)this.confidenceSpreadThreshold);
            confidenceDetail.put("uncertain", uncertain);
            // spread is already StageMath.confidenceRatio: the top-two gap over the top weight,
            // clamped to [0, 1], so it doubles as the confidence score directly.
            confidenceDetail.put("confidenceScore", (double)spread);
            if (uncertain) {
               confidenceDetail.put("warningCode", "UNCERTAIN_SPREAD");
            }

            traceOut.get()
               .add(
                  new ClassificationTraceStep(
                     TraceStepId.CONFIDENCE,
                     uncertain ? TraceStepStatus.WARNING : TraceStepStatus.SUCCESS,
                     uncertain ? "Low confidence (spread below threshold)" : "Confident classification",
                     confidenceDetail
                  )
               );
         }

         return new ClassificationResult(itemId, scores, dominant, secondary, spread, signals, uncertain, false);
      }
   }

   public static Map<String, Map<String, Float>> computeNamespaceAverages(Map<ResourceLocation, ClassificationResult> results) {
      Map<String, List<ClassificationResult>> byNamespace = new HashMap<>();

      for (Entry<ResourceLocation, ClassificationResult> e : results.entrySet()) {
         String ns = e.getKey().getNamespace();
         byNamespace.computeIfAbsent(ns, k -> new ArrayList<>()).add(e.getValue());
      }

      Map<String, Map<String, Float>> averages = new HashMap<>();

      for (Entry<String, List<ClassificationResult>> e : byNamespace.entrySet()) {
         List<ClassificationResult> list = e.getValue();
         if (!list.isEmpty()) {
            Map<String, Float> summed = new HashMap<>();

            for (ClassificationResult r : list) {
               for (Entry<String, Float> score : r.scores().entrySet()) {
                  summed.merge(score.getKey(), score.getValue(), Float::sum);
               }
            }

            Map<String, Float> avg = new HashMap<>();

            for (Entry<String, Float> s : summed.entrySet()) {
               avg.put(s.getKey(), s.getValue() / (float)list.size());
            }

            averages.put(e.getKey(), avg);
         }
      }

      return averages;
   }
}
