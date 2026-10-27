package dev.marie.framework.config;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.core.MarieCore;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * marie-config's own entry point into the merged MariesLib mod. marie-core can't see this module,
 * so it can't close the config registration window itself the way it does for its own registries
 * in {@code MarieBootstrap#attachFrameworkServices}.
 *
 * <p>Freezes {@link ConfigRegistry} and {@link ConfigCategoryRegistry} from enqueued common-setup
 * work, which runs after every mod's {@link FMLCommonSetupEvent} handlers, so the window matches
 * the documented one: mod constructor or common setup.
 */
@Mod(MarieCore.MOD_ID)
@ApiStatus.Internal
public final class MariesLibConfigBootstrap {

    public MariesLibConfigBootstrap(IEventBus modEventBus) {
        modEventBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            ConfigRegistry.freezeInternal();
            ConfigCategoryRegistry.freezeInternal();
        }));
    }
}
