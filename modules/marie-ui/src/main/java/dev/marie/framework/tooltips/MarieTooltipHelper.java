package dev.marie.framework.tooltips;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.api.source.SourcePairSynergy;
import dev.marie.framework.api.progression.MilestoneDefinition;
import dev.marie.framework.api.registry.MilestoneRegistry;
import dev.marie.framework.api.registry.SynergyRegistry;
import dev.marie.framework.client.config.render.MarieValueColors;
import dev.marie.framework.config.FeatureFlagCache;
import dev.marie.framework.core.IMarieConfig;
import dev.marie.framework.core.MarieContext;
import dev.marie.framework.core.MarieModRegistry;
import dev.marie.framework.scanner.ExcludedItemsRegistry;
import dev.marie.framework.scanner.ScannerSpecRegistry;
import dev.marie.framework.tracking.TrackingData;
import dev.marie.framework.util.MarieRegistryUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * Shared source tooltip formatter used by JEI/REI/EMI integrations.
 */
@ApiStatus.Internal
public final class MarieTooltipHelper {

    private MarieTooltipHelper() {}

    public static List<Component> getTooltipLines(ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        if (stack == null || stack.isEmpty()) {
            return lines;
        }
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return lines;
        }
        if (!MarieContext.isRegistered()) {
            return lines;
        }

        Minecraft mc = Minecraft.getInstance();
        if (!sourceTooltipsEnabled()) {
            return lines;
        }

        Player player = mc.player;
        // Every attached mod may classify this item for its own values, so every mod's resolver
        // runs and the results merge — otherwise only the last-attached mod's items get tooltips.
        Map<String, Float> valueBars = new java.util.LinkedHashMap<>();
        for (MarieContext modCtx : MarieModRegistry.getAll()) {
            valueBars.putAll(modCtx.tooltipValueResolver().apply(stack, player));
        }

        String itemId = MarieRegistryUtils.itemKey(stack).toString();
        String dominantCategory = valueBars.isEmpty()
                ? null
                : valueBars.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(null);
        // Header/translation keys are mod-specific; use the mod that owns the dominant value (or the
        // primary mod when nothing resolved) rather than whichever mod last attached.
        MarieContext headerCtx = dominantCategory != null
                ? MarieContext.forValue(dominantCategory)
                : MarieContext.get();
        // Gate on the mod that would actually render this tooltip, not a global OR across every
        // attached mod — MarieContext.isSourceItemAllowed() ORs every attached mod's filter, so any
        // other mod using MarieBootstrap.attach()'s unconfigured default filter (which accepts every
        // item) silently makes every mod's tooltip, including this one, show on every item in the
        // game. Whether headerCtx itself considers this item a source is the only relevant signal.
        if (!headerCtx.sourceItemFilter().test(stack) && valueBars.isEmpty()) {
            return lines;
        }
        String modId = headerCtx.modId();
        String familyKey = null;
        for (MarieContext modCtx : MarieModRegistry.getAll()) {
            familyKey = modCtx.sourceFamilyResolver().apply(MarieRegistryUtils.itemKey(stack));
            if (familyKey != null) {
                break;
            }
        }
        TrackingData tracking = headerCtx.clientTrackingDataProvider().get();
        long gameTimeMs = tracking.lastTickTime > 0 ? tracking.lastTickTime : 0L;
        float multiplier = player != null ? tracking.peekMultiplier(itemId, dominantCategory, familyKey, gameTimeMs) : 1.0f;

        lines.add(Component.literal("✦ " + modId).withStyle(ChatFormatting.GOLD));

        if (valueBars.isEmpty()) {
            boolean excluded = ExcludedItemsRegistry.isExcluded(itemId)
                    || ScannerSpecRegistry.get().excludedItems().contains(itemId);
            if (excluded) {
                Optional<String> override = TooltipMessageRegistry.getForItem(modId, itemId, "excluded");
                Component excludedLine = override.isPresent()
                        ? Component.literal(override.get())
                        : Component.translatable(modId + ".tooltip.excluded");
                int excludedColor = TooltipColorRegistry.getForItem(modId, itemId, "excluded")
                        .orElse(ChatFormatting.DARK_GRAY.getColor());
                lines.add(excludedLine.copy().withStyle(Style.EMPTY.withColor(excludedColor)));
                return lines;
            }
            lines.add(Component.translatable(modId + ".tooltip.unclassified").withStyle(ChatFormatting.GRAY));
        }

        if (multiplier < 1.0f && player != null) {
            String pct = (int) (multiplier * 100) + "%";
            lines.add(Component.translatable(modId + ".tooltip.diminished", pct).withStyle(ChatFormatting.YELLOW));
        } else if (player != null) {
            lines.add(Component.translatable(modId + ".tooltip.fresh").withStyle(ChatFormatting.GREEN));
        }

        final float minLine = 0.02f;
        String fmt = multiplier < 1.0f ? "%.2f" : "%.1f";
        String highestKey = null;
        float highestValue = Float.NEGATIVE_INFINITY;
        if (!valueBars.isEmpty()) {
            for (String key : MarieContext.get().valueKeys()) {
                float v = valueBars.getOrDefault(key, 0f);
                if (v > highestValue) {
                    highestValue = v;
                    highestKey = key;
                }
            }
        }

