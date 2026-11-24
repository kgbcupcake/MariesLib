package dev.marie.framework.diagnostics.service;

import com.google.gson.JsonObject;
import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.diagnostics.api.DiagnosticProfile;
import dev.marie.framework.diagnostics.api.FailureCategory;
import dev.marie.framework.diagnostics.api.FailureSubmission;
import dev.marie.framework.diagnostics.api.IncidentKind;
import dev.marie.framework.diagnostics.api.Severity;
import dev.marie.framework.diagnostics.capture.ContextCapture;
import dev.marie.framework.diagnostics.capture.ProviderRunner;
import dev.marie.framework.diagnostics.capture.StackAttribution;
import dev.marie.framework.diagnostics.registry.ModIds;
import dev.marie.framework.diagnostics.report.IncidentIds;
import dev.marie.framework.diagnostics.report.IncidentReportBuilder;
import dev.marie.framework.diagnostics.report.IncidentRequest;
import dev.marie.framework.diagnostics.report.ReportStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class DiagnosticsService {

    public static final Logger LOG = LoggerFactory.getLogger("MariesLib/Diagnostics");

    public static final int MAX_INCIDENTS_PER_SESSION = 50;
    public static final int MAX_DUPLICATES = 3;
    static final int WRITE_QUEUE = 64;

    private final ReportStore store;
    private final Supplier<List<DiagnosticProfile>> profiles;
    private final List<ContextCapture> captures;
    private final ProviderRunner providers;
    private final Function<String, String> modVersion;
    private final BooleanSupplier loadingComplete;
    private final SubmissionLimiter limiter;
    private final ThreadPoolExecutor writer;

    public DiagnosticsService(ReportStore store, Supplier<List<DiagnosticProfile>> profiles,
                              List<ContextCapture> captures, ProviderRunner providers,
                              Function<String, String> modVersion, BooleanSupplier loadingComplete,
                              SubmissionLimiter limiter) {
        this.store = store;
        this.profiles = profiles;
        this.captures = List.copyOf(captures);
        this.providers = providers;
        this.modVersion = modVersion;
        this.loadingComplete = loadingComplete;
        this.limiter = limiter;
        this.writer = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(WRITE_QUEUE), r -> {
            Thread t = new Thread(r, "marieslib-diagnostics");
            t.setDaemon(true);
            return t;
        });
    }

    public Optional<String> submit(String modId, @Nullable Throwable failure, @Nullable String description,
                                   FailureSubmission details) {
        if (DiagnosticsGuard.isActive()) {
            LOG.debug("Dropped a failure reported from inside diagnostics work by {}", modId);
            return Optional.empty();
        }
        try {
            List<String> limitations = new ArrayList<>(baseLimitations());
            String reporter = modId;
            if (!ModIds.isValid(modId)) {
                limitations.add("Submitted with an invalid mod id '" + clip(String.valueOf(modId))
                        + "'; stored under " + ModIds.MARIESLIB + ".");
                reporter = ModIds.MARIESLIB;
            }
            List<DiagnosticProfile> known = profiles.get();
            StackTraceElement[] site = failure == null ? reportSite() : null;
            StackAttribution.Result attribution = failure != null
                    ? StackAttribution.attribute(failure, known)
                    : StackAttribution.attributeSite(site, known);

            String key = reporter + "|" + details.component() + "|" + dedupeTail(failure, description);
            SubmissionLimiter.Admission admission = limiter.admit(key);
            if (!admission.allowed()) {
                logSuppressed(reporter, details.component(), admission);
                return Optional.empty();
            }

            String id = IncidentIds.newId();
            Instant now = Instant.now();
            IncidentRequest request = new IncidentRequest(id, now, IncidentKind.REPORTED_FAILURE, reporter, reporter,
                    details.component(), details.subsystem(), details.category(), details.severity(),
                    failure, failure == null ? clip(String.valueOf(description)) : null, site, attribution,
                    details.context(), details.contextTruncated(), details.runProviders(),
                    null, null, admission.occurrence(), List.copyOf(limitations));
            try {
                writer.execute(() -> DiagnosticsGuard.run(() -> write(request)));
            } catch (RejectedExecutionException e) {
                LOG.warn("Diagnostics write queue is full; dropped incident {} from {}", id, reporter);
                return Optional.empty();
            }
            return Optional.of(id);
        } catch (Throwable t) {
            LOG.warn("Could not record a failure reported by {}", modId, t);
            return Optional.empty();
        }
    }

    @Nullable
    public CrashTicket prepareCrash(@Nullable Throwable failure) {
        if (DiagnosticsGuard.isActive()) {
            return null;
        }
        List<DiagnosticProfile> known = profiles.get();
        StackAttribution.Result attribution = failure != null
                ? StackAttribution.attribute(failure, known)
                : new StackAttribution.Result(List.of(), new com.google.gson.JsonArray());
        String primary = attribution.primary();
        String storage = primary != null && ModIds.isValid(primary) ? primary : ModIds.MARIESLIB;
        String id = IncidentIds.newId();
        Instant now = Instant.now();
        String fileName = IncidentIds.fileName(now, id);
        List<String> limitations = new ArrayList<>(baseLimitations());
        if (failure == null) {
            limitations.add("Minecraft's crash report carried no exception.");
        }
        return new CrashTicket(id, now, storage, fileName, store.displayPath(storage, fileName),
                failure, attribution, List.copyOf(limitations));
    }

    public void completeCrash(CrashTicket ticket, String crashFileName, String reportType) {
        IncidentRequest request = new IncidentRequest(ticket.id(), ticket.timestamp(), IncidentKind.MINECRAFT_CRASH,
                ModIds.MARIESLIB, ticket.storageMod(), "minecraft crash report", null,
                FailureCategory.CRASH, Severity.FATAL, ticket.failure(), null, null, ticket.attribution(),
                Map.of(), false, true, crashFileName, reportType, 1, ticket.limitations());
        DiagnosticsGuard.run(() -> write(request, ticket.fileName()));
    }

    public void shutdown(long millis) {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(millis, TimeUnit.MILLISECONDS)) {
                LOG.warn("Diagnostics shut down with {} report(s) still unwritten", writer.getQueue().size());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        providers.shutdown();
    }

    private void write(IncidentRequest request) {
        write(request, IncidentIds.fileName(request.timestamp(), request.id()));
    }

    private void write(IncidentRequest request, String fileName) {
        List<DiagnosticProfile> known = profiles.get();
        DiagnosticProfile owner = null;
        for (DiagnosticProfile p : known) {
            if (p.modId().equals(request.storageMod())) {
                owner = p;
            }
        }
        int retention = owner != null ? owner.retention() : DiagnosticProfile.DEFAULT_RETENTION;
        try {
            JsonObject report = new IncidentReportBuilder(known, captures, providers, modVersion).build(request);
            String warning = store.write(request.storageMod(), fileName, report, retention);
            LOG.info("Wrote diagnostic report {} for {} to config/{}", request.id(), request.reportingMod(),
                    store.displayPath(request.storageMod(), fileName));
            if (warning != null) {
                LOG.warn("Diagnostic report {} saved, but {}", request.id(), warning);
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("Could not write diagnostic report {} for {}", request.id(), request.reportingMod(), e);
        }
    }

    private List<String> baseLimitations() {
        List<String> out = new ArrayList<>();
        try {
            if (!loadingComplete.getAsBoolean()) {
                out.add("Captured before mod loading finished; some mods may not have registered yet.");
            }
        } catch (Throwable t) {
            out.add("Mod loading state unknown.");
        }
        return out;
    }

    private static StackTraceElement[] reportSite() {
        StackTraceElement[] all = Thread.currentThread().getStackTrace();
        int start = 0;
        while (start < all.length && (all[start].getClassName().startsWith("java.lang.Thread")
                || all[start].getClassName().startsWith("dev.marie.framework.diagnostics.service.")
                || all[start].getClassName().startsWith("dev.marie.framework.diagnostics.api."))) {
            start++;
        }
        return Arrays.copyOfRange(all, start, all.length);
    }

    private static String dedupeTail(@Nullable Throwable failure, @Nullable String description) {
        if (failure == null) {
            return "description:" + description;
        }
        StackTraceElement[] trace = failure.getStackTrace();
        return failure.getClass().getName() + "@" + (trace.length > 0 ? trace[0] : "?");
    }

    private void logSuppressed(String mod, @Nullable String component, SubmissionLimiter.Admission admission) {
        if (admission.occurrence() == MAX_DUPLICATES + 1 || admission.occurrence() == 1) {
            LOG.warn("Not writing more reports for {} ({}): {}", mod, component, admission.reason());
        } else {
            LOG.debug("Suppressed report #{} for {} ({}): {}", admission.occurrence(), mod, component, admission.reason());
        }
    }

    private static String clip(String s) {
        return s.length() > 2000 ? s.substring(0, 2000) + "..." : s;
    }
}
