package dev.marie.framework.client.config.cloth;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.client.config.importexport.ImportExportButtonsWidget;
import dev.marie.framework.config.MariesLibConfigHolder;
import dev.marie.framework.config.MariesLibConfigKeys;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.client.gui.screens.Screen;

@ApiStatus.Internal
final class ClothCategoryScanner {

    private ClothCategoryScanner() {}

    static void build(ConfigBuilder builder, ConfigEntryBuilder entryBuilder, Screen parent) {
        MariesLibConfigHolder h = ClothConfigHelper.holder();
        ConfigCategory cat = ClothConfigHelper.category(builder, "scanner");

        SubCategoryBuilder classification = entryBuilder.startSubCategory(ClothConfigHelper.t("scanner.classificationGroup"));
        classification.setExpanded(true);
        addIfPresent(classification, ClothConfigHelper.buildFloat(entryBuilder, MariesLibConfigKeys.SCANNER_CONFIDENCE_SPREAD_THRESHOLD,
                "scanner.confidenceSpreadThreshold", h.scannerConfidenceSpreadThreshold, 0f, 0f, 100f,
                v -> h.scannerConfidenceSpreadThreshold = v));
        addIfPresent(classification, ClothConfigHelper.buildFloat(entryBuilder, MariesLibConfigKeys.COMPOSITE_RATIO_THRESHOLD,
                "scanner.compositeRatioThreshold", h.compositeRatioThreshold, 0.5f, 0f, 1f,
                v -> h.compositeRatioThreshold = v));
        addIfPresent(classification, ClothConfigHelper.buildBool(entryBuilder, MariesLibConfigKeys.SCANNER_ENABLE_RECIPE_INHERITANCE,
                "scanner.enableRecipeInheritance", h.scannerEnableRecipeInheritance, false,
                v -> h.scannerEnableRecipeInheritance = v));
        addIfPresent(classification, ClothConfigHelper.buildDouble(entryBuilder, MariesLibConfigKeys.MULTI_VALUE_INHERITANCE_THRESHOLD,
                "scanner.multiValueInheritanceThreshold", h.multiValueInheritanceThreshold, 0.20, 0.0, 1.0,
                v -> h.multiValueInheritanceThreshold = v));
        cat.addEntry(classification.build());

        SubCategoryBuilder signalWeights = entryBuilder.startSubCategory(ClothConfigHelper.t("scanner.signalWeightsGroup"));
        signalWeights.setExpanded(false);
        addIfPresent(signalWeights, ClothConfigHelper.buildFloat(entryBuilder, MariesLibConfigKeys.MULT_COMMUNITY_TAG,
                "scanner.multipliers.communityTag", h.multCommunityTag, 5f, 0f, 100f, v -> h.multCommunityTag = v));
        addIfPresent(signalWeights, ClothConfigHelper.buildFloat(entryBuilder, MariesLibConfigKeys.MULT_NAMESPACE,
                "scanner.multipliers.namespace", h.multNamespace, 4f, 0f, 100f, v -> h.multNamespace = v));
        addIfPresent(signalWeights, ClothConfigHelper.buildFloat(entryBuilder, MariesLibConfigKeys.MULT_SUFFIX,
                "scanner.multipliers.suffix", h.multSuffix, 3f, 0f, 100f, v -> h.multSuffix = v));
        addIfPresent(signalWeights, ClothConfigHelper.buildFloat(entryBuilder, MariesLibConfigKeys.MULT_KEYWORD,
                "scanner.multipliers.keyword", h.multKeyword, 2f, 0f, 100f, v -> h.multKeyword = v));
        addIfPresent(signalWeights, ClothConfigHelper.buildFloat(entryBuilder, MariesLibConfigKeys.MULT_ARCHETYPE,
                "scanner.multipliers.archetype", h.multArchetype, 2f, 0f, 100f, v -> h.multArchetype = v));
        addIfPresent(signalWeights, ClothConfigHelper.buildFloat(entryBuilder, MariesLibConfigKeys.MULT_RECIPE_INHERITANCE,
                "scanner.multipliers.recipeInheritance", h.multRecipeInheritance, 1f, 0f, 100f, v -> h.multRecipeInheritance = v));
        addIfPresent(signalWeights, ClothConfigHelper.buildFloat(entryBuilder, MariesLibConfigKeys.MULT_NAMESPACE_PEER,
                "scanner.multipliers.namespacePeer", h.multNamespacePeer, 0.5f, 0f, 100f, v -> h.multNamespacePeer = v));
        addIfPresent(signalWeights, ClothConfigHelper.buildFloat(entryBuilder, MariesLibConfigKeys.MULT_SECONDARY_SUFFIX,
                "scanner.multipliers.secondarySuffix", h.multSecondarySuffix, 0.5f, 0f, 100f, v -> h.multSecondarySuffix = v));
        addIfPresent(signalWeights, ClothConfigHelper.buildFloat(entryBuilder, MariesLibConfigKeys.MULT_NAMESPACE_PEER_AVG,
                "scanner.multipliers.namespacePeerAverageWeight", h.multNamespacePeerAverageWeight, 0.5f, 0f, 100f,
                v -> h.multNamespacePeerAverageWeight = v));
        cat.addEntry(signalWeights.build());

        cat.addEntry(entryBuilder.startTextDescription(ClothConfigHelper.t("scanner.toolsHint")).build());
        cat.addEntry(new ImportExportButtonsWidget(parent));
    }

    private static void addIfPresent(SubCategoryBuilder builder, AbstractConfigListEntry<?> entry) {
        if (entry != null) {
            builder.add(entry);
        }
    }
}
