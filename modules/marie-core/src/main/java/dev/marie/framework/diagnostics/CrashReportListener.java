package dev.marie.framework.diagnostics;

import dev.marie.framework.api.ApiStatus;
import net.minecraft.CrashReport;

import javax.annotation.Nullable;
import java.nio.file.Path;

@ApiStatus.Internal
public interface CrashReportListener {

    @Nullable
    PendingCrash prepare(CrashReport report);

    interface PendingCrash {

        void saved(Path file);
    }
}
