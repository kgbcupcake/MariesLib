package dev.marie.framework.tooltips;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.core.MarieCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player/modpack-dev-editable tooltip message overrides, keyed explicitly by {@code modId}.
 * <p>
 * Unlike other registries in this framework, this class never reads the global
 * {@code MarieContext} singleton — every lookup takes {@code modId} as an explicit parameter,
 * so it works for any consumer mod regardless of whether that mod ever registers a
 * {@code MarieContext}.
 * <p>
 * <b>Priority / Override Stack (highest to lowest):</b>
 * <ol>
 *   <li>Runtime override (set via {@link #registerExternal}, e.g. by a KubeJS script;
 *       survives config/datapack reloads)</li>
 *   <li>{@code data/<modId>/marie/tooltips/tooltip_messages.json} (datapack override)</li>
 *   <li>{@code config/<modId>/tooltips/tooltip_messages.json} (modpack creator override)</li>
 *   <li>No override (caller falls back to its own default)</li>
 * </ol>
 * Both tiers share the same JSON shape:
 * <pre>{@code
 * {
 *   "byKey": { "<key>": "<message>", ... },
 *   "byItem": { "<itemId>": "<message>", ... }
 * }
 * }</pre>
 * {@code byItem} entries take priority over {@code byKey} for {@link #getForItem}, per tier.
 */
public final class TooltipMessageRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "tooltip_messages.json";
    private static final String DATAPACK_RELATIVE_PATH = "marie/tooltips/tooltip_messages.json";

    private static final Map<String, OverrideTiers> CONFIG_CACHE = new HashMap<>();
    private static final Map<String, OverrideTiers> DATAPACK_CACHE = new HashMap<>();
    private static final Map<String, Map<String, String>> EXTERNALLY_REGISTERED = new ConcurrentHashMap<>();

    private TooltipMessageRegistry() {}

    @ApiStatus.Experimental
    public static Optional<String> get(String modId, String key) {
        Optional<String> fromExternal = lookupNonBlank(EXTERNALLY_REGISTERED.get(modId), key);
        if (fromExternal.isPresent()) {
            return fromExternal;
        }
        Optional<String> fromDatapack = lookupNonBlank(DATAPACK_CACHE.get(modId), key);
        if (fromDatapack.isPresent()) {
            return fromDatapack;
        }
        OverrideTiers config = CONFIG_CACHE.computeIfAbsent(modId, TooltipMessageRegistry::loadConfigTier);
        return lookupNonBlank(config, key);
    }

    @ApiStatus.Experimental
    public static Optional<String> getForItem(String modId, String itemId, String key) {
        Optional<String> fromExternalItem = lookupNonBlank(EXTERNALLY_REGISTERED.get(modId), itemId);
        if (fromExternalItem.isPresent()) {
            return fromExternalItem;
        }
        Optional<String> fromDatapackItem = lookupItemNonBlank(DATAPACK_CACHE.get(modId), itemId);
        if (fromDatapackItem.isPresent()) {
            return fromDatapackItem;
        }
        OverrideTiers config = CONFIG_CACHE.computeIfAbsent(modId, TooltipMessageRegistry::loadConfigTier);
        Optional<String> fromConfigItem = lookupItemNonBlank(config, itemId);
        if (fromConfigItem.isPresent()) {
            return fromConfigItem;
        }
        return get(modId, key);
    }

    /**
     * Sets a runtime tooltip message override for {@code key} under {@code modId}, e.g. from a
     * KubeJS script. Takes priority over both the datapack and config tiers, and survives
     * {@link #reload} and {@link #loadFromDatapack} calls. Overwrites any existing runtime value
     * for the same {@code modId}/{@code key} rather than throwing.
     */
    @ApiStatus.Experimental
    public static void registerExternal(String modId, String key, String message) {
        EXTERNALLY_REGISTERED.computeIfAbsent(modId, m -> new ConcurrentHashMap<>()).put(key, message);
    }

    /** Clears a single runtime override previously set via {@link #registerExternal}. */
    @ApiStatus.Experimental
    public static void removeExternal(String modId, String key) {
        Map<String, String> messages = EXTERNALLY_REGISTERED.get(modId);
        if (messages != null) {
            messages.remove(key);
        }
    }

    /**
     * Writes {@code config/<modId>/tooltips/tooltip_messages.json} seeded with {@code byKeyDefaults}
     * if that file does not already exist. Never overwrites an existing file. Not called automatically
     * by this class — consumer mods must invoke this explicitly at their own startup with their own
     * default values, since this class has no domain knowledge of what those defaults should be.
     */
    public static void seedDefaultsIfAbsent(String modId, Map<String, String> byKeyDefaults) {
        Path configDir = FMLPaths.CONFIGDIR.get().resolve(modId).resolve("tooltips");
        Path file = configDir.resolve(CONFIG_FILE_NAME);
        if (Files.exists(file)) {
            return;
        }
        try {
            Files.createDirectories(configDir);
            OverrideTiers seeded = new OverrideTiers(new HashMap<>(byKeyDefaults), new HashMap<>());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(seeded, writer);
            }
        } catch (IOException e) {
            MarieCore.LOGGER.warn("[TooltipMessageRegistry] Failed to seed tooltip_messages.json for {}", modId, e);
        }
    }

    public static void reload(String modId) {
        CONFIG_CACHE.remove(modId);
    }

    public static void loadFromDatapack(String modId, ResourceManager resourceManager) {
        ResourceLocation path = ResourceLocation.fromNamespaceAndPath(modId, DATAPACK_RELATIVE_PATH);
        Optional<Resource> resource = resourceManager.getResource(path);
        if (resource.isEmpty()) {
            DATAPACK_CACHE.remove(modId);
            return;
        }
        try (InputStream is = resource.get().open();
             Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            OverrideTiers tiers = GSON.fromJson(reader, OverrideTiers.class);
            if (tiers != null) {
                DATAPACK_CACHE.put(modId, tiers.normalized());
            } else {
                DATAPACK_CACHE.remove(modId);
            }
        } catch (IOException e) {
            DATAPACK_CACHE.remove(modId);
        }
    }

    private static Optional<String> lookupNonBlank(OverrideTiers tiers, String key) {
        return lookupNonBlank(tiers == null ? null : tiers.byKey(), key);
    }

    private static Optional<String> lookupNonBlank(Map<String, String> messages, String key) {
        if (messages == null) {
            return Optional.empty();
        }
        String value = messages.get(key);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    private static Optional<String> lookupItemNonBlank(OverrideTiers tiers, String itemId) {
        if (tiers == null) {
            return Optional.empty();
        }
        return lookupNonBlank(tiers.byItem(), itemId);
    }

    private static OverrideTiers loadConfigTier(String modId) {
        Path configDir = FMLPaths.CONFIGDIR.get().resolve(modId).resolve("tooltips");
        Path file = configDir.resolve(CONFIG_FILE_NAME);
        OverrideTiers result;
        try {
            Files.createDirectories(configDir);
            if (!Files.exists(file)) {
                writeEmpty(file);
            }
            result = parse(file);
        } catch (IOException e) {
            result = OverrideTiers.empty();
        }

        try {
            writeReadmeIfAbsent(configDir, modId);
        } catch (IOException e) {
            MarieCore.LOGGER.warn("[TooltipMessageRegistry] Failed to write TOOLTIP_MESSAGES_README.md", e);
        }

        return result;
    }

    private static void writeReadmeIfAbsent(Path configDir, String modId) throws IOException {
        Path readme = configDir.resolve("TOOLTIP_MESSAGES_README.md");
        if (Files.exists(readme)) {
            return;
        }
        String resourcePath = "/data/" + modId + "/config/TOOLTIP_MESSAGES_README.md";
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(resourcePath.substring(1))) {
            if (in == null) {
                MarieCore.LOGGER.warn("[TooltipMessageRegistry] No bundled TOOLTIP_MESSAGES_README.md for this modId, skipping write. Tried resource path: {}", resourcePath);
                return;
            }
            Files.copy(in, readme);
        }
    }

    private static OverrideTiers parse(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            OverrideTiers tiers = GSON.fromJson(reader, OverrideTiers.class);
            return tiers != null ? tiers.normalized() : OverrideTiers.empty();
        }
    }

    private static void writeEmpty(Path file) throws IOException {
        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(OverrideTiers.empty(), writer);
        }
    }

    private record OverrideTiers(Map<String, String> byKey, Map<String, String> byItem) {
        static OverrideTiers empty() {
            return new OverrideTiers(Map.of(), Map.of());
        }

        OverrideTiers normalized() {
            return new OverrideTiers(
                    byKey != null ? byKey : Map.of(),
                    byItem != null ? byItem : Map.of());
        }
    }
}
