package dev.marie.framework.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import dev.marie.framework.core.MarieCore;
import dev.marie.framework.scanner.ScannerSpecRegistry;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Loads and saves {@code config/marieslib.cfg} (JSON).
 */
public final class MariesLibConfigIO {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = MarieCore.MOD_ID + ".cfg";
    private static final Set<String> LEGACY_SECTIONS = Set.of("modules", "memory", "thresholds", "effects", "client");
    private static volatile boolean legacyWarningLogged;

    private MariesLibConfigIO() {}

    public static Path configPath() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    public static void load() {
        MariesLibConfigHolder h = MariesLibConfigHolder.get();
        Path file = configPath();
        if (!Files.exists(file)) {
            h.loadScannerScalarsFromRegistry();
            save();
            MarieCore.LOGGER.info("[MarieCore] Wrote default {}", file);
            return;
        }
        try (Reader r = Files.newBufferedReader(file)) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
            if (root != null) {
                readIntoHolder(root, h);
            }
            MarieCore.LOGGER.info("[MarieCore] Loaded config from {}", file);
        } catch (IOException e) {
            MarieCore.LOGGER.error("[MarieCore] Failed to load {}, using defaults", file, e);
            h.loadScannerScalarsFromRegistry();
        }
    }

    public static void save() {
        MariesLibConfigHolder h = MariesLibConfigHolder.get();
        Path file = configPath();
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = holderToJson(h);
            try (Writer w = Files.newBufferedWriter(file)) {
                GSON.toJson(root, w);
            }
            patchScannerSpecScalars(h);
            ScannerSpecRegistry.reload();
            MarieCore.LOGGER.info("[MarieCore] Saved config to {}", file);
        } catch (IOException e) {
            MarieCore.LOGGER.error("[MarieCore] Failed to save {}", file, e);
        }
    }

    static void readIntoHolder(JsonObject root, MariesLibConfigHolder h) {
        // Log once if legacy sections are present
        if (!legacyWarningLogged) {
            for (String section : LEGACY_SECTIONS) {
                if (root.has(section)) {
                    MarieCore.LOGGER.info("[MarieCore] Ignoring legacy config sections (modules/memory/thresholds/effects/client) — these now belong to consuming mods");
                    legacyWarningLogged = true;
                    break;
                }
            }
        }

        JsonObject debug = obj(root, sectionKey(MariesLibConfigKeys.ENABLE_DEBUG_LOGGING));
        if (debug != null) {
            h.enableDebugLogging = bool(debug, leafKey(MariesLibConfigKeys.ENABLE_DEBUG_LOGGING), h.enableDebugLogging);
        }

        JsonObject modScan = obj(root, sectionKey(MariesLibConfigKeys.ENABLE_MOD_SCAN));
        if (modScan != null) {
            h.enableModScan = bool(modScan, leafKey(MariesLibConfigKeys.ENABLE_MOD_SCAN), h.enableModScan);
        }

        JsonObject scanner = obj(root, sectionKey(MariesLibConfigKeys.SCANNER_CONFIDENCE_SPREAD_THRESHOLD));
        if (scanner != null) {
            h.scannerConfidenceSpreadThreshold = flt(scanner, leafKey(MariesLibConfigKeys.SCANNER_CONFIDENCE_SPREAD_THRESHOLD), h.scannerConfidenceSpreadThreshold);
            h.compositeRatioThreshold = flt(scanner, leafKey(MariesLibConfigKeys.COMPOSITE_RATIO_THRESHOLD), h.compositeRatioThreshold);
            h.scannerEnableRecipeInheritance = bool(scanner, leafKey(MariesLibConfigKeys.SCANNER_ENABLE_RECIPE_INHERITANCE), h.scannerEnableRecipeInheritance);
            h.multiValueInheritanceThreshold = dbl(scanner, leafKey(MariesLibConfigKeys.MULTI_VALUE_INHERITANCE_THRESHOLD), h.multiValueInheritanceThreshold);
            JsonObject mult = obj(scanner, "multipliers");
            if (mult != null) {
                h.multCommunityTag = flt(mult, "communityTag", h.multCommunityTag);
                h.multNamespace = flt(mult, "namespace", h.multNamespace);
                h.multSuffix = flt(mult, "suffix", h.multSuffix);
                h.multKeyword = flt(mult, "keyword", h.multKeyword);
                h.multArchetype = flt(mult, "archetype", h.multArchetype);
                h.multRecipeInheritance = flt(mult, "recipeInheritance", h.multRecipeInheritance);
                h.multNamespacePeer = flt(mult, "namespacePeer", h.multNamespacePeer);
                h.multSecondarySuffix = flt(mult, "secondarySuffix", h.multSecondarySuffix);
                h.multNamespacePeerAverageWeight = flt(mult, "namespacePeerAverageWeight", h.multNamespacePeerAverageWeight);
            }
        }
    }

    static JsonObject holderToJson(MariesLibConfigHolder h) {
        JsonObject root = new JsonObject();

        JsonObject debug = new JsonObject();
        debug.addProperty("enableDebugLogging", h.enableDebugLogging);
        root.add("debug", debug);

        JsonObject modScan = new JsonObject();
        modScan.addProperty("enableModScan", h.enableModScan);
        root.add("modScan", modScan);

        JsonObject scanner = new JsonObject();
        scanner.addProperty("confidenceSpreadThreshold", h.scannerConfidenceSpreadThreshold);
        scanner.addProperty("compositeRatioThreshold", h.compositeRatioThreshold);
        scanner.addProperty("enableRecipeInheritance", h.scannerEnableRecipeInheritance);
        scanner.addProperty("multiValueInheritanceThreshold", h.multiValueInheritanceThreshold);
        JsonObject mult = new JsonObject();
        mult.addProperty("communityTag", h.multCommunityTag);
        mult.addProperty("namespace", h.multNamespace);
        mult.addProperty("suffix", h.multSuffix);
        mult.addProperty("keyword", h.multKeyword);
        mult.addProperty("archetype", h.multArchetype);
        mult.addProperty("recipeInheritance", h.multRecipeInheritance);
        mult.addProperty("namespacePeer", h.multNamespacePeer);
        mult.addProperty("secondarySuffix", h.multSecondarySuffix);
        mult.addProperty("namespacePeerAverageWeight", h.multNamespacePeerAverageWeight);
        scanner.add("multipliers", mult);
        root.add("scanner", scanner);

        return root;
    }

    static void patchScannerSpecScalars(MariesLibConfigHolder h) throws IOException {
        Path configDir = FMLPaths.CONFIGDIR.get().resolve(MarieCore.MOD_ID);
        Path file = configDir.resolve("scanner_spec.json");
        Files.createDirectories(configDir);
        JsonObject root;
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                root = GSON.fromJson(r, JsonObject.class);
            }
            if (root == null) {
                root = new JsonObject();
            }
        } else {
            root = new JsonObject();
        }

        JsonObject multObj = root.has("multipliers") && root.get("multipliers").isJsonObject()
                ? root.getAsJsonObject("multipliers")
                : new JsonObject();
        multObj.addProperty("community_tag", h.multCommunityTag);
        multObj.addProperty("namespace", h.multNamespace);
        multObj.addProperty("suffix", h.multSuffix);
        multObj.addProperty("keyword", h.multKeyword);
        multObj.addProperty("archetype", h.multArchetype);
        multObj.addProperty("recipe_inheritance", h.multRecipeInheritance);
        multObj.addProperty("namespace_peer", h.multNamespacePeer);
        multObj.addProperty("secondary_suffix", h.multSecondarySuffix);
        multObj.addProperty("namespace_peer_average_weight", h.multNamespacePeerAverageWeight);
        root.add("multipliers", multObj);

        try (Writer w = Files.newBufferedWriter(file)) {
            GSON.toJson(root, w);
        }
    }

    private static JsonObject obj(JsonObject parent, String key) {
        if (parent.has(key) && parent.get(key).isJsonObject()) {
            return parent.getAsJsonObject(key);
        }
        return null;
    }

    private static boolean bool(JsonObject o, String key, boolean fallback) {
        return o.has(key) ? o.get(key).getAsBoolean() : fallback;
    }

    private static float flt(JsonObject o, String key, float fallback) {
        return o.has(key) ? o.get(key).getAsFloat() : fallback;
    }

    private static double dbl(JsonObject o, String key, double fallback) {
        return o.has(key) ? o.get(key).getAsDouble() : fallback;
    }

    private static String sectionKey(String dottedKey) {
        int dot = dottedKey.indexOf('.');
        return dot >= 0 ? dottedKey.substring(0, dot) : dottedKey;
    }

    private static String leafKey(String dottedKey) {
        int dot = dottedKey.lastIndexOf('.');
        return dot >= 0 ? dottedKey.substring(dot + 1) : dottedKey;
    }
}
