package dev.marie.framework.diagnostics.report;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.diagnostics.api.DiagnosticProfile;
import dev.marie.framework.diagnostics.api.DiagnosticProvider;
import dev.marie.framework.diagnostics.api.ProviderContext;
import dev.marie.framework.diagnostics.capture.ContextCapture;
import dev.marie.framework.diagnostics.capture.ProviderRunner;
import dev.marie.framework.diagnostics.capture.ThrowableCapture;
import dev.marie.framework.diagnostics.registry.ModIds;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class IncidentReportBuilder {

    public static final int SCHEMA_VERSION = 1;

    private final List<DiagnosticProfile> profiles;
    private final List<ContextCapture> captures;
    private final ProviderRunner providers;
    private final Function<String, String> modVersion;

    public IncidentReportBuilder(List<DiagnosticProfile> profiles, List<ContextCapture> captures,
                                 ProviderRunner providers, Function<String, String> modVersion) {
        this.profiles = profiles;
        this.captures = captures;
        this.providers = providers;
        this.modVersion = modVersion;
    }

    public JsonObject build(IncidentRequest req) {
        JsonObject report = new JsonObject();
        report.addProperty("schemaVersion", SCHEMA_VERSION);
        JsonArray captured = new JsonArray();
        JsonArray failed = new JsonArray();
        JsonArray unavailable = new JsonArray();
        JsonArray limitations = new JsonArray();
        req.limitations().forEach(limitations::add);

        section(report, "incident", () -> incident(req), captured, failed);
        section(report, "correlation", () -> correlation(req), captured, failed);
        for (ContextCapture capture : captures) {
            section(report, capture.section(), () -> {
                try {
                    return capture.capture();
                } catch (Exception e) {
                    throw new SectionFailure(e);
                }
            }, captured, failed);
        }
        section(report, "failure", () -> failure(req), captured, failed);
        section(report, "runtime", () -> runtime(req, failed, unavailable), captured, failed);
        report.add("analysis", analysis());
        unavailable.add("analysis (classification, root cause and suggested fixes arrive in later phases)");

        JsonObject status = new JsonObject();
        status.add("captured", captured);
        status.add("failed", failed);
        status.add("unavailable", unavailable);
        status.add("limitations", limitations);
        report.add("diagnosticStatus", status);
        return report;
    }

    private static void section(JsonObject report, String name, Supplier<JsonElement> body,
                                JsonArray captured, JsonArray failed) {
        try {
            report.add(name, body.get());
            captured.add(name);
        } catch (Throwable t) {
            Throwable cause = t instanceof SectionFailure && t.getCause() != null ? t.getCause() : t;
            JsonObject f = new JsonObject();
            f.addProperty("section", name);
            f.addProperty("error", cause.getClass().getName() + ": " + cause.getMessage());
            failed.add(f);
            report.add(name, JsonNull.INSTANCE);
        }
    }

    private static JsonObject incident(IncidentRequest req) {
        JsonObject o = new JsonObject();
        o.addProperty("id", req.id());
        o.addProperty("timestamp", req.timestamp().toString());
        o.addProperty("kind", req.kind().name());
        o.addProperty("reportingMod", req.reportingMod());
        o.addProperty("storageMod", req.storageMod());
        o.addProperty("subsystem", req.subsystem());
        o.addProperty("component", req.component());
        o.addProperty("category", req.category().name());
        o.addProperty("severity", req.severity().name());
        o.addProperty("occurrence", req.occurrence());
        return o;
    }

    private static JsonObject correlation(IncidentRequest req) {
        JsonObject o = new JsonObject();
        o.addProperty("minecraftCrashReport", req.crashReport());
        o.addProperty("reportType", req.reportType());
        return o;
    }

    private static JsonObject failure(IncidentRequest req) {
        JsonObject o = req.failure() != null ? ThrowableCapture.describe(req.failure()) : new JsonObject();
        if (req.failure() == null) {
            o.add("exceptionType", JsonNull.INSTANCE);
            o.addProperty("message", req.description());
            if (req.reportSite() != null) {
                o.add("reportSite", ThrowableCapture.frames(req.reportSite(), ThrowableCapture.MAX_CAUSE_FRAMES));
            }
        }
        o.add("relevantFrames", req.attribution().relevantFrames());
        JsonArray implicated = new JsonArray();
        req.attribution().implicatedMods().forEach(implicated::add);
        o.add("implicatedMods", implicated);
        o.addProperty("implicatedModsMeaning",
                "mods whose code appeared in the stack trace (evidence, not a confirmed cause)");
        JsonObject context = new JsonObject();
        for (Map.Entry<String, String> e : req.context().entrySet()) {
            context.addProperty(e.getKey(), e.getValue());
        }
        o.add("context", context);
        o.addProperty("contextTruncated", req.contextTruncated());
        return o;
    }

    private JsonObject runtime(IncidentRequest req, JsonArray failed, JsonArray unavailable) {
        JsonObject o = new JsonObject();
        JsonArray mods = new JsonArray();
        for (DiagnosticProfile p : profiles) {
            JsonObject m = new JsonObject();
            m.addProperty("id", p.modId());
            m.addProperty("version", safeVersion(p.modId()));
            JsonArray pkgs = new JsonArray();
            p.packages().forEach(pkgs::add);
            m.add("packages", pkgs);
            JsonArray ids = new JsonArray();
            p.providers().keySet().forEach(ids::add);
            m.add("providers", ids);
            mods.add(m);
        }
        o.add("integratedMods", mods);

        if (!req.runProviders()) {
            unavailable.add("providers (the reporting mod turned them off for this failure)");
            o.add("providers", new JsonObject());
            return o;
        }
        ProviderContext ctx = new ProviderContext(req.id(), req.kind(), req.reportingMod(), req.component(), req.category());
        ProviderRunner.Outcome outcome = providers.run(targets(req), ctx);
        o.add("providers", outcome.sections());
        for (String failure : outcome.failures()) {
            JsonObject f = new JsonObject();
            f.addProperty("section", "runtime.providers");
            f.addProperty("error", failure);
            failed.add(f);
        }
        return o;
    }

    private List<ProviderRunner.Target> targets(IncidentRequest req) {
        Set<String> order = new LinkedHashSet<>();
        order.add(req.reportingMod());
        order.addAll(req.attribution().implicatedMods());
        order.add(ModIds.MARIESLIB);
        List<ProviderRunner.Target> out = new ArrayList<>();
        for (String modId : order) {
            for (DiagnosticProfile p : profiles) {
                if (p.modId().equals(modId)) {
                    for (Map.Entry<String, DiagnosticProvider> e : p.providers().entrySet()) {
                        out.add(new ProviderRunner.Target(modId, e.getKey(), e.getValue()));
                    }
                }
            }
        }
        return out;
    }

    private String safeVersion(String modId) {
        try {
            String v = modVersion.apply(modId);
            return v == null ? "unknown" : v;
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private static JsonObject analysis() {
        JsonObject o = new JsonObject();
        o.add("classification", JsonNull.INSTANCE);
        o.add("executionTrace", JsonNull.INSTANCE);
        o.add("rootCause", JsonNull.INSTANCE);
        o.add("contributingFactors", new JsonArray());
        o.add("suggestedFixes", new JsonArray());
        o.add("confidence", JsonNull.INSTANCE);
        return o;
    }

    private static final class SectionFailure extends RuntimeException {
        SectionFailure(Exception cause) {
            super(cause);
        }
    }
}
