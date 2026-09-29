package dev.marie.framework.api.marieapi;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.compat.CompatDefinition;

@ApiStatus.Stable
final class CompatRegistrationDelegate {

    private CompatRegistrationDelegate() {}

    static void registerCompatEntry(CompatDefinition definition) {
        MarieAPIState.assertRegistrationAllowed("registerCompatEntry");
        dev.marie.framework.compat.ModCompat.registerExternal(definition);
    }
}
