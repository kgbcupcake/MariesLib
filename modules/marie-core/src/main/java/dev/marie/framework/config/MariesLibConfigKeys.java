package dev.marie.framework.config;

/**
 * Stable Cloth Config / {@link LockRegistry} keys for MarieCore-owned settings.
 * Only scanner and debug keys remain — gameplay keys belong to consuming mods.
 */
public final class MariesLibConfigKeys {

    // Debug
    public static final String ENABLE_DEBUG_LOGGING = "debug.enableDebugLogging";

    // Mod-file scan
    public static final String ENABLE_MOD_SCAN = "modScan.enableModScan";

    // Scanner (context)
    public static final String SCANNER_CONFIDENCE_SPREAD_THRESHOLD = "scanner.confidenceSpreadThreshold";
    public static final String COMPOSITE_RATIO_THRESHOLD = "scanner.compositeRatioThreshold";
    public static final String SCANNER_ENABLE_RECIPE_INHERITANCE = "scanner.enableRecipeInheritance";
    public static final String MULTI_VALUE_INHERITANCE_THRESHOLD = "scanner.multiValueInheritanceThreshold";

    // Scanner spec multipliers
    public static final String MULT_COMMUNITY_TAG = "scanner.multipliers.communityTag";
    public static final String MULT_NAMESPACE = "scanner.multipliers.namespace";
    public static final String MULT_SUFFIX = "scanner.multipliers.suffix";
    public static final String MULT_KEYWORD = "scanner.multipliers.keyword";
    public static final String MULT_ARCHETYPE = "scanner.multipliers.archetype";
    public static final String MULT_RECIPE_INHERITANCE = "scanner.multipliers.recipeInheritance";
    public static final String MULT_NAMESPACE_PEER = "scanner.multipliers.namespacePeer";
    public static final String MULT_SECONDARY_SUFFIX = "scanner.multipliers.secondarySuffix";
    public static final String MULT_NAMESPACE_PEER_AVG = "scanner.multipliers.namespacePeerAverageWeight";

    private MariesLibConfigKeys() {}
}
