package dev.marie.framework.config;

import dev.marie.framework.api.ApiStatus;

import com.google.gson.JsonObject;

/**
 * Builds import/export JSON sections from {@link MariesLibConfigHolder}.
 * Only scanner and diagnostics settings are exported.
 */
@ApiStatus.Internal
public final class MariesLibConfigBridge {

    private MariesLibConfigBridge() {}

    public static JsonObject buildExportRoot() {
        MariesLibConfigHolder h = MariesLibConfigHolder.get();
        JsonObject root = new JsonObject();

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

        JsonObject debug = new JsonObject();
        debug.addProperty("enableDebugLogging", h.enableDebugLogging);
        root.add("debug", debug);

        return root;
    }

    public static void applyImport(JsonObject root) {
        MariesLibConfigHolder h = MariesLibConfigHolder.get();

        if (root.has("scanner") && root.get("scanner").isJsonObject()) {
            JsonObject scanner = root.getAsJsonObject("scanner");
            if (scanner.has("confidenceSpreadThreshold")) {
                h.scannerConfidenceSpreadThreshold = scanner.get("confidenceSpreadThreshold").getAsFloat();
            }
            if (scanner.has("compositeRatioThreshold")) {
                h.compositeRatioThreshold = scanner.get("compositeRatioThreshold").getAsFloat();
            }
            if (scanner.has("enableRecipeInheritance")) {
                h.scannerEnableRecipeInheritance = scanner.get("enableRecipeInheritance").getAsBoolean();
            }
            if (scanner.has("multiValueInheritanceThreshold")) {
                h.multiValueInheritanceThreshold = scanner.get("multiValueInheritanceThreshold").getAsDouble();
            }
            if (scanner.has("multipliers") && scanner.get("multipliers").isJsonObject()) {
                JsonObject mult = scanner.getAsJsonObject("multipliers");
                if (mult.has("communityTag")) h.multCommunityTag = mult.get("communityTag").getAsFloat();
                if (mult.has("namespace")) h.multNamespace = mult.get("namespace").getAsFloat();
                if (mult.has("suffix")) h.multSuffix = mult.get("suffix").getAsFloat();
                if (mult.has("keyword")) h.multKeyword = mult.get("keyword").getAsFloat();
                if (mult.has("archetype")) h.multArchetype = mult.get("archetype").getAsFloat();
                if (mult.has("recipeInheritance")) h.multRecipeInheritance = mult.get("recipeInheritance").getAsFloat();
                if (mult.has("namespacePeer")) h.multNamespacePeer = mult.get("namespacePeer").getAsFloat();
                if (mult.has("secondarySuffix")) h.multSecondarySuffix = mult.get("secondarySuffix").getAsFloat();
                if (mult.has("namespacePeerAverageWeight")) {
                    h.multNamespacePeerAverageWeight = mult.get("namespacePeerAverageWeight").getAsFloat();
                }
            }
        }

        if (root.has("debug") && root.get("debug").isJsonObject()) {
            JsonObject debug = root.getAsJsonObject("debug");
            if (debug.has("enableDebugLogging")) {
                h.enableDebugLogging = debug.get("enableDebugLogging").getAsBoolean();
            }
        }

        MariesLibConfigIO.save();
    }
}
