package dev.marie.framework.api.marieapi;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.api.source.SourceTriggerDefinition;
import dev.marie.framework.api.source.SourceTriggerListener;
import dev.marie.framework.runtime.SourceTriggerRegistry;
import dev.marie.framework.runtime.TriggerHandlerRegistry;
import dev.marie.framework.util.MarieRegistryUtils;

@ApiStatus.Stable
final class TriggerRegistrationDelegate {

    private TriggerRegistrationDelegate() {}

    static void registerTriggerHandler(SourceTriggerListener handler) {
        MarieAPIState.assertRegistrationAllowed("registerTriggerHandler");
        if (handler == null) {
            throw new IllegalArgumentException("handler cannot be null");
        }
        TriggerHandlerRegistry.register(handler);
    }

    static void registerTriggerSource(SourceTriggerDefinition definition) {
        MarieAPIState.assertRegistrationAllowed("registerTriggerSource");
        if (definition == null) {
            throw new IllegalArgumentException("definition cannot be null");
        }
        MarieRegistryUtils.requireValueKey(definition.getValueKey(), "registerTriggerSource");
        SourceTriggerRegistry.register(definition);
    }
}
