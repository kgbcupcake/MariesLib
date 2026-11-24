package dev.marie.framework.diagnostics;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.core.MarieCore;
import dev.marie.framework.diagnostics.api.DiagnosticProfile;
import dev.marie.framework.diagnostics.devtest.DevTestDiagnostics;
import dev.marie.framework.diagnostics.registry.DiagnosticModRegistry;
import dev.marie.framework.diagnostics.registry.ModIds;
import dev.marie.framework.diagnostics.runtime.CrashListener;
import dev.marie.framework.diagnostics.runtime.DiagnosticsRuntimeHooks;
import dev.marie.framework.diagnostics.runtime.MariesLibStateProvider;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(MarieCore.MOD_ID)
@ApiStatus.Internal
public final class MariesLibDiagnosticsBootstrap {

    public MariesLibDiagnosticsBootstrap(IEventBus modEventBus) {
        DiagnosticModRegistry.register(DiagnosticProfile.builder(ModIds.MARIESLIB)
                .packages("dev.marie.framework")
                .provider("state", new MariesLibStateProvider())
                .build());
        if (!FMLEnvironment.production) {
            DevTestDiagnostics.register();
        }
        CrashReportHooks.setListener(new CrashListener());
        modEventBus.addListener((FMLLoadCompleteEvent event) -> DiagnosticModRegistry.freezeInternal());
        DiagnosticsRuntimeHooks.installShutdownHook();
    }
}
