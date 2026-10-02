package dev.marie.framework.scan;

import dev.marie.framework.api.ApiStatus;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

@ApiStatus.Internal
public record CacheStats(
        int hits,
        int misses,
        int size,
        long avgResolveNanos,
        long slowestResolveNanos,
        @Nullable ResourceLocation slowestItem,
        int recipeTimeouts
) {}
