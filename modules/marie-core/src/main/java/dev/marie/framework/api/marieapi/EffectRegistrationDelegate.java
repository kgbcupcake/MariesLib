package dev.marie.framework.api.marieapi;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.api.effects.ThresholdEffect;
import dev.marie.framework.core.MarieContext;
import dev.marie.framework.core.MarieRegistrationDelegate;

@ApiStatus.Stable
final class EffectRegistrationDelegate {

    private EffectRegistrationDelegate() {}

    static void registerCustomEffect(ThresholdEffect definition) {
        MarieAPIState.assertRegistrationAllowed("registerCustomEffect");
        MarieRegistrationDelegate delegate = MarieContext.get().registrationDelegate();
        if (delegate == null) {
            throw new IllegalStateException("MarieLib registration delegate not configured");
        }
        delegate.registerEffect(definition);
    }
}
