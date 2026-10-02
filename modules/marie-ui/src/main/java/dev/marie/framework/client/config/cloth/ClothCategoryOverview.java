package dev.marie.framework.client.config.cloth;

import dev.marie.framework.api.ApiStatus;

import java.util.List;
import java.util.stream.Collectors;

import dev.marie.framework.api.registry.ValueRegistry;
import dev.marie.framework.core.MarieContext;
import dev.marie.framework.core.MarieModRegistry;
import dev.marie.framework.core.MarieCore;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

/**
 * Overview tab: read-only framework status information.
 */
@ApiStatus.Internal
final class ClothCategoryOverview {

    private ClothCategoryOverview() {}

    static void build(ConfigBuilder builder, ConfigEntryBuilder entryBuilder) {
        ConfigCategory cat = ClothConfigHelper.category(builder, "overview");

        String version = ModList.get()
                .getModContainerById(MarieCore.MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("unknown");

        cat.addEntry(entryBuilder.startTextDescription(
                Component.translatable(ClothConfigHelper.key("overview.version"), version)
        ).build());

        cat.addEntry(entryBuilder.startTextDescription(
                Component.translatable(ClothConfigHelper.key("overview.configPath"), "config/marieslib.cfg")
        ).build());

        List<MarieContext> mods = MarieModRegistry.getAll();
        int modCount = mods.size();
        String modNames = mods.isEmpty()
                ? Component.translatable(ClothConfigHelper.key("overview.noMods")).getString()
                : mods.stream().map(MarieContext::modId).collect(Collectors.joining(", "));

        cat.addEntry(entryBuilder.startTextDescription(
                Component.translatable(ClothConfigHelper.key("overview.registeredMods"), modCount + " (" + modNames + ")")
        ).build());

        MarieContext primary = MarieModRegistry.getPrimaryOrNull();
        String primaryName = primary != null ? primary.modId() : "none";
        cat.addEntry(entryBuilder.startTextDescription(
                Component.translatable(ClothConfigHelper.key("overview.primaryMod"), primaryName)
        ).build());

        int valueCount = ValueRegistry.getAll().size();
        cat.addEntry(entryBuilder.startTextDescription(
                Component.translatable(ClothConfigHelper.key("overview.registeredValues"), valueCount)
        ).build());

        cat.addEntry(entryBuilder.startTextDescription(
                ClothConfigHelper.t("overview.gameplayHint")
        ).build());
    }
}
