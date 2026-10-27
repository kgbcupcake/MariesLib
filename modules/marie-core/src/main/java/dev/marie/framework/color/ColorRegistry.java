package dev.marie.framework.color;

import dev.marie.framework.api.ApiStatus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.marie.framework.core.IMarieConfig;
import dev.marie.framework.core.MarieContext;
import dev.marie.framework.core.MarieCore;
import dev.marie.framework.core.MarieModRegistry;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
 *
 * <p>With several mods attached, every mod's {@code colors.json} is read into one shared map, and
 * {@link #save()} writes each color back to its own mod's file: a {@code namespace:path} key goes to
 * the attached mod named by its namespace, any other key to the file it was read from (or the
 * current mod for a new one). Saving never moves one mod's colors into another mod's directory.
 */
@ApiStatus.Internal
public final class ColorRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final class Core extends AbstractRegistry<String, Integer> {
        Core() {
            super("ColorRegistry");
        }
    }

    private static final Core INSTANCE = new Core();

    /** The attached mod whose {@code colors.json} each key was read from or first set under; see {@link #ownerOf}. */
    private static final Map<String, String> OWNERS = new HashMap<>();

    /**
     * Mods whose colors were set or removed since the last load/save. {@link #save()} rewrites only
     * these files, so editing one mod's color never rewrites another mod's file (which could bake that
     * mod's datapack-override colors into its config for good).
     */
    private static final Set<String> DIRTY = new HashSet<>();

    /**
     * Per mod, entries of its own {@code colors.json} whose plain key (e.g. {@code accent}) a later mod's
     * file also defines. The shared map keeps only the later value, so these are written back when the
     * earlier mod's file is rewritten instead of being dropped from it.
     */
    private static final Map<String, Map<String, Integer>> SHADOWED = new HashMap<>();

    /** What one load pass reads across every mod's file before {@link #apply} swaps it in. */
    private static final class LoadPass {
        final Map<String, Integer> merged = new LinkedHashMap<>();
        final Map<String, String> owners = new HashMap<>();
        final Map<String, Map<String, Integer>> shadowed = new HashMap<>();
    }

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
        markDirty(key);
        OWNERS.putIfAbsent(key, ownerOf(key));
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
        markDirty(key);
        OWNERS.remove(key);
        forgetShadowed(key);
        INSTANCE.reset();
        for (Map.Entry<String, Integer> e : next.entrySet()) {
            INSTANCE.register(e.getKey(), e.getValue());
        }
        INSTANCE.freeze();
    }

    public static void load() {
        LoadPass pass = new LoadPass();
        for (String modId : modIds()) {
            runAsMod(modId, () -> loadModFile(modId, pass));
        }
        apply(pass);
    }

    private static void loadModFile(String modId, LoadPass pass) {
        Path configDir = configDir(modId);
        Path file = configDir.resolve("colors.json");
        try {
            Files.createDirectories(configDir);
            if (!Files.exists(file)) {
                writeEmpty(file);
                MarieCore.LOGGER.info("[ColorRegistry] Wrote default colors.json");
            }
            try (Reader r = Files.newBufferedReader(file)) {
                int added = mergeFrom(r, modId, pass);
                MarieCore.LOGGER.info("[ColorRegistry] Loaded {} custom colors from {}", added, file);
            }
        } catch (IOException e) {
            MarieCore.LOGGER.error("[ColorRegistry] Failed to load colors.json", e);
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
        Map<String, Map<String, Integer>> byMod = new LinkedHashMap<>();
        for (String modId : DIRTY) {
            byMod.put(modId, new LinkedHashMap<>());
        }
        // Persist every key currently held, not just ones matching a registered value id —
        // ColorKey-based overrides (value/activity/panel/etc., keyed by full ResourceLocation
        // string) are never value ids, so filtering to ValueRegistry would silently drop them.
        for (Map.Entry<String, Integer> e : INSTANCE.entries().entrySet()) {
            Map<String, Integer> file = byMod.get(ownerOf(e.getKey()));
            if (file != null) {
                file.put(e.getKey(), e.getValue());
            }
        }
        byMod.forEach((modId, entries) -> {
            SHADOWED.getOrDefault(modId, Map.of()).forEach(entries::putIfAbsent);
            // A file that failed to write stays dirty, so the next save retries it.
            if (writeModFile(modId, entries)) {
                DIRTY.remove(modId);
            }
        });
    }

    /** Marks the file {@code key} is saved to, and the file it was read from if that differs, as needing a rewrite. */
    private static void markDirty(String key) {
        DIRTY.add(ownerOf(key));
        String recorded = OWNERS.get(key);
        if (recorded != null) {
            DIRTY.add(recorded);
        }
    }

    /**
     * Drops every earlier mod's shadowed copy of {@code key} and marks those files dirty, so removing a
     * plain key shared by several mods really falls back to the default instead of the earlier copy
     * coming back on the next load.
     */
    private static void forgetShadowed(String key) {
        for (Map.Entry<String, Map<String, Integer>> e : SHADOWED.entrySet()) {
            if (e.getValue().remove(key) != null) {
                DIRTY.add(e.getKey());
            }
        }
    }

    private static boolean writeModFile(String modId, Map<String, Integer> entries) {
        Path configDir = configDir(modId);
        Path file = configDir.resolve("colors.json");
        try {
            Files.createDirectories(configDir);
            JsonArray arr = new JsonArray();
            for (Map.Entry<String, Integer> e : entries.entrySet()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("key", e.getKey());
                obj.addProperty("argb", String.format("0x%08X", e.getValue()));
                arr.add(obj);
            }
            try (Writer w = Files.newBufferedWriter(file)) {
                GSON.toJson(arr, w);
            }
            MarieCore.LOGGER.info("[ColorRegistry] Saved {} entries to {}", arr.size(), file);
            return true;
        } catch (IOException e) {
            MarieCore.LOGGER.error("[ColorRegistry] Failed to save colors.json", e);
            return false;
        }
    }

    /** The attached mod named by {@code key}'s namespace, else the file it came from, else the current mod. */
    private static String ownerOf(String key) {
        String namespace = attachedNamespace(key);
        if (namespace != null) {
            return namespace;
        }
        String recorded = OWNERS.get(key);
        return recorded != null ? recorded : IMarieConfig.get().modId();
    }

    /** {@code key}'s namespace when it names an attached mod, else {@code null}. */
    private static String attachedNamespace(String key) {
        int colon = key.indexOf(':');
        if (colon > 0) {
            String namespace = key.substring(0, colon);
            if (MarieModRegistry.isRegistered(namespace)) {
                return namespace;
            }
        }
        return null;
    }

    /** Every attached mod's id, or just the current config's when none has attached yet. */
    private static List<String> modIds() {
        List<String> ids = new ArrayList<>();
        for (MarieContext ctx : MarieModRegistry.getAll()) {
            ids.add(ctx.modId());
        }
        if (ids.isEmpty()) {
            ids.add(IMarieConfig.get().modId());
        }
        return ids;
    }

    /** Runs {@code action} as {@code modId}, so per-mod lookups inside it (README, datapack path) resolve to that mod. */
    private static void runAsMod(String modId, Runnable action) {
        MarieContext ctx = MarieModRegistry.get(modId);
        if (ctx == null) {
            action.run();
        } else {
            MarieContext.runAs(ctx, action);
        }
    }

    private static Path configDir(String modId) {
        return FMLPaths.CONFIGDIR.get().resolve(modId);
    }

    private static void apply(LoadPass pass) {
        INSTANCE.reset();
        pass.merged.forEach(INSTANCE::register);
        INSTANCE.freeze();
        OWNERS.clear();
        OWNERS.putAll(pass.owners);
        SHADOWED.clear();
        SHADOWED.putAll(pass.shadowed);
        DIRTY.clear();
    }

    /**
     * Datapack path {@code <modid>:config/colors.json} ({@code data/<modid>/config/colors.json}).
     * If absent, falls back to {@link #load()}.
     */
    public static void loadFromDatapack(ResourceManager resourceManager) {
        LoadPass pass = new LoadPass();
        for (String modId : modIds()) {
            runAsMod(modId, () -> MarieResourceLoader.loadFromModConfig(
                    resourceManager,
                    DatapackSchema.CONFIG_COLORS,
                    reader -> {
                        mergeFrom(reader, modId, pass);
                    },
                    () -> loadModFile(modId, pass),
                    "[ColorRegistry] Loaded from datapack override",
                    "[ColorRegistry] Failed to load datapack colors, falling back to config folder",
                    "[ColorRegistry] Loaded from config folder"
            ));
        }
        apply(pass);
    }

    private static void writeEmpty(Path file) throws IOException {
        try (Writer w = Files.newBufferedWriter(file)) {
            GSON.toJson(new JsonArray(), w);
        }
    }

    /** Parses one colors.json in full, then adds it to {@code pass} as {@code modId}'s; returns how many entries it had. */
    private static int mergeFrom(Reader reader, String modId, LoadPass pass) {
        Map<String, Integer> parsed = new LinkedHashMap<>();
        JsonArray arr = GSON.fromJson(reader, JsonArray.class);
        if (arr != null) {
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject obj = el.getAsJsonObject();
                if (!obj.has("key")) {
                    continue;
                }
                int argb = ColorArgbParser.parse(obj.get("argb"));
                parsed.put(obj.get("key").getAsString(), (argb & 0x00_FF_FF_FF) | 0xFF00_0000);
            }
        }
        parsed.forEach((key, argb) -> {
            String previousOwner = pass.owners.get(key);
            // Namespaced keys are routed to their own mod's file instead, so only plain keys shadow.
            if (previousOwner != null && !previousOwner.equals(modId) && attachedNamespace(key) == null) {
                pass.shadowed.computeIfAbsent(previousOwner, k -> new LinkedHashMap<>()).put(key, pass.merged.get(key));
            }
            pass.merged.put(key, argb);
            pass.owners.put(key, modId);
        });
        return parsed.size();
    }
}
