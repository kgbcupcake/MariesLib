package dev.marie.framework.scan;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.core.IMarieConfig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Shared numeric helpers used by multiple resolution stages.
 * Package-private — not part of the public API surface.
 */
@ApiStatus.Internal
public final class StageMath {

    private StageMath() {}

    public record NormalizationOutcome(
            Map<String, Float> normalized,
            Map<String, String> rejectedSignals
    ) {}

    public static float computeSpread(Map<String, Float> scores) {
        float first = Float.NEGATIVE_INFINITY;
        float second = Float.NEGATIVE_INFINITY;
        for (float v : scores.values()) {
            if (v > first) {
                second = first;
                first = v;
            } else if (v > second) {
                second = v;
            }
        }
        if (first == Float.NEGATIVE_INFINITY) return 0f;
        if (second == Float.NEGATIVE_INFINITY) return first;
        return first - second;
    }

    /**
     * Normalizes raw scores into the bar map shape expected downstream:
     * confident dominant (spread >= threshold) produces a single 1.0 entry,
     * otherwise normalizes all positive mass. Filters to valid value keys.
     */
    public static Map<String, Float> normalizeToBarMap(Map<String, Float> raw, Set<String> validKeys) {
        return normalizeWithRejections(raw, validKeys).normalized();
    }

    /**
     * Full normalization producing both the bar map and a rejection reason
     * for every valid value key that did not make it into the final map.
     */
    public static NormalizationOutcome normalizeWithRejections(Map<String, Float> raw, Set<String> validKeys) {
        Map<String, Float> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, Float> e : raw.entrySet()) {
            if (validKeys.contains(e.getKey()) && e.getValue() > 0f) {
                filtered.put(e.getKey(), e.getValue());
            }
        }
        if (filtered.isEmpty()) {
            Map<String, String> rejected = new LinkedHashMap<>();
            for (String key : validKeys) {
                rejected.put(key, ResolutionResult.REJECT_NO_MATCHING_KEYWORDS);
            }
            return new NormalizationOutcome(Map.of(), rejected);
        }

        float max = Float.NEGATIVE_INFINITY;
        String dominant = null;
        float second = Float.NEGATIVE_INFINITY;
        for (Map.Entry<String, Float> e : filtered.entrySet()) {
            if (e.getValue() > max) {
                second = max;
                max = e.getValue();
                dominant = e.getKey();
            } else if (e.getValue() > second) {
                second = e.getValue();
            }
        }

        float spread = (second == Float.NEGATIVE_INFINITY) ? max : max - second;
        float threshold = scannerConfidenceSpreadThreshold();

        if (spread >= threshold && dominant != null) {
            Map<String, String> rejected = new LinkedHashMap<>();
            for (String key : filtered.keySet()) {
                if (!key.equals(dominant)) {
                    rejected.put(key, ResolutionResult.REJECT_LOW_CONFIDENCE);
                }
            }
            for (String key : validKeys) {
                if (!filtered.containsKey(key)) {
                    rejected.put(key, ResolutionResult.REJECT_NO_MATCHING_KEYWORDS);
                }
            }
            return new NormalizationOutcome(Map.of(dominant, 1.0f), rejected);
        }

        float sum = 0f;
        for (float v : filtered.values()) sum += v;
        if (sum <= 1e-5f) {
            Map<String, String> rejected = new LinkedHashMap<>();
            for (String key : validKeys) {
                rejected.put(key, ResolutionResult.REJECT_NO_MATCHING_KEYWORDS);
            }
            return new NormalizationOutcome(Map.of(), rejected);
        }

        Map<String, Float> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Float> e : filtered.entrySet()) {
            normalized.put(e.getKey(), e.getValue() / sum);
        }
        Map<String, String> rejected = new LinkedHashMap<>();
        for (String key : validKeys) {
            if (!filtered.containsKey(key)) {
                rejected.put(key, ResolutionResult.REJECT_NO_MATCHING_KEYWORDS);
            }
        }
        return new NormalizationOutcome(normalized, rejected);
    }

    public static float scannerConfidenceSpreadThreshold() {
        return IMarieConfig.get().scannerConfidenceSpreadThreshold();
    }

    /**
     * Confidence of a score map: the spread between its top two values divided by the top
     * value, clamped to {@code [0, 1]}. A single positive category yields {@code 1.0}
     * (uncontested); a dead heat yields {@code 0.0}. Empty / all-non-positive maps yield
     * {@code 0.0}.
     *
     * <p>This is the same ratio {@link dev.marie.framework.scanner.ClassificationResult#confidenceScore()}
     * produces, lifted here so keyword-only results and merged keyword+recipe results
     * ({@link RuntimeResolutionMerge}) report confidence on one comparable scale rather than
     * one stage passing a collapsed {@code 1.0} through verbatim.</p>
     */
    public static float confidenceRatio(Map<String, Float> scores) {
        float max = 0f;
        for (float v : scores.values()) {
            if (v > max) {
                max = v;
            }
        }
        if (max <= 0f) {
            return 0f;
        }
        float ratio = computeSpread(scores) / max;
        return ratio < 0f ? 0f : Math.min(ratio, 1f);
    }
}
