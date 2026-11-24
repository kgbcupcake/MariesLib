package dev.marie.framework.diagnostics.devtest;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.diagnostics.api.DiagnosticProfile;
import dev.marie.framework.diagnostics.api.DiagnosticsAPI;
import dev.marie.framework.diagnostics.api.FailureCategory;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Optional;

@ApiStatus.Internal
public final class DevTestDiagnostics {

    public static final String MOD_ID = "marieslib_devtest";

    private static volatile boolean crashArmed;

    private DevTestDiagnostics() {}

    public static void register() {
        DiagnosticsAPI.register(DiagnosticProfile.builder(MOD_ID)
                .packages("dev.marie.framework.diagnostics.devtest")
                .retention(5)
                .provider("probe", (ctx, section) -> section
                        .entry("incident", ctx.incidentId())
                        .entry("kind", ctx.kind())
                        .entry("javaThreads", Thread.activeCount()))
                .provider("broken_probe", (ctx, section) -> {
                    throw new IllegalStateException("deliberate provider failure (dev test)");
                })
                .build());
        NeoForge.EVENT_BUS.addListener(DevTestDiagnostics::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(DevTestDiagnostics::onServerTick);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("marieslib")
                .then(Commands.literal("diagnostics")
                        .then(Commands.literal("devtest")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.literal("report").executes(DevTestDiagnostics::report))
                                .then(Commands.literal("crash").executes(DevTestDiagnostics::armCrash)))));
    }

    private static int report(CommandContext<CommandSourceStack> ctx) {
        Optional<String> id = DiagnosticsAPI.reportFailure(MOD_ID,
                new IllegalStateException("dev test failure", new ArithmeticException("dev test cause")),
                f -> f.component("DevTestDiagnostics")
                        .subsystem("devtest")
                        .category(FailureCategory.OTHER)
                        .context("trigger", "command"));
        ctx.getSource().sendSuccess(() -> Component.literal(id
                .map(i -> "Reported dev test failure, incident " + i + " (config/" + MOD_ID + "/crash-reports/)")
                .orElse("Dev test failure was not written (limit reached)")), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int armCrash(CommandContext<CommandSourceStack> ctx) {
        crashArmed = true;
        ctx.getSource().sendSuccess(() -> Component.literal("Crashing the server on the next tick (dev test)"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (crashArmed) {
            crashArmed = false;
            throw new DevTestCrash();
        }
    }

    static final class DevTestCrash extends RuntimeException {
        DevTestCrash() {
            super("Deliberate MariesLib diagnostics dev test crash");
        }
    }
}
