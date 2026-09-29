package dev.marie.framework.classification;

import dev.marie.framework.core.MarieContext;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Formats a {@link ClassificationTrace} into the full SOURCE INSPECTOR output.
 * Pure static, no state.
 */
public final class ClassificationTraceFormatter {

    private static final String SEP_FULL = "==================================================";
    static final String SEP_HALF = "--------------------------------------------------";

    private ClassificationTraceFormatter() {}

    public static String format(ClassificationTrace trace, ItemStack stack) {
        StringBuilder sb = new StringBuilder();

        appendFull(sb, SEP_FULL);
        appendLine(sb, "SOURCE INSPECTOR");
        appendFull(sb, SEP_FULL);
        appendLine(sb, "");

        appendItemSection(sb, trace, stack);
        appendClassificationSummary(sb, trace);
        appendClassificationPath(sb, trace);
        appendInheritanceBreakdown(sb, trace);
        appendAggregation(sb, trace);
        appendWhyWon(sb, trace);
        ClassificationDiagnosticsFormatter.appendDiagnostics(sb, trace);
        ClassificationDiagnosticsFormatter.appendDeveloperMetadata(sb, trace);

        return sb.toString();
    }

    // ─── Sections ────────────────────────────────────────────────────────────────

    private static void appendItemSection(StringBuilder sb, ClassificationTrace trace, ItemStack stack) {
        appendLine(sb, "Item");
        appendLine(sb, SEP_HALF);
        String itemId = trace.itemId();
        String namespace = itemId.contains(":") ? itemId.substring(0, itemId.indexOf(':')) : itemId;
        boolean sourceCapable = MarieContext.isRegistered()
                && MarieContext.isSourceItemAllowed(stack);
        appendKv(sb, "ID", itemId);
        appendKv(sb, "Namespace", namespace);
        appendKv(sb, "Source-capable", sourceCapable ? "YES" : "NO");
        appendLine(sb, "");
    }

    private static void appendClassificationSummary(StringBuilder sb, ClassificationTrace trace) {
        appendLine(sb, "Classification Summary");
        appendLine(sb, SEP_HALF);

        String dominant = trace.dominant();
        String status;
        if (trace.uncertain()) {
            status = "UNCERTAIN";
        } else if (dominant == null) {
            status = "UNCLASSIFIED";
        } else {
            status = "STABLE";
        }

        appendKv(sb, "Final Group", dominant != null ? dominant.toUpperCase(Locale.ROOT) : "NONE");

        // Confidence: derive from CONFIDENCE step detail
        String confidence = deriveConfidence(trace);
        appendKv(sb, "Confidence", confidence);

        appendKv(sb, "Pipeline", trace.pipeline().name());
        appendKv(sb, "Status", status);
        appendLine(sb, "");
    }

    private static void appendClassificationPath(StringBuilder sb, ClassificationTrace trace) {
        appendLine(sb, "Classification Path");
        appendLine(sb, SEP_HALF);
        appendLine(sb, "");

        List<ClassificationTraceStep> steps = trace.steps();
        for (int i = 0; i < steps.size(); i++) {
            ClassificationTraceStep step = steps.get(i);
            String icon = switch (step.status()) {
                case SUCCESS -> "✓";
                case FAILURE -> "✗";
                case WARNING -> "⚠";
                case SKIPPED -> "-";
            };
            appendLine(sb, "[" + (i + 1) + "] " + step.id().name() + " " + icon + " " + step.message());
        }
        appendLine(sb, "");
    }

    private static void appendInheritanceBreakdown(StringBuilder sb, ClassificationTrace trace) {
        List<ClassificationTraceStep> ingredientSteps = collectSteps(trace, TraceStepId.INGREDIENT_RESOLUTION);
        if (ingredientSteps.isEmpty()) return;

        appendLine(sb, "Inheritance Breakdown");
        appendLine(sb, SEP_HALF);

        for (ClassificationTraceStep step : ingredientSteps) {
            Map<String, Object> d = step.detail();
            String ingredientId = getString(d, "ingredientId", "unknown");
            String source = getString(d, "source", "NONE");
            appendLine(sb, ingredientId);

            @SuppressWarnings("unchecked")
            Map<String, Float> values = d.get("values") instanceof Map<?, ?> m
                    ? (Map<String, Float>) m : null;

            if (step.status() == TraceStepStatus.SUCCESS && values != null && !values.isEmpty()) {
                appendLine(sb, "  Source: " + source);
                String result = String.join(", ", values.keySet()).toUpperCase(Locale.ROOT);
                appendLine(sb, "  Result: " + result);
            } else if (step.status() == TraceStepStatus.SKIPPED) {
                String skipReason = getString(d, "skipReason", "skipped");
                appendLine(sb, "  Source: " + source);
                appendLine(sb, "  Result: SKIPPED");
                appendLine(sb, "  Reason: " + skipReason);
            } else {
                appendLine(sb, "  Source: NONE");
                appendLine(sb, "  Result: UNCLASSIFIED");
                String failure = "No direct tags · No runtime match · No recipe ingredients";
                appendLine(sb, "  Failure: " + failure);
            }
            appendLine(sb, "");
        }
    }

