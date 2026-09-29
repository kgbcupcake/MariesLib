package dev.marie.framework.api.marieapi;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.api.effects.AbsorptionModifier;
import dev.marie.framework.api.effects.SleepBonusEvaluator;
import dev.marie.framework.api.hover.BlockHoverProvider;
import dev.marie.framework.api.registry.AbsorptionModifierRegistry;
import dev.marie.framework.api.registry.BlockHoverProviderRegistry;
import dev.marie.framework.api.registry.SleepBonusEvaluatorRegistry;
import dev.marie.framework.api.registry.SourcePropertySignalRegistry;
import dev.marie.framework.api.reporting.ExportResolver;
import dev.marie.framework.api.source.SourcePropertySignal;

@ApiStatus.Stable
final class HookProviderRegistrationDelegate {

    private HookProviderRegistrationDelegate() {}

    static void registerBlockHoverProvider(BlockHoverProvider provider) {
        if (!MarieAPIState.isRegistrationAllowed()) {
            throw new IllegalStateException(
                    "[MarieAPI] Registration closed — registerBlockHoverProvider must be called during mod initialization or datapack reload.");
        }
        if (provider == null) {
            throw new IllegalArgumentException("provider cannot be null");
        }
        BlockHoverProviderRegistry.register(provider);
    }

    static void registerSourcePropertySignal(SourcePropertySignal signal) {
        MarieAPIState.assertRegistrationAllowed("registerSourcePropertySignal");
        if (signal == null) {
            throw new IllegalArgumentException("signal cannot be null");
        }
        SourcePropertySignalRegistry.register(signal);
    }

    static void registerAbsorptionModifier(AbsorptionModifier modifier) {
        MarieAPIState.assertRegistrationAllowed("registerAbsorptionModifier");
        AbsorptionModifierRegistry.register(modifier);
    }

    static <T> void registerExportResolver(ExportResolver<T> resolver) {
        throw new UnsupportedOperationException(
                "[MarieAPI] registerExportResolver(ExportResolver) cannot be implemented: "
                        + "ExportResolver carries no registry key, so there is no way to know which "
                        + "registry to iterate. Use registerExportResolver(String, ResourceKey, ExportResolver) instead.");
    }

    static <T> void registerExportResolver(
            String key,
            net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<T>> registryKey,
            ExportResolver<T> resolver) {
        dev.marie.framework.export.ExportResolverRegistry.registerWithRegistry(key, registryKey, resolver);
    }

    static void registerSleepBonusEvaluator(SleepBonusEvaluator evaluator) {
        MarieAPIState.assertRegistrationAllowed("registerSleepBonusEvaluator");
        if (evaluator == null) {
            throw new IllegalArgumentException("evaluator cannot be null");
        }
        SleepBonusEvaluatorRegistry.register(evaluator);
    }
}
