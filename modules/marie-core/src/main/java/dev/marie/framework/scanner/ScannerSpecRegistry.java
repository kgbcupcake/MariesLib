package dev.marie.framework.scanner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.core.IMarieConfig;
import dev.marie.framework.core.MarieCore;
import dev.marie.framework.registry.AbstractRegistry;
import dev.marie.framework.data.DatapackSchema;
import dev.marie.framework.util.MarieJsonUtils;
import dev.marie.framework.util.MarieResourceLoader;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads the scanner classification spec from JSON.
 *
 * <p><b>Priority / Override Stack (lowest to highest):</b></p>
 * <ol>
 *   <li>Bundled defaults at {@code data/<modid>/<modid>/scanner/scanner_spec.json}</li>
 *   <li>{@code config/<modid>/scanner_spec.json} (modpack creator override)</li>
 *   <li>{@code data/<ns>/<modid>/scanner/scanner_spec.json} (datapack override)</li>
 * </ol>
 *
 * <p>The spec contains all signal multipliers, weight maps, and archetype patterns
 * used by {@link ItemClassifier}. Domain-specific source property signals are
 * registered via {@link dev.marie.framework.api.marieapi.MarieAPI#registerSourcePropertySignal}.</p>
 */
@ApiStatus.Experimental
public final class ScannerSpecRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "scanner_spec.json";

    private static String bundledResourcePath() {
        return "/data/" + IMarieConfig.get().modId() + "/" + DatapackSchema.root() + "/scanner/scanner_spec.json";
    }
    private static final String SPEC_KEY = "active";

    private static final class Core extends AbstractRegistry<String, ScannerSpec> {
        Core() {
            super("ScannerSpecRegistry");
        }
    }

    private static final Core INSTANCE = new Core();

    private ScannerSpecRegistry() {}

    public static ScannerSpec get() {
        ScannerSpec spec = INSTANCE.get(SPEC_KEY);
        return spec != null ? spec : ScannerSpec.empty();
    }

    public static void load() {
        Path configDir = FMLPaths.CONFIGDIR.get().resolve(IMarieConfig.get().modId());
        Path file = configDir.resolve(CONFIG_FILE_NAME);
        try {
            Files.createDirectories(configDir);
            if (!Files.exists(file)) {
                if (writeBundledTo(file)) {
                    MarieCore.LOGGER.info("[ScannerSpecRegistry] Wrote default scanner_spec.json");
                } else {
                    MarieCore.LOGGER.warn("[ScannerSpecRegistry] No bundled scanner_spec.json for this modId, skipping write");
                }
            }
            ScannerSpec spec = parseFile(file);
            if (spec == null) {
                MarieCore.LOGGER.warn("[ScannerSpecRegistry] scanner_spec.json was empty/invalid, falling back to bundled defaults");
                spec = parseBundled();
            }
            if (spec == null) {
                spec = ScannerSpec.empty();
            }
            INSTANCE.reset();
            INSTANCE.register(SPEC_KEY, spec);
            INSTANCE.freeze();
            MarieCore.LOGGER.info("[ScannerSpecRegistry] Loaded scanner spec from {}", file);
        } catch (IOException e) {
            MarieCore.LOGGER.error("[ScannerSpecRegistry] Failed to load scanner_spec.json, using bundled defaults", e);
            ScannerSpec bundled = parseBundled();
            INSTANCE.reset();
            INSTANCE.register(SPEC_KEY, bundled != null ? bundled : ScannerSpec.empty());
            INSTANCE.freeze();
        }

        try {
            writeReadmeIfAbsent(configDir.resolve("Read_Me"));
        } catch (IOException e) {
            MarieCore.LOGGER.warn("[ScannerSpecRegistry] Failed to write SCANNER_SPEC_README.md", e);
        }
    }

    private static void writeReadmeIfAbsent(Path readmeDir) throws IOException {
        Path readme = readmeDir.resolve("SCANNER_SPEC_README.md");
        if (Files.exists(readme)) {
            return;
        }
        Files.createDirectories(readmeDir);
        String resourcePath = "/data/" + IMarieConfig.get().modId() + "/config/SCANNER_SPEC_README.md";
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(resourcePath.substring(1))) {
            if (in == null) {
                MarieCore.LOGGER.warn("[ScannerSpecRegistry] No bundled SCANNER_SPEC_README.md for this modId, skipping write. Tried resource path: {}", resourcePath);
                return;
            }
            Files.copy(in, readme);
        }
    }

    public static void reload() {
        MarieCore.LOGGER.info("[ScannerSpecRegistry] Reloading scanner_spec.json");
        load();
    }

    public static void loadFromDatapack(ResourceManager resourceManager) {
        MarieResourceLoader.loadFromModConfig(
                resourceManager,
                "scanner/scanner_spec.json",
                reader -> {
                    ScannerSpec spec = parseReader(reader);
                    if (spec == null) {
                        return false;
                    }
                    INSTANCE.reset();
                    INSTANCE.register(SPEC_KEY, spec);
                    INSTANCE.freeze();
                    return true;
                },
                ScannerSpecRegistry::load,
                "[ScannerSpecRegistry] Loaded scanner_spec.json from datapack override",
                "[ScannerSpecRegistry] Failed to load datapack override, falling back to config folder",
                null
        );
    }

    private static ScannerSpec parseFile(Path file) throws IOException {
        try (Reader r = Files.newBufferedReader(file)) {
            return parseReader(r);
        }
    }

    private static ScannerSpec parseBundled() {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(bundledResourcePath().substring(1))) {
            if (in == null) return null;
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return parseReader(r);
            }
        } catch (IOException e) {
            MarieCore.LOGGER.error("[ScannerSpecRegistry] Failed to read bundled scanner_spec.json", e);
            return null;
        }
    }

    private static boolean writeBundledTo(Path file) throws IOException {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(bundledResourcePath().substring(1))) {
            if (in == null) {
                return false;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
                 Writer writer = Files.newBufferedWriter(file)) {
                JsonObject obj = GSON.fromJson(reader, JsonObject.class);
                if (obj == null) {
                    throw new IOException("Bundled scanner_spec.json was empty/invalid");
                }
                GSON.toJson(obj, writer);
            }
        }
        return true;
    }

    private static ScannerSpec parseReader(Reader reader) {
        JsonObject root = GSON.fromJson(reader, JsonObject.class);
        if (root == null) return null;

        Multipliers mult = parseMultipliers(getObj(root, "multipliers"));
        Map<String, Map<String, Float>> communityTags = parseStringFloatMap(getObj(root, "community_tags"));
        String communityTagDirectory = MarieJsonUtils.getOptionalString(root, "community_tag_directory", "foods/");
        Map<String, Map<String, Float>> namespaces = parseStringFloatMap(getObj(root, "namespaces"));
        Map<String, Map<String, Float>> suffixes = parseStringFloatMap(getObj(root, "suffixes"));
        Map<String, Map<String, Float>> keywords = TokenStemmer.stemMapKeys(parseStringFloatMap(getObj(root, "keywords")));
        Map<String, Map<String, Float>> negatives = TokenStemmer.stemMapKeys(parseStringFloatMap(getObj(root, "negative_keywords")));
        List<ArchetypePattern> archetypes = parseArchetypes(getArr(root, "archetypes"));
        Set<String> excludedItems = parseStringSet(getArr(root, "excluded_items"));
        Set<String> contestableValues = parseStringSet(getArr(root, "contestable_values"));
        String[] stemmerDictionary = parseStringArray(getArr(root, "stemmer_dictionary"));
        Map<String, String[]> stemmerCompoundSplits = parseCompoundSplits(getObj(root, "stemmer_compound_splits"));
        Map<String, String> stemmerIrregularForms = parseStringStringMap(getObj(root, "stemmer_irregular_forms"));
        Set<String> stemmerStopWords = parseStringSet(getArr(root, "stemmer_stop_words"));
        Set<String> stemmerNoiseSuffixes = parseStringSet(getArr(root, "stemmer_noise_suffixes"));

        return new ScannerSpec(
                mult,
                communityTags,
                communityTagDirectory,
                namespaces,
                suffixes,
                keywords,
                negatives,
                archetypes,
                excludedItems,
                contestableValues,
                stemmerDictionary,
                stemmerCompoundSplits,
                stemmerIrregularForms,
                stemmerStopWords,
                stemmerNoiseSuffixes
        );
    }

    private static Multipliers parseMultipliers(JsonObject obj) {
        if (obj == null) return Multipliers.defaults();
        return new Multipliers(
                getFloat(obj, "community_tag", 5.0f),
                getFloat(obj, "namespace", 4.0f),
                getFloat(obj, "suffix", 3.0f),
                getFloat(obj, "keyword", 2.0f),
                getFloat(obj, "archetype", 2.0f),
                getFloat(obj, "recipe_inheritance", 1.0f),
                getFloat(obj, "namespace_peer", 0.5f),
                getFloat(obj, "secondary_suffix", 0.5f),
                getFloat(obj, "namespace_peer_average_weight", 0.5f)
        );
    }

    private static Map<String, Map<String, Float>> parseStringFloatMap(JsonObject obj) {
        if (obj == null) return Map.of();
        Map<String, Map<String, Float>> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            Map<String, Float> inner = parseFlatFloatMap(e.getValue().getAsJsonObject());
            if (!inner.isEmpty()) {
                result.put(e.getKey(), inner);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, Float> parseFlatFloatMap(JsonObject obj) {
        if (obj == null) return Map.of();
        Map<String, Float> inner = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e2 : obj.entrySet()) {
            if (e2.getValue().isJsonPrimitive()) {
                inner.put(e2.getKey(), e2.getValue().getAsFloat());
            }
        }
        return Collections.unmodifiableMap(inner);
    }

    private static List<ArchetypePattern> parseArchetypes(JsonArray arr) {
        if (arr == null) return List.of();
        List<ArchetypePattern> out = new ArrayList<>(arr.size());
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            if (!o.has("pattern")) continue;
            String pattern = o.get("pattern").getAsString();
            Map<String, Float> contribs = parseFlatFloatMap(getObj(o, "contributions"));
            out.add(new ArchetypePattern(pattern, contribs));
        }
        return Collections.unmodifiableList(out);
    }

    private static Set<String> parseStringSet(JsonArray arr) {
        if (arr == null) return Set.of();
        Set<String> out = new LinkedHashSet<>();
        for (JsonElement el : arr) {
            if (el != null && el.isJsonPrimitive()) {
                out.add(el.getAsString());
            }
        }
        return Collections.unmodifiableSet(out);
    }

    private static JsonObject getObj(JsonObject parent, String key) {
        if (parent == null || !parent.has(key)) return null;
        JsonElement el = parent.get(key);
        return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
    }

    private static JsonArray getArr(JsonObject parent, String key) {
        if (parent == null || !parent.has(key)) return null;
        JsonElement el = parent.get(key);
        return el != null && el.isJsonArray() ? el.getAsJsonArray() : null;
    }

    private static float getFloat(JsonObject obj, String key, float fallback) {
        if (obj == null) {
            return fallback;
        }
        return MarieJsonUtils.getOptionalFloat(obj, key, fallback);
    }

    private static int getInt(JsonObject obj, String key, int fallback) {
        if (obj == null) {
            return fallback;
        }
        return MarieJsonUtils.getOptionalInt(obj, key, fallback);
    }

    public static String communityTagDirectory() {
        return get().communityTagDirectory();
    }

    /**
     * Value categories the active spec has opted into recipe-inheritance contestability.
     * Empty unless a consumer mod set {@code contestable_values} in its {@code scanner_spec.json}
     * — see {@link ScannerSpec#contestableValues()}.
     */
    public static Set<String> contestableValues() {
        return get().contestableValues();
    }

    public static String[] stemmerDictionary() {
        return get().stemmerDictionary();
    }

    public static Map<String, String[]> stemmerCompoundSplits() {
        return get().stemmerCompoundSplits();
    }

    public static Map<String, String> stemmerIrregularForms() {
        return get().stemmerIrregularForms();
    }

    public static Set<String> stemmerStopWords() {
        return get().stemmerStopWords();
    }

    public static Set<String> stemmerNoiseSuffixes() {
        return get().stemmerNoiseSuffixes();
    }

    private static String[] parseStringArray(JsonArray arr) {
        if (arr == null) {
            return new String[0];
        }
        List<String> out = new ArrayList<>(arr.size());
        for (JsonElement el : arr) {
            if (el != null && el.isJsonPrimitive()) {
                out.add(el.getAsString());
            }
        }
        return out.toArray(String[]::new);
    }

    private static Map<String, String[]> parseCompoundSplits(JsonObject obj) {
        if (obj == null) {
            return Map.of();
        }
        Map<String, String[]> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            if (!entry.getValue().isJsonArray()) {
                continue;
            }
            JsonArray array = entry.getValue().getAsJsonArray();
            List<String> parts = new ArrayList<>(array.size());
            for (JsonElement el : array) {
                if (el != null && el.isJsonPrimitive()) {
                    parts.add(el.getAsString());
                }
            }
            if (!parts.isEmpty()) {
                result.put(entry.getKey(), parts.toArray(String[]::new));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, String> parseStringStringMap(JsonObject obj) {
        if (obj == null) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                result.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Spec data classes
    // ─────────────────────────────────────────────────────────────────────────

    public record ScannerSpec(
            Multipliers multipliers,
            Map<String, Map<String, Float>> communityTagWeights,
            /**
             * The {@code c} namespace tag directory (e.g. {@code "foods/"}) that
             * {@link dev.marie.framework.scanner.stages.CommunityTagResolutionStage} scans for community
             * tag matches, and that scanner traces/labels are built from. Defaults to {@code "foods/"} to
             * preserve the framework's original default behavior; a consumer mod classifying a different
             * kind of item (e.g. tools or armor) can override this in its own {@code scanner_spec.json} to
             * something like {@code "materials/"}.
             */
            String communityTagDirectory,
            Map<String, Map<String, Float>> namespaceWeights,
            Map<String, Map<String, Float>> suffixWeights,
            Map<String, Map<String, Float>> keywordWeights,
            Map<String, Map<String, Float>> negativeKeywords,
            List<ArchetypePattern> archetypes,
            Set<String> excludedItems,
            /**
             * Value categories the consuming mod has explicitly opted into <i>recipe-inheritance
             * contestability</i> (JSON key {@code contestable_values}, an array of value-key strings).
             *
             * <p>When a category is in this set and the recipe-inheritance supplement's combined,
             * decayed weight for that category exceeds the keyword/suffix-matched category's weight,
             * {@link dev.marie.framework.scan.RuntimeResolutionMerge} makes the recipe-derived
             * category the winning classification outright — a genuine override, not merely an
             * appended secondary bar.</p>
             *
             * <p><b>Empty by default.</b> With no entries (the shipped default, and the value in
             * {@link ScannerSpec#empty()}), every category keeps the historical append-only
             * behaviour: a keyword/suffix match permanently locks the winning category and recipe
             * data can only introduce brand-new categories, never displace an existing one. A
             * category must be named here for recipe weight to be allowed to beat the name.</p>
             *
             * <p>Example — a consumer mod that trusts its crafting graph over item names for a
             * "metals" category would put in its {@code scanner_spec.json}:
             * <pre>{@code "contestable_values": ["metals", "woods"]}</pre>
             * so that e.g. an "iron_reinforced_frame" whose recipe is overwhelmingly wood resolves to
             * {@code woods} instead of being locked to {@code metals} by the "iron" token.</p>
             */
            Set<String> contestableValues,
            String[] stemmerDictionary,
            Map<String, String[]> stemmerCompoundSplits,
            Map<String, String> stemmerIrregularForms,
            Set<String> stemmerStopWords,
            Set<String> stemmerNoiseSuffixes
    ) {
        public static ScannerSpec empty() {
            return new ScannerSpec(
                    Multipliers.defaults(),
                    Map.of(),
                    "foods/",
                    Map.of(), Map.of(), Map.of(), Map.of(),
                    List.of(),
                    Set.of(),
                    Set.of(),
                    new String[0],
                    Map.of(),
                    Map.of(),
                    Set.of(),
                    Set.of()
            );
        }
    }

    public record Multipliers(
            float communityTag,
            float namespace,
            float suffix,
            float keyword,
            float archetype,
            float recipeInheritance,
            float namespacePeer,
            float secondarySuffix,
            float namespacePeerAverageWeight
    ) {
        public static Multipliers defaults() {
            return new Multipliers(5.0f, 4.0f, 3.0f, 2.0f, 2.0f, 1.0f, 0.5f, 0.5f, 0.5f);
        }
    }
}
