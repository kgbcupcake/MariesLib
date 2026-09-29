package dev.marie.framework.data;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.core.IMarieConfig;

/**
 * Constants describing MarieLib datapack schema locations and keys.
 *
 * <p>All JSON files are loaded from {@code data/<namespace>/<modid>/}.</p>
 */
@ApiStatus.Experimental
public final class DatapackSchema {

    private DatapackSchema() {}

    /** Base directory under a datapack namespace — equals the consuming mod's mod id. */
    public static String root() {
        return IMarieConfig.get().modId();
    }

    /** Datapack-relative path: {@code config/colors.json}. */
    public static final String CONFIG_COLORS = "config/colors.json";
    /** Datapack-relative path: {@code config/locks.json}. */
    public static final String CONFIG_LOCKS = "config/locks.json";
    /** Datapack-relative path: {@code config/source_classifications.json}. */
    public static final String CONFIG_SOURCE_CLASSIFICATIONS = "config/source_classifications.json";
    /** @deprecated Use {@link #CONFIG_SOURCE_CLASSIFICATIONS}. */
    @Deprecated
    public static final String CONFIG_SOURCE_OVERRIDES = "config/source_overrides.json";
    /** @deprecated Use {@link #CONFIG_SOURCE_CLASSIFICATIONS}. */
    @Deprecated
    public static final String CONFIG_SOURCE_VALUES = "config/source_values.json";
    /** Optional integer key declaring datapack schema version. */
    public static final String KEY_SCHEMA_VERSION = "marie_schema_version";

    /** Path: {@code data/<namespace>/<modid>/values/<id>.json}. */
    public static final String VALUES_DIR = "values";
    /** Required string key for value display name. */
    public static final String KEY_DISPLAY_NAME = "display_name";

    /** Path: {@code data/<namespace>/<modid>/source_classifications/<id>.json}. */
    public static final String SOURCE_CLASSIFICATIONS_DIR = "source_classifications";
    /** Required string key for value target. */
    public static final String KEY_VALUE_KEY = "value_key";
    /** Required numeric key for value amount. */
    public static final String KEY_AMOUNT = "amount";
    /** Optional item id mapping key. */
    public static final String KEY_ITEM = "item";
    /** Optional item tag mapping key. */
    public static final String KEY_TAG = "tag";

    /** Path: {@code data/<namespace>/<modid>/food_overrides/<id>.json}. Directory form of the legacy flat {@code config/food_overrides.json}. */
    public static final String FOOD_OVERRIDES_DIR = "food_overrides";
    /** Optional object key mapping nutrient key -> delta value for a food override. */
    public static final String KEY_NUTRIENTS = "nutrients";
    /** Optional integer key for a food override's calorie value. Default 0. */
    public static final String KEY_CALORIES = "calories";
    /** Optional boolean key toggling a food override on/off. Default true. */
    public static final String KEY_ENABLED = "enabled";

    /** Path: {@code data/<namespace>/<modid>/effects/<id>.json}. */
    public static final String EFFECTS_DIR = "effects";
    /** Required numeric key for trigger threshold. */
    public static final String KEY_THRESHOLD = "threshold";
    /** Required enum key for threshold type (CRITICAL/LOW/EXCESS/BONUS). */
    public static final String KEY_THRESHOLD_TYPE = "threshold_type";
    /** Required effect id key. */
    public static final String KEY_EFFECT_ID = "effect_id";
    /** Optional integer key for effect amplifier. */
    public static final String KEY_AMPLIFIER = "amplifier";
    /** Optional integer key for effect duration in ticks. */
    public static final String KEY_DURATION = "duration";

    /** Path: {@code data/<namespace>/<modid>/synergies/<id>.json}. */
    public static final String SYNERGIES_DIR = "synergies";
    /** Required first value key. */
    public static final String KEY_VALUE_A_KEY = "value_a_key";
    /** Required first value condition key (HIGH/LOW/OPTIMAL). */
    public static final String KEY_VALUE_A_CONDITION = "value_a_condition";
    /** Required second value key. */
    public static final String KEY_VALUE_B_KEY = "value_b_key";
    /** Required second value condition key (HIGH/LOW/OPTIMAL). */
    public static final String KEY_VALUE_B_CONDITION = "value_b_condition";
    /** Optional synergy effect id key. */
    public static final String KEY_BONUS_EFFECT_ID = "bonus_effect_id";
    /** Optional synergy effect duration key. */
    public static final String KEY_EFFECT_DURATION = "effect_duration";
    /** Optional penalty flag key. */
    public static final String KEY_IS_PENALTY = "is_penalty";

