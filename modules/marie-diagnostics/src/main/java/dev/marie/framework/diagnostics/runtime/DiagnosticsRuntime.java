package dev.marie.framework.diagnostics.runtime;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.diagnostics.api.DiagnosticSection;
import dev.marie.framework.diagnostics.capture.ProviderRunner;
import dev.marie.framework.diagnostics.registry.DiagnosticModRegistry;
import dev.marie.framework.diagnostics.report.ReportStore;
import dev.marie.framework.diagnostics.service.DiagnosticsService;
import dev.marie.framework.diagnostics.service.SubmissionLimiter;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.util.List;

@ApiStatus.Internal
public final class DiagnosticsRuntime {

    static final long SHUTDOWN_DRAIN_MS = 2000L;

    @Nullable
    private static volatile DiagnosticsService service;

    private DiagnosticsRuntime() {}

    public static DiagnosticsService service() {
        DiagnosticsService s = service;
        if (s == null) {
            synchronized (DiagnosticsRuntime.class) {
                s = service;
                if (s == null) {
                    s = new DiagnosticsService(
                            new ReportStore(FMLPaths.CONFIGDIR.get()),
                            DiagnosticModRegistry::all,
                            List.of(new EnvironmentCapture(), new SessionCapture()),
                            new ProviderRunner(DiagnosticSection.TIME_LIMIT_MS),
                            DiagnosticsRuntime::modVersion,
                            DiagnosticModRegistry::isFrozen,
                            new SubmissionLimiter(DiagnosticsService.MAX_INCIDENTS_PER_SESSION,
                                    DiagnosticsService.MAX_DUPLICATES));
                    service = s;
                }
            }
        }
        return s;
    }

    static void shutdown() {
        DiagnosticsService s = service;
        if (s != null) {
            s.shutdown(SHUTDOWN_DRAIN_MS);
        }
    }

    @Nullable
    static String modVersion(String modId) {
        ModList list = ModList.get();
        if (list == null) {
            return null;
        }
        return list.getModContainerById(modId)
                .map(c -> String.valueOf(c.getModInfo().getVersion()))
                .orElse(null);
    }
}