    private static void appendAggregation(StringBuilder sb, ClassificationTrace trace) {
        List<ClassificationTraceStep> aggSteps = collectSteps(trace, TraceStepId.SIGNAL_AGGREGATION);
        if (aggSteps.isEmpty()) return;

        appendLine(sb, "Aggregation");
        appendLine(sb, SEP_HALF);

        // Collect ingredient contributions for "Raw Contributions"
        List<ClassificationTraceStep> ingredientSteps = collectSteps(trace, TraceStepId.INGREDIENT_RESOLUTION);
        if (!ingredientSteps.isEmpty()) {
            appendLine(sb, "Raw Contributions");
            for (ClassificationTraceStep step : ingredientSteps) {
                Map<String, Object> d = step.detail();
                String ingredientId = getString(d, "ingredientId", "unknown");
                String shortId = ingredientId.contains(":") ? ingredientId.substring(ingredientId.indexOf(':') + 1) : ingredientId;
                @SuppressWarnings("unchecked")
                Map<String, Float> values = d.get("values") instanceof Map<?, ?> m
                        ? (Map<String, Float>) m : null;
                if (values != null) {
                    for (Map.Entry<String, Float> e : values.entrySet()) {
                        appendLine(sb, "  " + e.getKey() + ": " + shortId + " = " + String.format(Locale.ROOT, "%.1f", e.getValue()));
                    }
                }
            }
            appendLine(sb, "");
        }

        ClassificationTraceStep aggStep = aggSteps.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Float> merged = aggStep.detail().get("mergedScores") instanceof Map<?, ?> m
                ? (Map<String, Float>) m : null;
        if (merged != null && !merged.isEmpty()) {
            float total = merged.values().stream().reduce(0f, Float::sum);
            appendLine(sb, "Weighted Totals");
            for (Map.Entry<String, Float> e : merged.entrySet()) {
                float pct = total > 0 ? e.getValue() / total * 100f : 0f;
                appendLine(sb, String.format(Locale.ROOT, "  %-12s %.1f%%", e.getKey(), pct));
            }
        }
        appendLine(sb, "");
    }

    private static void appendWhyWon(StringBuilder sb, ClassificationTrace trace) {
        String dominant = trace.dominant();
        if (dominant == null) return;

        List<ClassificationTraceStep> aggSteps = collectSteps(trace, TraceStepId.SIGNAL_AGGREGATION);
        if (aggSteps.isEmpty()) return;

        appendLine(sb, "Why " + dominant.toUpperCase(Locale.ROOT) + " Won");
        appendLine(sb, SEP_HALF);

        @SuppressWarnings("unchecked")
        Map<String, Float> merged = aggSteps.get(0).detail().get("mergedScores") instanceof Map<?, ?> m
                ? (Map<String, Float>) m : null;

        if (merged != null) {
            float topScore = merged.getOrDefault(dominant, 0f);
            float secondScore = 0f;
            for (Map.Entry<String, Float> e : merged.entrySet()) {
                if (!e.getKey().equals(dominant) && e.getValue() > secondScore) {
                    secondScore = e.getValue();
                }
            }
            float diff = topScore - secondScore;
            appendLine(sb, "  " + dominant + " score: " + String.format(Locale.ROOT, "%.1f", topScore));
            appendLine(sb, "  secondary score: " + String.format(Locale.ROOT, "%.1f", secondScore));
            appendLine(sb, "  Difference: " + String.format(Locale.ROOT, "%+.1f", diff));
            appendLine(sb, "  Confidence: " + deriveConfidence(trace));
            appendLine(sb, "  Reason: " + (trace.summaryReason().isEmpty() ? "Highest weighted contribution." : trace.summaryReason()));
        }
        appendLine(sb, "");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private static String deriveConfidence(ClassificationTrace trace) {
        for (ClassificationTraceStep step : trace.steps()) {
            if (step.id() == TraceStepId.CONFIDENCE) {
                Object cs = step.detail().get("confidenceScore");
                if (cs instanceof Number n) {
                    return String.format(Locale.ROOT, "%.0f%%", n.doubleValue() * 100.0);
                }
                // Fallback: compute from spread/threshold
                Object spread = step.detail().get("spread");
                Object threshold = step.detail().get("threshold");
                if (spread instanceof Number sp && threshold instanceof Number th) {
                    double ratio = th.doubleValue() > 0 ? sp.doubleValue() / (th.doubleValue() * 2) : 0;
                    ratio = Math.max(0.0, Math.min(1.0, ratio));
                    return String.format(Locale.ROOT, "%.0f%%", ratio * 100.0);
                }
            }
        }
        // Fallback from KEYWORD_SUFFIX_SCORING spread
        for (ClassificationTraceStep step : trace.steps()) {
            if (step.id() == TraceStepId.KEYWORD_SUFFIX_SCORING) {
                Object spread = step.detail().get("spread");
                Object threshold = step.detail().get("spreadThreshold");
                if (spread instanceof Number sp && threshold instanceof Number th) {
                    double ratio = th.doubleValue() > 0 ? sp.doubleValue() / (th.doubleValue() * 2) : 0;
                    ratio = Math.max(0.0, Math.min(1.0, ratio));
                    return String.format(Locale.ROOT, "%.0f%%", ratio * 100.0);
                }
            }
        }
        return "N/A";
    }

    static List<ClassificationTraceStep> collectSteps(ClassificationTrace trace, TraceStepId id) {
        List<ClassificationTraceStep> result = new ArrayList<>();
        for (ClassificationTraceStep step : trace.steps()) {
            if (step.id() == id) result.add(step);
        }
        return result;
    }

    static String getString(Map<String, Object> map, String key, String fallback) {
        Object v = map.get(key);
        return v != null ? v.toString() : fallback;
    }

    static void appendLine(StringBuilder sb, String line) {
        sb.append(line).append('\n');
    }

    private static void appendFull(StringBuilder sb, String line) {
        sb.append(line).append('\n');
    }

    static void appendKv(StringBuilder sb, String label, String value) {
        sb.append(label).append(": ").append(value).append('\n');
    }
}