        boolean renderedAny = false;
        for (String key : MarieContext.get().valueKeys()) {
            float base = valueBars.getOrDefault(key, 0f);
            if (base < minLine) {
                continue;
            }
            float display = base * multiplier;
            renderedAny = true;
            String label = MarieRegistryUtils.capitalizeFirst(key);
            String gain = String.format(Locale.ROOT, fmt, display);
            int color = computeTooltipColor(modId, itemId, key, tracking, display);
            MutableComponent line = Component.literal("  " + label + "  +" + gain)
                    .withStyle(Style.EMPTY.withColor(color));
            lines.add(line);
        }

        if (!renderedAny && highestKey != null && highestValue > 0f) {
            float base = Math.max(0f, valueBars.getOrDefault(highestKey, 0f));
            float display = base * multiplier;
            String label = MarieRegistryUtils.capitalizeFirst(highestKey);
            String gain = String.format(Locale.ROOT, fmt, display);
            int color = computeTooltipColor(modId, itemId, highestKey, tracking, display);
            lines.add(Component.literal("  " + label + "  +" + gain).withStyle(Style.EMPTY.withColor(color)));
        }

        if (debugLoggingEnabled() && player != null) {
            var breakdown = tracking.getMultiplierBreakdown(itemId, dominantCategory, familyKey, gameTimeMs);
            float fin = breakdown.finalMultiplier();
            lines.add(Component.empty());
            lines.add(Component.literal(
                    "  → " + (int) (fin * 100) + "% value gain (memory blend)")
                    .withStyle(fin < 1.0f ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        } else if (IMarieConfig.get().debugMemoryLogging() && player != null) {
            var breakdown = tracking.getMultiplierBreakdown(itemId, dominantCategory, familyKey, gameTimeMs);
            lines.add(Component.empty());
            lines.add(Component.literal("  → " + (int) (breakdown.finalMultiplier() * 100) + "% value gain")
                    .withStyle(breakdown.finalMultiplier() < 1.0f ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        }

        ResourceLocation thisItem = MarieRegistryUtils.itemKey(stack);
        if (synergiesEnabled()) {
            addSourceSynergyLine(lines, thisItem);
        }
        if (milestonesEnabled() && !valueBars.isEmpty()) {
            addMilestoneLine(lines, valueBars.keySet());
        }
        return lines;
    }

    private static void addSourceSynergyLine(List<Component> lines, ResourceLocation sourceId) {
        List<SourcePairSynergy> sourceSynergies = SynergyRegistry.getSourcePairSynergies();
        if (!synergiesEnabled() || sourceSynergies.isEmpty()) {
            return;
        }
        for (SourcePairSynergy def : sourceSynergies) {
            ResourceLocation partner = null;
            if (sourceId.equals(def.getSourceA())) {
                partner = def.getSourceB();
            } else if (sourceId.equals(def.getSourceB())) {
                partner = def.getSourceA();
            }
            if (partner == null) {
                continue;
            }

            Item partnerItem = BuiltInRegistries.ITEM.get(partner);
            Component partnerName = new ItemStack(partnerItem).getHoverName();
            lines.add(Component.literal("✦ Pairs with: ").withStyle(ChatFormatting.AQUA).append(partnerName.copy().withStyle(ChatFormatting.AQUA)));
            return;
        }
    }

    private static void addMilestoneLine(List<Component> lines, Set<String> valuesInTooltip) {
        if (!milestonesEnabled() || MilestoneRegistry.getAll().isEmpty()) {
            return;
        }
        Set<String> valueSet = new LinkedHashSet<>(valuesInTooltip);
        for (String valueKey : valueSet) {
            List<MilestoneDefinition> milestones = MilestoneRegistry.getForValue(valueKey);
            if (!milestones.isEmpty()) {
                MilestoneDefinition milestone = milestones.getFirst();
                String name = milestone.getId().replace('_', ' ');
                lines.add(Component.literal("✦ Counts toward: " + name).withStyle(ChatFormatting.YELLOW));
                return;
            }
        }
    }

    private static final int COL_CRITICAL = 0xFFFF5555;
    private static final int COL_WARNING = 0xFFFFAA00;
    private static final int COL_GOOD = 0xFF55FF55;

    private static int computeTooltipColor(String modId, String itemId, String key, TrackingData tracking, float gain) {
        Optional<Integer> override = TooltipColorRegistry.getForItem(modId, itemId, key);
        if (override.isPresent()) {
            return override.get();
        }
        boolean beneficial = MarieContext.isValueBeneficial(key);
        float current = tracking.values.getOrDefault(key, 0f);
        float projected = current + gain;

        if (beneficial) {
            return MarieValueColors.baseColorArgb(key);
        }
        MarieContext keyCtx = MarieContext.forValue(key);
        var def = keyCtx.valueDefinitionFor(key);
        float excess = def != null ? def.getExcessThreshold() : keyCtx.excessThreshold();
        float low = def != null ? def.getLowThreshold() : keyCtx.lowThreshold();
        if (projected > excess) {
            return COL_CRITICAL;
        } else if (projected > low) {
            return COL_WARNING;
        }
        return COL_GOOD;
    }

    private static boolean sourceTooltipsEnabled() {
        return !FeatureFlagCache.isInitialized() || FeatureFlagCache.enableSourceTooltips();
    }

    private static boolean debugLoggingEnabled() {
        return FeatureFlagCache.isInitialized() && FeatureFlagCache.enableDebugLogging();
    }

    private static boolean synergiesEnabled() {
        return !FeatureFlagCache.isInitialized() || FeatureFlagCache.enableSynergies();
    }

    private static boolean milestonesEnabled() {
        return !FeatureFlagCache.isInitialized() || FeatureFlagCache.enableMilestones();
    }

}
