package dev.marie.framework.diagnostics.runtime;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.diagnostics.CrashReportListener;
import dev.marie.framework.diagnostics.service.CrashTicket;
import dev.marie.framework.diagnostics.service.DiagnosticsService;
import net.minecraft.CrashReport;

import javax.annotation.Nullable;

@ApiStatus.Internal
public final class CrashListener implements CrashReportListener {

    public static final String SYSTEM_DETAIL = "MariesLib Incident";

    @Nullable
    @Override
    public PendingCrash prepare(CrashReport report) {
        DiagnosticsService service = DiagnosticsRuntime.service();
        CrashTicket ticket = service.prepareCrash(report.getException());
        if (ticket == null) {
            return null;
        }
        report.getSystemReport().setDetail(SYSTEM_DETAIL, ticket.id() + " (config/" + ticket.displayPath() + ")");
        return file -> service.completeCrash(ticket, String.valueOf(file.getFileName()), "CRASH");
    }
}
