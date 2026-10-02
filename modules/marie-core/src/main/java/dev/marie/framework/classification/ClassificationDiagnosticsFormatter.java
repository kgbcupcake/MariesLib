package dev.marie.framework.classification;

import dev.marie.framework.api.ApiStatus;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Renders the "Diagnostics" and "Developer Metadata" sections of the SOURCE INSPECTOR output for
 * {@link ClassificationTraceFormatter}. Extracted since the diagnostic rule table and developer
 * metadata are a self-contained concern, unlike the narrative sections that share state across
 * each other (aggregation feeding into why-won, etc.).
 * Pure static, no state.
 */
@ApiStatus.Internal
final class ClassificationDiagnosticsFormatter {

    private ClassificationDiagnosticsFormatter() {}

    static void appendDiagnostics(StringBuilder sb, ClassificationTrace trace) {
        List<DiagnosticEntry> diagnostics = collectDiagnostics(trace);

        long errorCount = diagnostics.stream().filter(d -> d.isError).count();
        long warnCount = diagnostics.stream().filter(d -> !d.isError).count();
        long infoCount = trace.steps().stream().filter(s -> s.status() == TraceStepStatus.SUCCESS).count();

        ClassificationTraceFormatter.appendLine(sb, "Diagnostics");
        ClassificationTraceFormatter.appendLine(sb, ClassificationTraceFormatter.SEP_HALF);
        ClassificationTraceFormatter.appendKv(sb, "Errors", String.valueOf(errorCount));
        ClassificationTraceFormatter.appendKv(sb, "Warnings", String.valueOf(warnCount));
        ClassificationTraceFormatter.appendKv(sb, "Infos", String.valueOf(infoCount));

        if (!diagnostics.isEmpty()) {
            ClassificationTraceFormatter.appendLine(sb, "");
            for (DiagnosticEntry entry : diagnostics) {
                ClassificationTraceFormatter.appendLine(sb, (entry.isError ? "ERROR " : "WARNING ") + entry.code);
                ClassificationTraceFormatter.appendLine(sb, "  " + entry.summary);
                ClassificationTraceFormatter.appendLine(sb, "  Cause: " + entry.cause);
                ClassificationTraceFormatter.appendLine(sb, "  Impact: " + entry.impact);
                if (entry.fix != null) {
                    ClassificationTraceFormatter.appendLine(sb, "  Suggested Fix: " + entry.fix);
                }
                ClassificationTraceFormatter.appendLine(sb, "");
            }
        }
    }

    static void appendDeveloperMetadata(StringBuilder sb, ClassificationTrace trace) {
        List<ClassificationTraceStep> cacheSteps =
                ClassificationTraceFormatter.collectSteps(trace, TraceStepId.RESOLVER_CACHE);
        String cacheStatus = "MISS";
        if (!cacheSteps.isEmpty()) {
            Object hit = cacheSteps.get(0).detail().get("hit");
            if (Boolean.TRUE.equals(hit)) {
                cacheStatus = "HIT";
            }
        }

        // Trace ID: first 8 chars of UUID derived from itemId + step count
        String traceSource = trace.itemId() + trace.steps().size();
        String traceId = UUID.nameUUIDFromBytes(traceSource.getBytes()).toString().substring(0, 8);

        ClassificationTraceFormatter.appendLine(sb, "Developer Metadata");
        ClassificationTraceFormatter.appendLine(sb, ClassificationTraceFormatter.SEP_HALF);
        ClassificationTraceFormatter.appendKv(sb, "Pipeline", trace.pipeline().name());
        ClassificationTraceFormatter.appendKv(sb, "Trace ID", traceId);
        ClassificationTraceFormatter.appendKv(sb, "Steps", String.valueOf(trace.steps().size()));
        ClassificationTraceFormatter.appendKv(sb, "Uncertain", String.valueOf(trace.uncertain()));
        ClassificationTraceFormatter.appendKv(sb, "Cache", cacheStatus);
    }

    private static List<DiagnosticEntry> collectDiagnostics(ClassificationTrace trace) {
        List<DiagnosticEntry> list = new ArrayList<>();

        for (ClassificationTraceStep step : trace.steps()) {
            Map<String, Object> d = step.detail();

            if (step.id() == TraceStepId.INGREDIENT_RESOLUTION
                    && (step.status() == TraceStepStatus.FAILURE || step.status() == TraceStepStatus.WARNING)) {
                String ingredientId = ClassificationTraceFormatter.getString(d, "ingredientId", "unknown");
                list.add(new DiagnosticEntry(
                        false,
                        "NRS-W001",
                        ingredientId + " is currently unclassified.",
                        "No value source found for ingredient.",
                        "Ingredient ignored during inheritance.",
                        "Add to a values/primary tag\n                 OR create a source_classifications datapack entry."
                ));
            }

            if (step.id() == TraceStepId.CONFIDENCE && step.status() == TraceStepStatus.WARNING) {
                list.add(new DiagnosticEntry(
                        false,
                        "NRS-W002",
                        "Classification confidence is below threshold.",
                        "Signal spread below spread threshold.",
                        "Classification marked as UNCERTAIN.",
                        null
                ));
            }

            if (step.id() == TraceStepId.HARD_FALLBACK && step.status() == TraceStepStatus.FAILURE) {
                list.add(new DiagnosticEntry(
                        true,
                        "NRS-001",
                        "Item could not be classified through any pipeline path.",
                        "No signal source produced a valid classification.",
                        "Item will appear as unclassified in tracking tracking.",
                        "Add to a values/* tag or create a datapack entry."
                ));
            }

            if (step.id() == TraceStepId.RECIPE_LOOKUP && step.status() == TraceStepStatus.FAILURE) {
                list.add(new DiagnosticEntry(
                        true,
                        "NRS-002",
                        "No recipe found for this item.",
                        "Item has no known crafting recipe on the server.",
                        "Recipe inheritance path unavailable.",
                        null
                ));
            }

            if (step.id() == TraceStepId.PRIMARY_RECIPE_MERGE && step.status() == TraceStepStatus.FAILURE) {
                list.add(new DiagnosticEntry(
                        true,
                        "NRS-003",
                        "No classified ingredients found in recipe.",
                        "All recipe ingredients are unclassified.",
                        "Recipe inheritance produced no value keys.",
                        "Classify at least one recipe ingredient via value tags."
                ));
            }
        }

        return list;
    }

    private record DiagnosticEntry(
            boolean isError,
            String code,
            String summary,
            String cause,
            String impact,
            @Nullable String fix
    ) {}
}
