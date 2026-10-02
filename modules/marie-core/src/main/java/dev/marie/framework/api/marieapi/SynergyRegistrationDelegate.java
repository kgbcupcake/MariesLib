package dev.marie.framework.api.marieapi;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.api.effects.SynergyDefinition;
import dev.marie.framework.api.registry.SynergyRegistry;
import dev.marie.framework.api.source.SourcePairSynergy;

@ApiStatus.Stable
final class SynergyRegistrationDelegate {

    private SynergyRegistrationDelegate() {}

    static void registerValueSynergy(SynergyDefinition definition) {
        MarieAPIState.assertRegistrationAllowed("registerValueSynergy");
        SynergyRegistry.registerValueSynergy(definition);
    }

    static void registerSourcePairSynergy(SourcePairSynergy definition) {
        MarieAPIState.assertRegistrationAllowed("registerSourcePairSynergy");
        SynergyRegistry.registerSourcePairSynergy(definition);
    }
}