    /** Path: {@code data/<namespace>/<modid>/source_synergies/<id>.json}. */
    public static final String SOURCE_SYNERGIES_DIR = "source_synergies";
    /** Required first item key. */
    public static final String KEY_SOURCE_A = "source_a";
    /** Required second item key. */
    public static final String KEY_SOURCE_B = "source_b";
    /** Optional combo time window key in ticks. */
    public static final String KEY_TIME_WINDOW_TICKS = "time_window_ticks";
    /** Required value key that receives combo bonus. */
    public static final String KEY_BONUS_VALUE_KEY = "bonus_value_key";
    /** Required numeric combo bonus amount key. */
    public static final String KEY_BONUS_AMOUNT = "bonus_amount";
    /** Optional multiplier applied to value gain while the synergy buff is active. Default 1.0. */
    public static final String KEY_VALUE_MODIFIER = "value_modifier";
    /** Optional duration in ticks for the value modifier buff. Default 0 (no buff). */
    public static final String KEY_MODIFIER_DURATION_TICKS = "modifier_duration_ticks";

    /** Path: {@code data/<namespace>/<modid>/milestones/<id>.json}. */
    public static final String MILESTONES_DIR = "milestones";
    /** Required cumulative value goal key. */
    public static final String KEY_CUMULATIVE_GOAL = "cumulative_goal";
    /** Optional milestone reward effect id key. */
    public static final String KEY_REWARD_EFFECT_ID = "reward_effect_id";
    /** Optional milestone reward duration key in ticks. */
    public static final String KEY_REWARD_DURATION = "reward_duration";
    /** Optional advancement id key. */
    public static final String KEY_ADVANCEMENT_ID = "advancement_id";

    /** Path: {@code data/<namespace>/<modid>/tracker_milestones/<id>.json}. */
    public static final String TRACKER_MILESTONES_DIR = "tracker_milestones";
    /** Required tracker id key. */
    public static final String KEY_TRACKER_ID = "tracker_id";
    /** Required numeric goal value key. */
    public static final String KEY_GOAL = "goal";
    /** Required scope key (LIFETIME/CURRENT_PERIOD). */
    public static final String KEY_SCOPE = "scope";

    /** Path: {@code data/<namespace>/<modid>/tracking_profiles/<id>.json}. */
    public static final String TRACKING_PROFILES_DIR = "tracking_profiles";
    /** Optional profile description key. */
    public static final String KEY_DESCRIPTION = "description";
    /** Optional object key containing value threshold overrides. */
    public static final String KEY_CUSTOM_THRESHOLDS = "custom_thresholds";
    /** Optional object key containing value decay overrides. */
    public static final String KEY_CUSTOM_DECAY_RATES = "custom_decay_rates";
    /** Optional array key of bonus effect ids. */
    public static final String KEY_BONUS_EFFECTS = "bonus_effects";

    /** Path: {@code data/<namespace>/<modid>/compat/<id>.json}. */
    public static final String COMPAT_DIR = "compat";
    /** Required target mod id key for compat entries. */
    public static final String KEY_MOD_ID = "mod_id";
    /** Optional compat category key (SOURCE_MOD/FARMING_MOD/SURVIVAL_OVERHAUL). */
    public static final String KEY_CATEGORY = "category";
    /** Optional object key mapping item ids to value keys. */
    public static final String KEY_MAPPINGS = "mappings";

    /** Path: {@code data/<namespace>/<modid>/source_families/<id>.json}. */
    public static final String SOURCE_FAMILIES_DIR = "source_families";
    /** Required object key mapping family -> keyword array. */
    public static final String KEY_FAMILIES = "families";

    /** Path: {@code data/<namespace>/<modid>/module_locks/<id>.json}. */
    public static final String MODULE_LOCKS_DIR = "module_locks";
    /** Optional array key of locked module keys. */
    public static final String KEY_LOCKED = "locked";
    /** Optional array key of server-only module keys. */
    public static final String KEY_SERVER_ONLY = "server_only";
}
