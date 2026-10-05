package dev.marie.framework.runtime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.core.IMarieConfig;
import dev.marie.framework.data.DatapackSchema;
import dev.marie.framework.registry.AbstractRegistry;
import dev.marie.framework.util.MarieResourceLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Loads per-source manual value assignments — the overrides the in-game item editor's File &gt; Save
 * Override writes ({@link #save()}) — from under {@code config/&lt;modid&gt;/item_editor/}, split by
 * value key to match the editor's own per-category sliders (Protein, Dairy, Vegetables, etc., driven
 * by whatever {@code ValueDefinition}s the consuming mod registers — not a fixed list):
 * <pre>
 * item_editor/
 *   source_classifications.json        — source id + calories + enabled (not tied to any one value)
 *   Source Classification/
 *     &lt;value key&gt;/source_classifications.json   — source id + that one value's override, one file per key
 * </pre>
 * Replaces both SourceOverrideRegistry (source_overrides.json) and SourceValueRegistry (source_values.json).
 * On first load, migrates existing old files automatically — including a not-yet-split single combined
 * file from the earlier {@code overrides/Overrides/source_classifications.json} location, before the
 * folder was renamed to {@code item_editor} and split per value key.
 */
@ApiStatus.Internal
public class SourceClassificationRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * @param total    the effective calorie/total override to apply, or 0 when the entry specifies
     *                 none. Populated from an explicit {@code "calories"} field (following the same
     *                 convention as food_overrides.json) when present, otherwise from the legacy
     *                 {@code "total"} field.
     * @param calories the raw explicit {@code "calories"} field as authored (0 = absent). Retained
     *                 separately from {@code total} so the field round-trips through
     *                 {@link #writeRegistry(Path)} under its own name.
     */
    public record SourceClassification(String sourceId, Map<String, Float> values, float total, int calories, boolean enabled) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final class Core extends AbstractRegistry<String, SourceClassification> {
        Core() {
            super("SourceClassificationRegistry");
        }
    }

    private static final Core INSTANCE = new Core();

    // sourceIds pushed into SourceRegistry by the last pushToSourceRegistry() call, so the next call can drop stale ones first.
    private static Set<ResourceLocation> bridgedSourceIds = Set.of();

    public static Optional<SourceClassification> getOverride(String sourceId) {
        SourceClassification entry = INSTANCE.get(sourceId);
        if (entry != null && entry.enabled()) {
            return Optional.of(entry);
        }
        return Optional.empty();
    }

    public static SourceClassification get(String sourceId) {
        return INSTANCE.get(sourceId);
    }

    public static Map<String, SourceClassification> getAll() {
        return INSTANCE.entries();
    }

    /**
     * Returns the registered classification score for an item/value pair, or 0 if none.
     * Delegates to SourceRegistry for scanner-derived external classifications.
     */
    public static float getScore(String itemId, String valueKey) {
        ResourceLocation loc = ResourceLocation.tryParse(itemId);
        if (loc == null) {
            return 0f;
        }
        Map<String, Float> classification = SourceRegistry.getExternalClassification(loc);
        if (classification == null) {
            return 0f;
        }
        Float score = classification.get(valueKey);
        return score != null ? score : 0f;
    }

    /** Subfolder of {@code item_editor/} holding one subfolder per value key (the item editor's own sliders — Protein, Dairy, Vegetables, etc.), each with its own {@code source_classifications.json}. */
    private static final String CATEGORIES_DIR_NAME = "Source Classification";
    private static final String DATA_FILE_NAME = "source_classifications.json";

    public static void load() {
        Path configDir = FMLPaths.CONFIGDIR.get().resolve(IMarieConfig.get().modId());
        Path itemEditorDir = configDir.resolve("item_editor");
        Path categoriesDir = itemEditorDir.resolve(CATEGORIES_DIR_NAME);
        Path readmeDir = itemEditorDir.resolve("Read_Me");
        Path rootFile = itemEditorDir.resolve(DATA_FILE_NAME);
        // Pre-"item_editor" locations, oldest first: a bare config/<modid>/source_classifications.json
        // (or the even older source_overrides.json/source_values.json pair it replaced), then a flat
        // config/<modid>/overrides/source_classifications.json, then the most recent (still one combined
        // file, not yet split by category) config/<modid>/overrides/Overrides/source_classifications.json.
        Path oldOverridesDir = configDir.resolve("overrides");
        Path oldDataFile = oldOverridesDir.resolve("Overrides").resolve(DATA_FILE_NAME);
        Path oldFlatFile = oldOverridesDir.resolve(DATA_FILE_NAME);
        Path oldOverrides = configDir.resolve("source_overrides.json");
        Path oldValues = configDir.resolve("source_values.json");
        Path oldRootFile = configDir.resolve(DATA_FILE_NAME);

        try {
            Files.createDirectories(categoriesDir);
            Files.createDirectories(readmeDir);

            boolean hasSplitData = Files.exists(rootFile) || hasAnyCategoryFile(categoriesDir);
            boolean hasLegacyData = Files.exists(oldDataFile) || Files.exists(oldFlatFile)
                    || Files.exists(oldOverrides) || Files.exists(oldValues) || hasContent(oldRootFile);

            if (hasSplitData) {
                parseSplit(rootFile, categoriesDir);
            } else if (hasLegacyData) {
                JsonArray legacy = readLegacyCombined(oldDataFile, oldFlatFile, oldOverrides, oldRootFile);
                INSTANCE.reset();
                for (int i = 0; i < legacy.size(); i++) {
                    JsonElement el = legacy.get(i);
                    if (!el.isJsonObject()) {
                        LOGGER.warn("[SourceClassificationRegistry] Skipping malformed entry at index {}: not a JSON object", i);
                        continue;
                    }
                    parseEntry(el.getAsJsonObject(), i);
                }
                INSTANCE.freeze();
                writeSplit(rootFile, categoriesDir);
                pushToSourceRegistry();
                LOGGER.warn("[SourceClassificationRegistry] Migrated {} entries into {}/{} (per-category subfolders)", INSTANCE.size(), itemEditorDir, CATEGORIES_DIR_NAME);
            } else {
                writeSplit(rootFile, categoriesDir);
                INSTANCE.reset();
                INSTANCE.freeze();
                pushToSourceRegistry();
                LOGGER.info("[SourceClassificationRegistry] Wrote default source_classifications.json");
            }

            Files.deleteIfExists(oldOverrides);
            Files.deleteIfExists(oldValues);
            Files.deleteIfExists(oldFlatFile);
            Files.deleteIfExists(oldDataFile);
            Files.deleteIfExists(oldRootFile);
            deleteIfEmptyDir(oldOverridesDir.resolve("Overrides"));
            deleteIfEmptyDir(oldOverridesDir.resolve("Read_Me"));
            deleteIfEmptyDir(oldOverridesDir);
        } catch (IOException e) {
            LOGGER.error("[SourceClassificationRegistry] Failed to load source_classifications.json", e);
            INSTANCE.reset();
            INSTANCE.freeze();
            pushToSourceRegistry();
        } catch (RuntimeException e) {
            // Per-entry parse failures are isolated in parseEntry()/parseSplit(); this only catches
            // whole-file corruption (invalid JSON syntax, or a top-level value that isn't an array).
            LOGGER.error("[SourceClassificationRegistry] source_classifications.json is not valid JSON, ignoring file", e);
            INSTANCE.reset();
            INSTANCE.freeze();
            pushToSourceRegistry();
        }

        try {
            Path oldReadmeNested = oldOverridesDir.resolve("Read_Me").resolve("SOURCE_CLASSIFICATIONS_README.md");
            Path oldReadmeFlat = oldOverridesDir.resolve("SOURCE_CLASSIFICATIONS_README.md");
            Path newReadme = readmeDir.resolve("SOURCE_CLASSIFICATIONS_README.md");
            if (Files.exists(oldReadmeNested) && !Files.exists(newReadme)) {
                Files.move(oldReadmeNested, newReadme);
            }
            Files.deleteIfExists(oldReadmeFlat);
            writeReadmeIfAbsent(readmeDir);
            deleteIfEmptyDir(oldOverridesDir.resolve("Read_Me"));
            deleteIfEmptyDir(oldOverridesDir);
        } catch (IOException e) {
            LOGGER.warn("[SourceClassificationRegistry] Failed to write SOURCE_CLASSIFICATIONS_README.md", e);
        }
    }

    /** Whether {@code categoriesDir} already has at least one {@code <category>/source_classifications.json} — i.e. this mod's data is already in the split, per-category layout rather than one combined file. */
    private static boolean hasAnyCategoryFile(Path categoriesDir) {
        if (!Files.isDirectory(categoriesDir)) {
            return false;
        }
        try (var dirs = Files.list(categoriesDir)) {
            return dirs.anyMatch(dir -> Files.exists(dir.resolve(DATA_FILE_NAME)));
        } catch (IOException e) {
            return false;
        }
    }

    /** Reads whichever single-file legacy location is present (checked newest first) into one combined array, matching the shape {@link #parseEntry} already expects. */
    private static JsonArray readLegacyCombined(Path oldDataFile, Path oldFlatFile, Path oldOverrides, Path oldRootFile) {
        JsonArray combined = new JsonArray();
        if (Files.exists(oldDataFile)) {
            appendArrayFrom(oldDataFile, combined);
            return combined;
        }
        if (Files.exists(oldFlatFile)) {
            appendArrayFrom(oldFlatFile, combined);
            return combined;
        }
        // Oldest layout: source_overrides.json and a root source_classifications.json, merged
        // (mirrors the pre-item_editor migrateFromLegacy behavior this replaces).
        appendArrayFrom(oldOverrides, combined);
        appendArrayFrom(oldRootFile, combined);
        return combined;
    }

    private static void appendArrayFrom(Path file, JsonArray into) {
        if (!Files.exists(file)) {
            return;
        }
        try (Reader r = Files.newBufferedReader(file)) {
            JsonArray arr = GSON.fromJson(r, JsonArray.class);
            if (arr != null) {
                for (JsonElement el : arr) {
                    into.add(el);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[SourceClassificationRegistry] Could not read {} during migration: {}", file, e.getMessage());
        }
    }

    /**
     * Loads the split, per-category layout: {@code rootFile} (source id + calories + enabled, no
     * values) plus one {@code categoriesDir/<key>/source_classifications.json} per value key (source
     * id + that one key's value), merged back into full {@link SourceClassification} entries. A
     * source id present in a category file but missing from {@code rootFile} (e.g. the root file was
     * hand-edited) still loads, defaulting to enabled with no calorie override — the same lenient
     * fallback {@link #parseEntry} already applies to a missing {@code "enabled"}/{@code "calories"}.
     */
    private static void parseSplit(Path rootFile, Path categoriesDir) throws IOException {
        LinkedHashMap<String, JsonObject> rootById = new LinkedHashMap<>();
        if (Files.exists(rootFile)) {
            try (Reader r = Files.newBufferedReader(rootFile)) {
                JsonArray arr = GSON.fromJson(r, JsonArray.class);
                if (arr != null) {
                    for (JsonElement el : arr) {
                        if (el.isJsonObject() && el.getAsJsonObject().has("source_id")) {
                            rootById.put(el.getAsJsonObject().get("source_id").getAsString(), el.getAsJsonObject());
                        }
                    }
                }
            }
        }

        LinkedHashMap<String, Map<String, Float>> valuesBySource = new LinkedHashMap<>();
        if (Files.isDirectory(categoriesDir)) {
            try (var dirs = Files.list(categoriesDir)) {
                for (Path categoryDir : dirs.filter(Files::isDirectory).toList()) {
                    String category = categoryDir.getFileName().toString();
                    Path file = categoryDir.resolve(DATA_FILE_NAME);
                    if (!Files.exists(file)) {
                        continue;
                    }
                    try (Reader r = Files.newBufferedReader(file)) {
                        JsonArray arr = GSON.fromJson(r, JsonArray.class);
                        if (arr == null) {
                            continue;
                        }
                        for (JsonElement el : arr) {
                            if (!el.isJsonObject()) {
                                continue;
                            }
                            JsonObject obj = el.getAsJsonObject();
                            if (!obj.has("source_id") || !obj.has("value")) {
                                continue;
                            }
                            valuesBySource.computeIfAbsent(obj.get("source_id").getAsString(), k -> new LinkedHashMap<>())
                                    .put(category, obj.get("value").getAsFloat());
                        }
                    } catch (RuntimeException e) {
                        LOGGER.warn("[SourceClassificationRegistry] Skipping malformed category file {}: {}", file, e.getMessage());
                    }
                }
            }
        }

        LinkedHashMap<String, SourceClassification> combined = new LinkedHashMap<>();
        for (Map.Entry<String, JsonObject> e : rootById.entrySet()) {
            try {
                combined.put(e.getKey(), classificationFromRoot(e.getValue(), valuesBySource.getOrDefault(e.getKey(), Map.of())));
            } catch (RuntimeException ex) {
                LOGGER.warn("[SourceClassificationRegistry] Skipping malformed entry ({}): {}", e.getKey(), ex.getMessage());
            }
        }
        for (Map.Entry<String, Map<String, Float>> e : valuesBySource.entrySet()) {
            combined.computeIfAbsent(e.getKey(), id -> new SourceClassification(id, new HashMap<>(e.getValue()), 0f, 0, true));
        }

        INSTANCE.reset();
        for (Map.Entry<String, SourceClassification> e : combined.entrySet()) {
            INSTANCE.register(e.getKey(), e.getValue());
        }
        INSTANCE.freeze();
        pushToSourceRegistry();
        LOGGER.info("[SourceClassificationRegistry] Loaded {} entries from config folder", INSTANCE.size());
    }

    private static SourceClassification classificationFromRoot(JsonObject obj, Map<String, Float> values) {
        String sourceId = obj.get("source_id").getAsString();
        boolean enabled = !obj.has("enabled") || obj.get("enabled").getAsBoolean();
        float legacyTotal = obj.has("total") && !obj.get("total").isJsonNull() ? obj.get("total").getAsFloat() : 0f;
        int calories = obj.has("calories") && !obj.get("calories").isJsonNull() ? obj.get("calories").getAsInt() : 0;
        float total = calories != 0 ? (float) calories : legacyTotal;
        return new SourceClassification(sourceId, new HashMap<>(values), total, calories, enabled);
    }

    /** Writes the current {@link #INSTANCE} out in the split layout: {@code rootFile} (id/calories/enabled) plus one {@code categoriesDir/<key>/source_classifications.json} per value key found across every entry's {@link SourceClassification#values()}. Prunes any category subfolder no longer referenced by any entry. */
    private static void writeSplit(Path rootFile, Path categoriesDir) throws IOException {
        JsonArray rootArr = new JsonArray();
        LinkedHashMap<String, JsonArray> byCategory = new LinkedHashMap<>();
        for (SourceClassification entry : INSTANCE.values()) {
            JsonObject rootObj = new JsonObject();
            rootObj.addProperty("source_id", entry.sourceId());
            if (entry.calories() != 0) {
                rootObj.addProperty("calories", entry.calories());
            } else {
                rootObj.addProperty("total", entry.total());
            }
            rootObj.addProperty("enabled", entry.enabled());
            rootArr.add(rootObj);

            for (Map.Entry<String, Float> v : entry.values().entrySet()) {
                JsonObject catObj = new JsonObject();
                catObj.addProperty("source_id", entry.sourceId());
                catObj.addProperty("value", v.getValue());
                byCategory.computeIfAbsent(v.getKey(), k -> new JsonArray()).add(catObj);
            }
        }

        Files.createDirectories(categoriesDir);
        try (Writer w = Files.newBufferedWriter(rootFile)) {
            GSON.toJson(rootArr, w);
        }
        pruneStaleCategoryDirs(categoriesDir, byCategory.keySet());
        for (Map.Entry<String, JsonArray> e : byCategory.entrySet()) {
            Path dir = categoriesDir.resolve(sanitizeCategoryName(e.getKey()));
            Files.createDirectories(dir);
            try (Writer w = Files.newBufferedWriter(dir.resolve(DATA_FILE_NAME))) {
                GSON.toJson(e.getValue(), w);
            }
        }
    }

    /** Deletes a category subfolder (and its one data file) that no entry references anymore, so a renamed/removed value key doesn't leave a stale folder behind. */
    private static void pruneStaleCategoryDirs(Path categoriesDir, Set<String> currentKeys) {
        if (!Files.isDirectory(categoriesDir)) {
            return;
        }
        Set<String> sanitizedCurrent = new HashSet<>();
        for (String key : currentKeys) {
            sanitizedCurrent.add(sanitizeCategoryName(key));
        }
        try (var dirs = Files.list(categoriesDir)) {
            for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                if (sanitizedCurrent.contains(dir.getFileName().toString())) {
                    continue;
                }
                try {
                    Files.deleteIfExists(dir.resolve(DATA_FILE_NAME));
                    deleteIfEmptyDir(dir);
                } catch (IOException ignored) {
                    // Best-effort prune only.
                }
            }
        } catch (IOException ignored) {
            // Best-effort prune only.
        }
    }

    /** A value key as a filesystem-safe folder name — value keys are mod-defined strings, not guaranteed path-safe. */
    private static String sanitizeCategoryName(String key) {
        String sanitized = key.trim().replaceAll("[^A-Za-z0-9_\\- ]", "_");
        return sanitized.isEmpty() ? "_" : sanitized;
    }

    /** Removes {@code dir} only if it exists and migrating its contents elsewhere left it empty, so a legacy folder tree doesn't linger once nothing in it is current. */
    private static void deleteIfEmptyDir(Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var entries = Files.list(dir)) {
            if (entries.findAny().isEmpty()) {
                Files.delete(dir);
            }
        } catch (IOException ignored) {
            // Best-effort cleanup only; a leftover empty legacy folder isn't worth failing load() over.
        }
    }

    public static void reload() {
        LOGGER.info("[SourceClassificationRegistry] Reloading source_classifications.json");
        load();
    }

    public static void loadFromDatapack(ResourceManager resourceManager) {
        MarieResourceLoader.loadFromModConfig(
                resourceManager,
                DatapackSchema.CONFIG_SOURCE_CLASSIFICATIONS,
                SourceClassificationRegistry::parseFromReader,
                SourceClassificationRegistry::load,
                "[SourceClassificationRegistry] Loaded from datapack override",
                "[SourceClassificationRegistry] Failed to load from datapack, falling back to config folder",
                "[SourceClassificationRegistry] Loaded from config folder"
        );
    }

    private static void parseFromReader(Reader reader) {
        // Tokenize/parse the whole document BEFORE touching INSTANCE. Gson (lenient or not) rejects
        // trailing commas and other syntax errors with a RuntimeException; doing this first means a
        // malformed datapack file leaves the previous good state (and its bridged SourceRegistry
        // entries) intact instead of half-clearing it and silently serving stale data.
        JsonArray arr;
        try {
            arr = GSON.fromJson(reader, JsonArray.class);
        } catch (RuntimeException e) {
            LOGGER.error("[SourceClassificationRegistry] source_classifications.json failed to parse "
                    + "(check for trailing commas / invalid JSON); keeping previously loaded entries", e);
            throw e;
        }
        INSTANCE.reset();
        if (arr != null) {
            for (int i = 0; i < arr.size(); i++) {
                JsonElement el = arr.get(i);
                if (!el.isJsonObject()) {
                    LOGGER.warn("[SourceClassificationRegistry] Skipping malformed entry at index {}: not a JSON object", i);
                    continue;
                }
                parseEntry(el.getAsJsonObject(), i);
            }
        }
        INSTANCE.freeze();
        pushToSourceRegistry();
    }

    // Bridges enabled INSTANCE entries into SourceRegistry so getScore()/getExternalClassification() see them.
    private static void pushToSourceRegistry() {
        for (ResourceLocation staleId : bridgedSourceIds) {
            SourceRegistry.unregisterClassification(staleId);
        }
        Set<ResourceLocation> pushed = new HashSet<>();
        for (SourceClassification entry : INSTANCE.entries().values()) {
            if (!entry.enabled() || entry.values().isEmpty()) {
                continue;
            }
            ResourceLocation loc = ResourceLocation.tryParse(entry.sourceId());
            if (loc == null) {
                LOGGER.warn("[SourceClassificationRegistry] Skipping entry with malformed source_id: {}", entry.sourceId());
                continue;
            }
            // Isolated like parseEntry(): one bad entry must not abort the whole reload pass and skip every registry queued after this one.
            try {
                SourceRegistry.applyAuthoritativeOverride(loc, entry.values());
                pushed.add(loc);
            } catch (RuntimeException e) {
                LOGGER.warn("[SourceClassificationRegistry] Failed to push override for {}: {}", entry.sourceId(), e.getMessage());
            }
        }
        bridgedSourceIds = pushed;
    }

    private static void parseEntry(JsonObject obj, int index) {
        try {
            if (!obj.has("source_id")) {
                LOGGER.warn("[SourceClassificationRegistry] Skipping malformed entry at index {}: missing \"source_id\"", index);
                return;
            }
            String sourceId = obj.get("source_id").getAsString();
            boolean enabled = !obj.has("enabled") || obj.get("enabled").getAsBoolean();

            Map<String, Float> values = new HashMap<>();
            if (obj.has("values") && obj.get("values").isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : obj.getAsJsonObject("values").entrySet()) {
                    values.put(entry.getKey(), entry.getValue().getAsFloat());
                }
            }

            float legacyTotal = obj.has("total") && !obj.get("total").isJsonNull()
                    ? obj.get("total").getAsFloat()
                    : 0f;
            // Optional explicit calorie override, same convention as food_overrides.json
            // (int, default 0 / absent = "no explicit override"). Takes precedence over the
            // legacy "total" field when both are present.
            int calories = obj.has("calories") && !obj.get("calories").isJsonNull()
                    ? obj.get("calories").getAsInt()
                    : 0;
            float total = calories != 0 ? (float) calories : legacyTotal;

            INSTANCE.register(sourceId, new SourceClassification(sourceId, values, total, calories, enabled));
        } catch (RuntimeException e) {
            JsonElement idEl = obj.get("source_id");
            String label = (idEl != null && idEl.isJsonPrimitive()) ? idEl.getAsString() : ("index " + index);
            LOGGER.warn("[SourceClassificationRegistry] Skipping malformed entry ({}): {}", label, e.getMessage());
        }
    }

    private static boolean hasContent(Path file) {
        if (!Files.exists(file)) {
            return false;
        }
        try (Reader r = Files.newBufferedReader(file)) {
            JsonArray arr = GSON.fromJson(r, JsonArray.class);
            return arr != null && !arr.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static void writeReadmeIfAbsent(Path overridesDir) throws IOException {
        Path readme = overridesDir.resolve("SOURCE_CLASSIFICATIONS_README.md");
        if (Files.exists(readme)) {
            return;
        }
        String resourcePath = "/data/" + IMarieConfig.get().modId() + "/config/SOURCE_CLASSIFICATIONS_README.md";
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(resourcePath.substring(1))) {
            if (in == null) {
                LOGGER.warn("[SourceClassificationRegistry] No bundled SOURCE_CLASSIFICATIONS_README.md for this modId, skipping write. Tried resource path: {}", resourcePath);
                return;
            }
            Files.copy(in, readme);
        }
    }

    public static void save() {
        Path itemEditorDir = FMLPaths.CONFIGDIR.get().resolve(IMarieConfig.get().modId()).resolve("item_editor");
        try {
            writeSplit(itemEditorDir.resolve(DATA_FILE_NAME), itemEditorDir.resolve(CATEGORIES_DIR_NAME));
            LOGGER.info("[SourceClassificationRegistry] Saved source_classifications.json");
        } catch (IOException e) {
            LOGGER.error("[SourceClassificationRegistry] Failed to save source_classifications.json", e);
        }
    }

    public static void setOverride(String sourceId, Map<String, Float> values, boolean enabled) {
        setOverride(sourceId, values, 0, enabled);
    }

    /**
     * @param calories explicit calorie override for the entry, or 0 for "no explicit override".
     *                 Same convention as food_overrides.json. When non-zero this is the value the
     *                 classification pipeline falls back to if the resolver yields no calorie total.
     */
    public static void setOverride(String sourceId, Map<String, Float> values, int calories, boolean enabled) {
        Objects.requireNonNull(sourceId, "sourceId");
        LinkedHashMap<String, SourceClassification> next = new LinkedHashMap<>(INSTANCE.entries());
        next.put(sourceId, new SourceClassification(
                sourceId, new HashMap<>(values), calories != 0 ? (float) calories : 0f, calories, enabled));
        INSTANCE.reset();
        for (Map.Entry<String, SourceClassification> e : next.entrySet()) {
            INSTANCE.register(e.getKey(), e.getValue());
        }
        INSTANCE.freeze();
        pushToSourceRegistry();
    }

    public static void removeOverride(String sourceId) {
        Objects.requireNonNull(sourceId, "sourceId");
        LinkedHashMap<String, SourceClassification> next = new LinkedHashMap<>(INSTANCE.entries());
        next.remove(sourceId);
        INSTANCE.reset();
        for (Map.Entry<String, SourceClassification> e : next.entrySet()) {
            INSTANCE.register(e.getKey(), e.getValue());
        }
        INSTANCE.freeze();
        pushToSourceRegistry();
    }

}
