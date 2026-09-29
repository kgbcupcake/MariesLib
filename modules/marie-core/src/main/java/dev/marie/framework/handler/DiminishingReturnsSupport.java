package dev.marie.framework.handler;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.core.IMarieConfig;
import dev.marie.framework.tracking.DiminishingReturnsConfig;

@ApiStatus.Internal
final class DiminishingReturnsSupport {

    private DiminishingReturnsSupport() {}

    static DiminishingReturnsConfig resolveMemoryConfig() {
        return IMarieConfig.get().trackingMemoryConfig();
    }

    static void resetMemoryConfigWarning() {
    }
}
