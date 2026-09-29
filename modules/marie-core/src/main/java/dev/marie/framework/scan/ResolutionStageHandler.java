package dev.marie.framework.scan;

import dev.marie.framework.api.ApiStatus;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * A single step in the runtime source resolution cascade.
 * Implementations must be stateless with respect to the resolution call (all mutable
 * context lives in {@link StageContext}) and must return {@code null} to defer to the next stage.
 */
@ApiStatus.Experimental
public interface ResolutionStageHandler {

    @Nullable
    ResolutionResult resolve(ResourceLocation itemId, StageContext ctx);
}
