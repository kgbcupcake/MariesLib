package dev.marie.framework.api.marieapi;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.api.registry.ValueRegistry;
import dev.marie.framework.api.value.ValueDefinition;
import dev.marie.framework.core.MarieContext;
import dev.marie.framework.core.MarieRegistrationDelegate;
import dev.marie.framework.util.MarieRegistryUtils;
import net.minecraft.resources.ResourceLocation;

@ApiStatus.Stable
final class ValueSourceRegistrationDelegate {

    private ValueSourceRegistrationDelegate() {}

    static void registerValue(ValueDefinition definition) {
        MarieAPIState.assertRegistrationAllowed("registerValue");
        MarieRegistrationDelegate delegate = MarieContext.get().registrationDelegate();
        if (delegate == null) {
            throw new IllegalStateException("MarieLib registration delegate not configured");
        }
        if (definition == null) {
            throw new IllegalArgumentException("registerValue: definition must not be null");
        }
        String id = definition.getId();
        if (!dev.marie.framework.util.MarieValidation.sanitizeModId(id)) {
            throw new IllegalArgumentException(
                    "registerValue: value id must match [a-z0-9_]{1,64}, got: '" + id + "'");
        }
        if (delegate.getValueKeys().contains(id)) {
            throw new IllegalArgumentException("Value already registered: " + id);
        }
        delegate.registerValue(definition);
        ValueRegistry.register(definition);
    }

    static void registerSourceClassification(ResourceLocation sourceId, String valueKey, float amount) {
        MarieAPIState.assertRegistrationAllowed("registerSourceClassification");
        dev.marie.framework.util.MarieValidation.requireNonNullId(sourceId, "MarieAPI.registerSourceClassification");
        if (!Float.isFinite(amount)) {
            throw new IllegalArgumentException("MarieAPI.registerSourceClassification.amount: value must be finite, got " + amount);
        }
        if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(sourceId)) {
            // Packs routinely ship mappings for optional mods; an entry for a mod that isn't installed
            // can never resolve, so drop it quietly instead of warning (and syncing it to clients).
            // A missing item from an installed mod is still a likely typo and keeps the warning.
            if (!net.neoforged.fml.ModList.get().isLoaded(sourceId.getNamespace())) {
                org.slf4j.LoggerFactory.getLogger(MarieAPI.class).debug("[MarieAPI] registerSourceClassification: skipping '{}' — mod '{}' not loaded", sourceId, sourceId.getNamespace());
                return;
            }
            org.slf4j.LoggerFactory.getLogger(MarieAPI.class).warn("[MarieAPI] registerSourceClassification: item '{}' not found in BuiltInRegistries.ITEM", sourceId);
        }
        MarieRegistryUtils.requireValueKey(valueKey, "MarieAPI.registerSourceClassification");
        MarieRegistrationDelegate delegate = MarieContext.get().registrationDelegate();
        if (delegate == null) {
            throw new IllegalStateException("MarieLib registration delegate not configured");
        }
        delegate.registerSourceClassification(sourceId, valueKey, amount);
    }
}
