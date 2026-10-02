package dev.marie.framework.command;


import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.core.MarieContext;
import dev.marie.framework.core.MarieCore;
import dev.marie.framework.runtime.RuntimeResolver;
import dev.marie.framework.runtime.SourceCollector;
import dev.marie.framework.scanner.ClassificationResult;
import dev.marie.framework.scanner.ItemScanner;
import dev.marie.framework.scanner.analysis.MultiValueAnalysisPipeline;
import dev.marie.framework.tracking.TrackingAttachment;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@ApiStatus.Internal
final class MarieScannerCommands {

    private MarieScannerCommands() {}

    static int invalidateCache(CommandContext<CommandSourceStack> ctx, String modId) {
        CommandSourceStack source = ctx.getSource();
        ItemScanner.invalidateCache();
        RuntimeResolver.getInstance().invalidateCache();
        if (MarieContext.isRegistered()) {
            MarieContext.forMod(modId).cacheInvalidatedHook().run();
        }
        ItemScanner.scanAndApply(source.getServer().getRecipeManager());
        source.sendSuccess(() -> Component.literal(
                "[" + modId + "] cache invalidated. Scanner will reapply on next tick."), false);
        return 1;
    }

    static int showNbtPaths(CommandContext<CommandSourceStack> ctx, String modId) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String header = "[" + modId + "] NBT paths:";
        player.sendSystemMessage(MarieCommandSupport.copyableGreenLine(header));

        for (String key : MarieCommandSupport.registeredValueKeys()) {
            String path = TrackingAttachment.getValueNbtPath(key);
            player.sendSystemMessage(MarieCommandSupport.copyableGreenLine(path));
        }

        player.sendSystemMessage(MarieCommandSupport.copyableGreenLine(TrackingAttachment.getTotalNbtPath()));
        return 1;
    }

    static int showUnassignedSources(CommandContext<CommandSourceStack> ctx, String modId) throws CommandSyntaxException {
        if (!MarieCommandSupport.ensureConsumerRegistered(ctx.getSource())) {
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemScanner.scanAndApplyNow(ctx.getSource().getServer().getRecipeManager());
        Map<String, List<ResourceLocation>> byNamespace = new TreeMap<>();

        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (!MarieContext.isSourceItemAllowed(stack)) {
                continue;
            }
            ResourceLocation itemId = item.builtInRegistryHolder().key().location();
            if (MarieCommandSupport.isClassified(itemId, item.builtInRegistryHolder())) {
                continue;
            }
            byNamespace.computeIfAbsent(itemId.getNamespace(), k -> new ArrayList<>()).add(itemId);
        }

        if (byNamespace.isEmpty()) {
            player.sendSystemMessage(Component.literal("[" + modId + "] All loaded sources are classified.")
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        }

        int totalCount = byNamespace.values().stream().mapToInt(List::size).sum();
        Path outputPath = FMLPaths.CONFIGDIR.get().resolve(modId).resolve("unassigned_sources.txt");
        try {
            Files.createDirectories(outputPath.getParent());
            List<String> lines = new ArrayList<>();
            lines.add("[" + modId + "] Total unassigned sources: " + totalCount);
            lines.add("");
            for (Map.Entry<String, List<ResourceLocation>> entry : byNamespace.entrySet()) {
                List<ResourceLocation> ids = entry.getValue();
                ids.sort(Comparator.comparing(ResourceLocation::toString));
                lines.add("[" + entry.getKey() + "]");
                for (ResourceLocation id : ids) {
                    lines.add(id.toString());
                }
                lines.add("");
            }
            Files.write(outputPath, lines, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            MarieCore.LOGGER.error("[MarieLib] Failed to write unassigned sources file", ex);
            player.sendSystemMessage(Component.literal("[" + modId + "] Failed to write unassigned sources file.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        player.sendSystemMessage(Component.literal("[" + modId + "] Written " + totalCount
                        + " unassigned sources to config/" + modId + "/unassigned_sources.txt.")
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    static int runScanAnalysis(CommandContext<CommandSourceStack> ctx, String modId) {
        RecipeManager recipeManager = ctx.getSource().getServer().getRecipeManager();
        List<ClassificationResult> classified = SourceCollector.collectAllClassifiedSources(recipeManager);
        MultiValueAnalysisPipeline.runFullRegistry(classified, 0.15f, 0.35f, 0.10f);
        String outputPath = "config/" + modId + "/scanner_analysis/";
        ctx.getSource().sendSuccess(
                () -> Component.literal("Multi-value analysis written to " + outputPath)
                        .withStyle(ChatFormatting.GREEN),
                true);
        return 1;
    }
}
