package dev.marie.framework.util;

import dev.marie.framework.api.ApiStatus;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;

import java.util.Optional;

/**
 * Helpers for resolving mob effect ids from strings.
 */
@ApiStatus.Stable
public final class MarieEffectUtils {

    private MarieEffectUtils() {}

    /**
     * Resolves a mob effect id string to a holder. Returns empty if the id is not a valid
     * {@link ResourceLocation}, or if the effect is not registered.
     */
    public static Optional<? extends Holder<MobEffect>> resolveEffect(String id) {
        try {
            ResourceLocation rl = ResourceLocation.parse(id);
            return BuiltInRegistries.MOB_EFFECT.getHolder(rl);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
