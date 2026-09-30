package dev.marie.framework.api.marieapi;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.compat.CompatDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

@ApiStatus.Stable
final class CompatRegistrationDelegate {

    private CompatRegistrationDelegate() {}

    static void registerCompatEntry(CompatDefinition definition) {
        MarieAPIState.assertRegistrationAllowed("registerCompatEntry");
        dev.marie.framework.compat.ModCompat.registerExternal(definition);
        for (Map.Entry<ResourceLocation, CompatDefinition.SourceMapping> entry : definition.getSourceMappingDetails().entrySet()) {
            CompatDefinition.SourceMapping mapping = entry.getValue();
            ValueSourceRegistrationDelegate.registerSourceClassification(entry.getKey(), mapping.valueKey(), mapping.amount());
        }
    }
}
