package dev.marie.framework.client.config.cloth;

import dev.marie.framework.api.ApiStatus;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;

/**
 * Tools tab: library-only command hints and future compiler placeholder.
 */
@ApiStatus.Internal
final class ClothCategoryTools {

    private ClothCategoryTools() {}

    static void build(ConfigBuilder builder, ConfigEntryBuilder entryBuilder) {
        ConfigCategory cat = ClothConfigHelper.category(builder, "tools");

        cat.addEntry(entryBuilder.startTextDescription(
                ClothConfigHelper.t("tools.commandsHeader")
        ).build());

        cat.addEntry(entryBuilder.startTextDescription(
                ClothConfigHelper.t("tools.commandStatus")
        ).build());

        cat.addEntry(entryBuilder.startTextDescription(
                ClothConfigHelper.t("tools.commandMods")
        ).build());

        cat.addEntry(entryBuilder.startTextDescription(
                ClothConfigHelper.t("tools.commandApi")
        ).build());

        cat.addEntry(entryBuilder.startTextDescription(
                ClothConfigHelper.t("tools.commandRegistries")
        ).build());

        cat.addEntry(entryBuilder.startTextDescription(
                ClothConfigHelper.t("tools.consumerCommandsHint")
        ).build());

        cat.addEntry(entryBuilder.startTextDescription(
                ClothConfigHelper.t("tools.compilerPlaceholder")
        ).build());
    }
}
