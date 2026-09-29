package dev.marie.framework.scan;

import dev.marie.framework.api.ApiStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable result of a single runtime source resolution attempt.
 */
@ApiStatus.Internal
public record ResolutionResult(
        Map<String, Float> values,
        Map<String, Float> rawScores,
        List<String> tokens,
        Map<String, Float> tokenWeights,
        Map<String, String> rejectedSignals,
        boolean cacheHit,
        float confidence,
        RuntimeCascadeStage stage,
        String debugReason
) {

    public static final String REJECT_SPREAD_BELOW_THRESHOLD = "spread below threshold";
    public static final String REJECT_LOW_CONFIDENCE = "low confidence";
    public static final String REJECT_NO_MATCHING_KEYWORDS = "no matching keywords";
    public static final String REJECT_BELOW_PEER_MINIMUM = "below peer minimum";

    public ResolutionResult {
        values = Map.copyOf(values);
        rawScores = Map.copyOf(rawScores);
        tokens = List.copyOf(tokens);
        tokenWeights = Map.copyOf(tokenWeights);
        rejectedSignals = rejectedSignals.isEmpty() ? Map.of() : Map.copyOf(rejectedSignals);
    }

    public ResolutionResult withCacheHit(boolean hit) {
        return new ResolutionResult(
                values, rawScores, tokens, tokenWeights, rejectedSignals,
                hit, confidence, stage, debugReason
        );
    }

    public Map<String, Float> toValueMap() {
        return new LinkedHashMap<>(values);
    }
}
