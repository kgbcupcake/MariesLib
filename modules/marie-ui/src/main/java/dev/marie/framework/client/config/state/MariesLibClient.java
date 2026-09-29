package dev.marie.framework.client.config.state;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.client.config.cloth.MariesLibClothConfig;
import dev.marie.framework.client.config.importexport.MariesLibExportScreen;
import dev.marie.framework.client.config.importexport.MariesLibImportScreen;
import dev.marie.framework.client.ClientSessionTeardownListener;
import dev.marie.framework.core.MarieCore;
import dev.marie.framework.core.MarieBootstrap;
import dev.marie.framework.ui.api.MarieNotifications;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = MarieCore.MOD_ID, dist = Dist.CLIENT)
@ApiStatus.Internal
public final class MariesLibClient {

    public MariesLibClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (modContainer, parent) -> MariesLibClothConfig.create(parent));
        MarieBootstrap.setConfigScreenFactory(() -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            return MariesLibClothConfig.create(mc.screen);
        });
        MarieBootstrap.setExportScreenFactory(parent -> new MariesLibExportScreen((Screen) parent));
        MarieBootstrap.setImportScreenFactory(parent -> new MariesLibImportScreen((Screen) parent));
        MarieNotifications.registerClientListeners();

        // Client-side session teardown: marie-core owns the disconnect listener and clears its own
        // client caches; the marie-ui client caches it cannot see are contributed here as steps.
        ClientSessionTeardownListener.register();
        ClientSessionTeardownListener.registerAdditionalTeardown(MarieClientCache::resetDiagnostics);
        ClientSessionTeardownListener.registerAdditionalTeardown(MarieClientState::reset);
        ClientSessionTeardownListener.registerAdditionalTeardown(MarieNotifications::clear);
    }
}
