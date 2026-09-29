package dev.marie.framework.compat;

import dev.marie.framework.api.ApiStatus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.marie.framework.core.MarieCore;
import dev.marie.framework.core.MarieContext;
import dev.marie.framework.util.MarieValidation;
import dev.marie.framework.util.MarieRegistryUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Auto-detects loaded source mods that have not been registered in the compat registry.
 */
@ApiStatus.Internal
public final class AutoCompatDiscovery {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> VANILLA_MOD_IDS = Set.of("minecraft", "neoforge", "java", "forge");
    private static final float DEFAULT_HINT_AMOUNT = 0.1f;

    private AutoCompatDiscovery() {}

    public static void discover() {
        MarieCore.LOGGER.info("[MarieLib] AutoCompatDiscovery.discover() called — evaluating {} mods", ModList.get().getMods().size());
        try {
            Path autoCompatDir = FMLPaths.CONFIGDIR.get().resolve(MarieContext.get().modId() + "/auto_compat");
            Files.createDirectories(autoCompatDir);
            Set<String> registeredModIds = new HashSet<>(ModCompat.getDetected().keySet());

            for (var modInfo : ModList.get().getMods()) {
                String modId = modInfo.getModId();
                MarieCore.LOGGER.info("[MarieLib] AutoCompat checking: {}", modId);
                if (VANILLA_MOD_IDS.contains(modId)) {
                    MarieCore.LOGGER.info("[MarieLib] AutoCompat skipping {} — reason: {}", modId, "vanilla/system mod");
                    continue;
                }
                if (registeredModIds.contains(modId)) {
                    MarieCore.LOGGER.info("[MarieLib] AutoCompat skipping {} — reason: {}", modId, "already registered");
                    continue;
                }

                Map<ResourceLocation, String> hintedMappings = new LinkedHashMap<>();
                List<Item> sourceItems = collectSourceItems(modId, hintedMappings);
                MarieCore.LOGGER.info("[MarieLib] AutoCompat source item count for {}: {}", modId, sourceItems.size());
                if (sourceItems.isEmpty()) {
                    MarieCore.LOGGER.info("[MarieLib] AutoCompat skipping {} — reason: {}", modId, "no source items found");
                    continue;
                }

                CompatDefinition definition = CompatDefinition.builder(modId)
                        .category(CompatDefinition.CompatCategory.SOURCE_MOD)
                        .addAllSourceMappings(Map.of())
                        .build();
                ModCompat.registerExternal(definition);
                registeredModIds.add(modId);

                MarieCore.LOGGER.info(
                        "[MarieLib] Auto-detected source mod: {} ({} source items found)",
                        modId,
                        sourceItems.size()
                );

                writeStubIfMissing(autoCompatDir, modId, sourceItems.size(), hintedMappings);
            }
        } catch (Exception e) {
            MarieCore.LOGGER.warn("[MarieLib] Auto compat discovery failed: {}", e.getMessage());
        }
    }

    private static List<Item> collectSourceItems(String modId, Map<ResourceLocation, String> hintedMappings) {
        List<Item> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation itemId = MarieRegistryUtils.itemKey(item);
            if (itemId == null || !modId.equals(itemId.getNamespace())) {
                continue;
            }

            ItemStack stack = new ItemStack(item);
            if (!MarieContext.isSourceItemAllowed(stack)) {
                continue;
            }

            items.add(item);
            String valueKey = resolveMatchedValueKey(stack);
            if (valueKey != null) {
                hintedMappings.put(itemId, valueKey);
            }
        }
        return items;
    }

    private static String resolveMatchedValueKey(ItemStack stack) {
        return null;
    }

    private static void writeStubIfMissing(
            Path autoCompatDir,
            String modId,
            int sourceItemCount,
            Map<ResourceLocation, String> hintedMappings
    ) {
        if (!MarieValidation.sanitizeModId(modId)) {
            MarieCore.LOGGER.warn("[MarieLib] AutoCompat: invalid modId '{}' — skipping stub write", modId);
            return;
        }
        Path stubPath = autoCompatDir.resolve(modId + ".json");
        if (Files.exists(stubPath)) {
            return;
        }

        JsonObject root = new JsonObject();
        root.addProperty(MarieContext.get().modId() + "_schema_version", 1);
        root.addProperty("mod_id", modId);
        root.addProperty("category", "SOURCE_MOD");
        root.addProperty("auto_detected", true);
        root.addProperty("source_item_count", sourceItemCount);

        JsonObject mappings = new JsonObject();
        for (Map.Entry<ResourceLocation, String> entry : hintedMappings.entrySet()) {
            JsonObject mapping = new JsonObject();
            mapping.addProperty("value", entry.getValue());
            mapping.addProperty("amount", DEFAULT_HINT_AMOUNT);
            mappings.add(entry.getKey().toString(), mapping);
        }
        root.add("mappings", mappings);

        try (Writer writer = Files.newBufferedWriter(stubPath)) {
            GSON.toJson(root, writer);
            MarieCore.LOGGER.info("[MarieLib] Wrote auto-compat stub: {}", stubPath);
        } catch (IOException e) {
            MarieCore.LOGGER.warn("[MarieLib] Failed to write auto-compat stub {}: {}", stubPath, e.getMessage());
        }
    }
}
