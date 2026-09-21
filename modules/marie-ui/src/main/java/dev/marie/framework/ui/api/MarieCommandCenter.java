package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.commandcenter.CommandCenterRegistry;
import dev.marie.framework.ui.commandcenter.CommandCenterScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Public facade for the generic, domain-agnostic command-center screen: a single shared
 * sidebar-navigated dashboard that any number of consumer mods can contribute categories and
 * cards to, rather than each mod building its own standalone settings/quick-actions screen.
 *
 * <p>Contributing content is done ahead of time through {@link CommandCenterRegistry}
 * ({@code registerCategory}/{@code registerCard}/{@code registerCustomCard}, gated to MarieLib's
 * registration phase); this facade's only job is opening the resulting shared screen.
 *
 * <pre>{@code
 * // during mod init, register what this mod contributes:
 * CommandCenterRegistry.registerCategory(new CommandCenterCategory("mymod", Component.literal("My Mod"), 100));
 * CommandCenterRegistry.registerCard(new CommandCenterCard(
 *         "mymod.toggle_feature", "mymod",
 *         Component.literal("Toggle Feature"), Component.literal("Enable/disable the thing"),
 *         0xFF5DA9E9, () -> MyModConfig.toggleFeature()));
 *
 * // wherever the screen should open, e.g. a keybind handler:
 * MarieCommandCenter.openScreen();
 * }</pre>
 */
@ApiStatus.Experimental
public final class MarieCommandCenter {

    private static KeyMapping openKey;

    private MarieCommandCenter() {}

    /**
     * Registers the shared "Open Command Center" keybind (client only, call from the mod constructor
     * with the mod event bus). Idempotent: every consuming mod may call it and only the first call
     * registers, so the player sees one entry under the MariesLib category. Unbound by default so it
     * cannot clash with anything; the player assigns a key in Controls. The key only opens the screen
     * when no other screen is open.
     */
    public static synchronized void registerOpenKey(IEventBus modEventBus) {
        if (openKey != null) {
            return;
        }
        openKey = new KeyMapping("key.marieslib.open_command_center", InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(), "key.categories.marieslib");
        modEventBus.addListener((RegisterKeyMappingsEvent event) -> event.register(openKey));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            Minecraft mc = Minecraft.getInstance();
            while (openKey.consumeClick()) {
                if (mc.screen == null && mc.level != null) {
                    openScreen();
                }
            }
        });
    }

    /** Opens {@link CommandCenterScreen} over whatever screen is currently open (or none). */
    public static void openScreen() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new CommandCenterScreen(mc.screen));
    }
}
