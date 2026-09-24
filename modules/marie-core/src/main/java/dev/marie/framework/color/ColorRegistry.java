package dev.marie.framework.color;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.marie.framework.core.IMarieConfig;
import dev.marie.framework.core.MarieCore;
import dev.marie.framework.data.DatapackSchema;
import dev.marie.framework.registry.AbstractRegistry;
import dev.marie.framework.util.MarieResourceLoader;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Per-value HUD/UI colors from {@code config/<modid>/colors.json}, with optional datapack override
 * {@code <modid>:config/colors.json} ({@code data/<modid>/config/colors.json}).
 * <p>File format (JSON array):
 * <pre>{@code
 * [
 *   {"key": "value_a", "argb": "0xFF55FF55"},
 *   {"key": "value_b", "argb": "0xFF4DD9D9"}
 * ]
 * }</pre>
 * Call {@link #load()} after effect registry initialization.
 */
public final class ColorRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final class Core extends AbstractRegistry<String, Integer> {
        Core() {
            super("ColorRegistry");
        }
    }

    private static final Core INSTANCE = new Core();

    private ColorRegistry() {}

    public static Optional<Integer> getArgb(String key) {
        Integer v = INSTANCE.get(key);
        return v == null ? Optional.empty() : Optional.of(v);
    }

    /** Puts or replaces a color (alpha forced to 0xFF). */
    public static void setArgb(String key, int argb) {
        Objects.requireNonNull(key, "key");
        int opaque = (argb & 0x00_FF_FF_FF) | 0xFF00_0000;
        LinkedHashMap<String, Integer> next = new LinkedHashMap<>(INSTANCE.entries());
        next.put(key, opaque);
        INSTANCE.reset();
        for (Map.Entry<String, Integer> e : next.entrySet()) {
            INSTANCE.register(e.getKey(), e.getValue());
        }
        INSTANCE.freeze();
    }

    /** Removes a custom color so UI falls back to the default palette. */
    public static void remove(String key) {
        Objects.requireNonNull(key, "key");
        LinkedHashMap<String, Integer> next = new LinkedHashMap<>(INSTANCE.entries());
        next.remove(key);
        INSTANCE.reset();
        for (Map.Entry<String, Integer> e : next.entrySet()) {
            INSTANCE.register(e.getKey(), e.getValue());
        }
        INSTANCE.freeze();
    }

    public static void load() {
        Path configDir = FMLPaths.CONFIGDIR.get().resolve(IMarieConfig.get().modId());
        Path file = configDir.resolve("colors.json");
        try {
            Files.createDirectories(configDir);
            if (!Files.exists(file)) {
                writeEmpty(file);
                MarieCore.LOGGER.info("[ColorRegistry] Wrote default colors.json");
            }
            parseFile(file);
            MarieCore.LOGGER.info("[ColorRegistry] Loaded {} custom colors from {}", INSTANCE.size(), file);
        } catch (IOException e) {
            MarieCore.LOGGER.error("[ColorRegistry] Failed to load colors.json", e);
            INSTANCE.reset();
            INSTANCE.freeze();
        }

        try {
            writeReadmeIfAbsent(configDir.resolve("Read_Me"));
        } catch (IOException e) {
            MarieCore.LOGGER.warn("[ColorRegistry] Failed to write COLORS_README.md", e);
        }
    }

    private static void writeReadmeIfAbsent(Path readmeDir) throws IOException {
        Path readme = readmeDir.resolve("COLORS_README.md");
        if (Files.exists(readme)) {
            return;
        }
        Files.createDirectories(readmeDir);
        String resourcePath = "/data/" + IMarieConfig.get().modId() + "/config/COLORS_README.md";
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(resourcePath.substring(1))) {
            if (in == null) {
                MarieCore.LOGGER.warn("[ColorRegistry] No bundled COLORS_README.md for this modId, skipping write. Tried resource path: {}", resourcePath);
                return;
            }
            Files.copy(in, readme);
        }
    }

    public static void reload() {
        MarieCore.LOGGER.info("[ColorRegistry] Reloading colors.json");
        load();
    }

    public static void save() {
        Path configDir = FMLPaths.CONFIGDIR.get().resolve(IMarieConfig.get().modId());
        Path file = configDir.resolve("colors.json");
        try {
            Files.createDirectories(configDir);
            JsonArray arr = new JsonArray();
            // Persist every key currently held, not just ones matching a registered value id —
            // ColorKey-based overrides (value/activity/panel/etc., keyed by full ResourceLocation
            // string) are never value ids, so filtering to ValueRegistry would silently drop them.
            for (Map.Entry<String, Integer> e : INSTANCE.entries().entrySet()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("key", e.getKey());
                obj.addProperty("argb", String.format("0x%08X", e.getValue()));
                arr.add(obj);
            }
            try (Writer w = Files.newBufferedWriter(file)) {
                GSON.toJson(arr, w);
            }
            MarieCore.LOGGER.info("[ColorRegistry] Saved {} entries to {}", arr.size(), file);
        } catch (IOException e) {
            MarieCore.LOGGER.error("[ColorRegistry] Failed to save colors.json", e);
        }
    }

    /**
     * Datapack path {@code <modid>:config/colors.json} ({@code data/<modid>/config/colors.json}).
     * If absent, falls back to {@link #load()}.
     */
    public static void loadFromDatapack(ResourceManager resourceManager) {
        MarieResourceLoader.loadFromModConfig(
                resourceManager,
                DatapackSchema.CONFIG_COLORS,
                ColorRegistry::parseFromReader,
                ColorRegistry::load,
                "[ColorRegistry] Loaded from datapack override",
                "[ColorRegistry] Failed to load datapack colors, falling back to config folder",
                "[ColorRegistry] Loaded from config folder"
        );
    }

    private static void writeEmpty(Path file) throws IOException {
        try (Writer w = Files.newBufferedWriter(file)) {
            GSON.toJson(new JsonArray(), w);
        }
    }

    private static void parseFile(Path file) throws IOException {
        try (Reader r = Files.newBufferedReader(file)) {
            parseFromReader(r);
        }
    }

    private static void parseFromReader(Reader reader) {
        INSTANCE.reset();
        JsonArray arr = GSON.fromJson(reader, JsonArray.class);
        if (arr == null || arr.isEmpty()) {
            INSTANCE.freeze();
            return;
        }
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject obj = el.getAsJsonObject();
            if (!obj.has("key")) {
                continue;
            }
            String key = obj.get("key").getAsString();
            int argb = parseArgbElement(obj.get("argb"));
            int opaque = (argb & 0x00_FF_FF_FF) | 0xFF00_0000;
            INSTANCE.register(key, opaque);
        }
        INSTANCE.freeze();
    }

    private static int parseArgbElement(JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return 0xFF_FF_FF_FF;
        }
        if (el.isJsonPrimitive()) {
            if (el.getAsJsonPrimitive().isNumber()) {
                int n = el.getAsInt();
                return n | 0xFF00_0000;
            }
            return parseArgbString(el.getAsString());
        }
        return 0xFF_FF_FF_FF;
    }

    private static int parseArgbString(String raw) {
        if (raw == null) {
            return 0xFF_FF_FF_FF;
        }
        String s = raw.trim();
        try {
            if (s.startsWith("0x") || s.startsWith("0X")) {
                String hex = s.substring(2);
                return (int) (Long.parseLong(hex, 16) & 0xFF_FF_FF_FF);
            }
            if (s.startsWith("#")) {
                s = s.substring(1);
            }
            if (s.length() == 6) {
                return (int) (Long.parseLong(s, 16) & 0xFF_FF_FF) | 0xFF00_0000;
            }
            if (s.length() == 8) {
                return (int) (Long.parseLong(s, 16) & 0xFF_FF_FF_FF);
            }
        } catch (NumberFormatException ignored) {
        }
        return 0xFF_FF_FF_FF;
    }

}
