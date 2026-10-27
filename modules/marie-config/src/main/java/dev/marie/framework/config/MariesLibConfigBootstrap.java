package dev.marie.framework.config;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.core.MarieCore;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;

/**
 * marie-config's own entry point into the merged MariesLib mod. marie-core can't see this module,
 * so it can't close the config registration window itself the way it does for its own registries
 * in {@code MarieBootstrap#attachFrameworkServices}.
 *
 * <p>Freezes {@link ConfigRegistry} and {@link ConfigCategoryRegistry} on {@link FMLLoadCompleteEvent},
 * which fires after every mod's construction and common setup, including work they queued with
 * {@code enqueueWork}. Freezing from MariesLib's own queued common-setup work would not be safe:
 * queued work runs grouped by mod in load order, and MariesLib loads before the mods that depend on it.
 */
@Mod(MarieCore.MOD_ID)
@ApiStatus.Internal
public final class MariesLibConfigBootstrap {

    public MariesLibConfigBootstrap(IEventBus modEventBus) {
        modEventBus.addListener((FMLLoadCompleteEvent event) -> {
            ConfigRegistry.freezeInternal();
            ConfigCategoryRegistry.freezeInternal();
        });
    }
}
