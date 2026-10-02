package dev.marie.framework.api.marieapi;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.command.CommandCapability;
import dev.marie.framework.command.CommandCapabilityRegistry;
import net.minecraft.resources.ResourceLocation;

@ApiStatus.Stable
final class CommandCapabilityDelegate {

    private CommandCapabilityDelegate() {}

    static void registerCommandCapability(
            ResourceLocation modId,
            ResourceLocation capability,
            CommandCapability handler) {
        MarieAPIState.assertRegistrationAllowed("registerCommandCapability");
        CommandCapabilityRegistry.register(modId, capability, handler);
    }
}
