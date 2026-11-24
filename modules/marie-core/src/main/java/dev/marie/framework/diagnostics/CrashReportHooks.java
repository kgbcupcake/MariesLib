package dev.marie.framework.diagnostics;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.core.MarieCore;
import net.minecraft.CrashReport;

import javax.annotation.Nullable;
import java.nio.file.Path;

@ApiStatus.Internal
public final class CrashReportHooks {

    @Nullable
    private static volatile CrashReportListener listener;

    private CrashReportHooks() {}

    public static void setListener(@Nullable CrashReportListener newListener) {
        listener = newListener;
    }

    @Nullable
    public static CrashReportListener.PendingCrash prepare(CrashReport report) {
        CrashReportListener current = listener;
        if (current == null) {
            return null;
        }
        try {
            return current.prepare(report);
        } catch (Throwable t) {
            logFailure("prepare", t);
            return null;
        }
    }

    public static void saved(@Nullable CrashReportListener.PendingCrash pending, Path file) {
        if (pending == null) {
            return;
        }
        try {
            pending.saved(file);
        } catch (Throwable t) {
            logFailure("write", t);
        }
    }

    private static void logFailure(String stage, Throwable t) {
        try {
            MarieCore.LOGGER.error("[MariesLib Diagnostics] Crash companion report failed at {} stage;"
                    + " Minecraft's crash report is unaffected", stage, t);
        } catch (Throwable ignored) {
        }
    }
}
