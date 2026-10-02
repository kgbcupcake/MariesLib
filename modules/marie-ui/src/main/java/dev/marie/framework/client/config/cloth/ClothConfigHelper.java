package dev.marie.framework.client.config.cloth;

import dev.marie.framework.api.ApiStatus;

import java.util.Optional;

import dev.marie.framework.config.LockRegistry;
import dev.marie.framework.config.MariesLibConfigHolder;
import dev.marie.framework.core.MarieCore;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Shared Cloth Config helpers: lock filtering, translation keys, entry builders.
 */
@ApiStatus.Internal
final class ClothConfigHelper {

    private ClothConfigHelper() {}

    static String key(String suffix) {
        return "config." + MarieCore.MOD_ID + "." + suffix;
    }

    static Component t(String suffix) {
        return Component.translatable(key(suffix));
    }

    private static Component[] tooltip(String labelSuffix) {
        return new Component[]{Component.translatable(key(labelSuffix + ".desc"))};
    }

    static boolean shouldShow(String lockKey) {
        return !LockRegistry.isLocked(lockKey);
    }

    static boolean isEditable(String lockKey) {
        if (LockRegistry.isServerOnly(lockKey)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getConnection() != null && !mc.hasSingleplayerServer()) {
                return false;
            }
        }
        return true;
    }

    static AbstractConfigListEntry<?> buildBool(
            ConfigEntryBuilder entryBuilder,
            String lockKey,
            String labelSuffix,
            boolean current,
            boolean defaultValue,
            java.util.function.Consumer<Boolean> save) {
        if (!shouldShow(lockKey)) {
            return null;
        }
        AbstractConfigListEntry<?> entry = entryBuilder.startBooleanToggle(t(labelSuffix), current)
                .setDefaultValue(defaultValue)
                .setTooltip(Optional.of(tooltip(labelSuffix)))
                .setSaveConsumer(v -> { if (isEditable(lockKey)) save.accept(v); })
                .build();
        entry.setEditable(isEditable(lockKey));
        return entry;
    }

    static AbstractConfigListEntry<?> buildFloat(
            ConfigEntryBuilder entryBuilder,
            String lockKey,
            String labelSuffix,
            float current,
            float defaultValue,
            float min,
            float max,
            java.util.function.Consumer<Float> save) {
        if (!shouldShow(lockKey)) {
            return null;
        }
        var field = entryBuilder.startFloatField(t(labelSuffix), current)
                .setDefaultValue(defaultValue)
                .setMin(min)
                .setTooltip(Optional.of(tooltip(labelSuffix)))
                .setSaveConsumer(v -> { if (isEditable(lockKey)) save.accept(v); });
        if (max < Float.MAX_VALUE / 2f) {
            field.setMax(max);
        }
        AbstractConfigListEntry<?> entry = field.build();
        entry.setEditable(isEditable(lockKey));
        return entry;
    }

    static AbstractConfigListEntry<?> buildDouble(
            ConfigEntryBuilder entryBuilder,
            String lockKey,
            String labelSuffix,
            double current,
            double defaultValue,
            double min,
            double max,
            java.util.function.Consumer<Double> save) {
        if (!shouldShow(lockKey)) {
            return null;
        }
        AbstractConfigListEntry<?> entry = entryBuilder.startDoubleField(t(labelSuffix), current)
                .setDefaultValue(defaultValue)
                .setMin(min)
                .setMax(max)
                .setTooltip(Optional.of(tooltip(labelSuffix)))
                .setSaveConsumer(v -> { if (isEditable(lockKey)) save.accept(v); })
                .build();
        entry.setEditable(isEditable(lockKey));
        return entry;
    }

    static MariesLibConfigHolder holder() {
        return MariesLibConfigHolder.get();
    }

    static ConfigCategory category(ConfigBuilder builder, String suffix) {
        return builder.getOrCreateCategory(t("category." + suffix));
    }
}
