package dev.marie.framework.mixin;

import dev.marie.framework.diagnostics.CrashReportHooks;
import dev.marie.framework.diagnostics.CrashReportListener;
import net.minecraft.CrashReport;
import net.minecraft.ReportType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.List;

@Mixin(CrashReport.class)
public abstract class CrashReportMixin {

    @Shadow
    @Nullable
    private Path saveFile;

    @Unique
    @Nullable
    private CrashReportListener.PendingCrash marieslib$pendingCrash;

    @Inject(method = "saveToFile(Ljava/nio/file/Path;Lnet/minecraft/ReportType;Ljava/util/List;)Z", at = @At("HEAD"))
    private void marieslib$beforeSave(Path path, ReportType type, List<String> links,
                                      CallbackInfoReturnable<Boolean> cir) {
        try {
            if (this.saveFile == null && type == ReportType.CRASH && this.marieslib$pendingCrash == null) {
                this.marieslib$pendingCrash = CrashReportHooks.prepare((CrashReport) (Object) this);
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "saveToFile(Ljava/nio/file/Path;Lnet/minecraft/ReportType;Ljava/util/List;)Z", at = @At("RETURN"))
    private void marieslib$afterSave(Path path, ReportType type, List<String> links,
                                     CallbackInfoReturnable<Boolean> cir) {
        try {
            CrashReportListener.PendingCrash pending = this.marieslib$pendingCrash;
            if (pending != null && Boolean.TRUE.equals(cir.getReturnValue())) {
                this.marieslib$pendingCrash = null;
                CrashReportHooks.saved(pending, path);
            }
        } catch (Throwable ignored) {
        }
    }
}
