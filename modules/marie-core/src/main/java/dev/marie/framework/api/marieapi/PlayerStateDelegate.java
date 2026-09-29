package dev.marie.framework.api.marieapi;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.api.impl.EmptyApplicationHistoryView;
import dev.marie.framework.api.marie.MariePlayerData;
import dev.marie.framework.api.reporting.ApplicationHistoryView;
import dev.marie.framework.api.value.ValueModifierContext;
import dev.marie.framework.api.value.ValueModifierEvent;
import dev.marie.framework.core.IMarieConfig;
import dev.marie.framework.core.MarieContext;
import dev.marie.framework.core.MarieDataProvider;
import dev.marie.framework.core.MarieRegistrationDelegate;
import dev.marie.framework.util.MarieRegistryUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@ApiStatus.Stable
final class PlayerStateDelegate {

    private PlayerStateDelegate() {}

    static ResourceLocation apiModifierSource() {
        String modId = IMarieConfig.get().modId();
        return ResourceLocation.fromNamespaceAndPath(modId, "api");
    }

    static float getAggregateLevel(Player player) {
        if (player == null) {
            return 0f;
        }
        MarieDataProvider provider = MarieContext.get().dataProvider();
        if (provider == null) {
            return 0f;
        }
        return provider.getAggregateLevel(player);
    }

    static float getValueLevel(Player player, String valueKey) {
        if (player == null) {
            return -1.0f;
        }
        MarieDataProvider provider = MarieContext.get().dataProvider();
        if (provider == null) {
            return -1.0f;
        }
        return provider.getValueLevel(player, valueKey);
    }

    static ApplicationHistoryView getApplicationHistory(Player player) {
        if (player == null) {
            return EmptyApplicationHistoryView.INSTANCE;
        }
        MarieDataProvider provider = MarieContext.get().dataProvider();
        if (provider == null) {
            return EmptyApplicationHistoryView.INSTANCE;
        }
        return provider.getApplicationHistoryView(player);
    }

    static MariePlayerData getTrackingData(Player player) {
        Map<String, Float> values = new LinkedHashMap<>();
        MarieRegistrationDelegate delegate = MarieContext.get().registrationDelegate();
        if (delegate != null) {
            for (String valueKey : delegate.getValueKeys()) {
                values.put(valueKey, getValueLevel(player, valueKey));
            }
        }
        return new MariePlayerData(
                getAggregateLevel(player),
                Collections.unmodifiableMap(values),
                getApplicationHistory(player)
        );
    }

    static void modifyValue(Player player, String valueKey, float delta) {
        MarieRegistryUtils.requireValueKey(valueKey, "MarieAPI.modifyValue");
        MarieDataProvider provider = MarieContext.get().dataProvider();
        if (provider == null) {
            return;
        }
        ValueModifierEvent modifierEvent = new ValueModifierEvent(
                ValueModifierContext.of(player, apiModifierSource(), valueKey), delta);
        NeoForge.EVENT_BUS.post(modifierEvent);
        if (modifierEvent.isCanceled()) {
            return;
        }
        provider.modifyValue(player, valueKey, modifierEvent.getAmount());
    }
}
