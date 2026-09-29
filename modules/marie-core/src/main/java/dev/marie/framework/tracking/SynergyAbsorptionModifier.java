package dev.marie.framework.tracking;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.api.effects.AbsorptionModifier;

import net.minecraft.world.entity.player.Player;

/**
 * Applies temporary absorption multipliers granted by source pair synergies,
 * as tracked by {@link SynergyBuffTracker}.
 */
@ApiStatus.Internal
public final class SynergyAbsorptionModifier implements AbsorptionModifier {

    @Override
    public String getModifierId() {
        return "marieslib:synergy_buff";
    }

    @Override
    public float getAbsorptionMultiplier(Player player, String valueKey, float baseAmount) {
        long currentTick = player.level().getGameTime();
        return SynergyBuffTracker.getActiveModifier(player.getUUID(), valueKey, currentTick);
    }
}
