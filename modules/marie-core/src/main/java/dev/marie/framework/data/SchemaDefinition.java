package dev.marie.framework.data;

import dev.marie.framework.api.ApiStatus;

import java.util.List;

@ApiStatus.Experimental
public final class SchemaDefinition {

    private static final int VERSION = 1;
    private final String typeName;
    private final int version;
    private final List<SchemaField> fields;

    public SchemaDefinition(String typeName, int version, List<SchemaField> fields) {
        this.typeName = typeName;
        this.version = version;
        this.fields = List.copyOf(fields);
    }

    public String getTypeName() {
        return typeName;
    }

    public int getVersion() {
        return version;
    }

    public List<SchemaField> getFields() {
        return fields;
    }

    public static SchemaDefinition forValue() {
        return new SchemaDefinition(DatapackSchema.VALUES_DIR, VERSION, List.of(
                SchemaField.optional(DatapackSchema.KEY_SCHEMA_VERSION, SchemaType.INT, VERSION),
                SchemaField.required(DatapackSchema.KEY_DISPLAY_NAME, SchemaType.STRING),
                SchemaField.optional("color", SchemaType.INT, null),
                SchemaField.optional("default_decay_rate", SchemaType.FLOAT, null),
                SchemaField.optional("critical_threshold", SchemaType.FLOAT, null),
                SchemaField.optional("low_threshold", SchemaType.FLOAT, null),
                SchemaField.optional("excess_threshold", SchemaType.FLOAT, null),
                SchemaField.optional("amountScale", SchemaType.DOUBLE, 1.0)
        ));
    }

    public static SchemaDefinition forSourceClassification() {
        return new SchemaDefinition(DatapackSchema.SOURCE_CLASSIFICATIONS_DIR, VERSION, List.of(
                SchemaField.optional(DatapackSchema.KEY_SCHEMA_VERSION, SchemaType.INT, VERSION),
                SchemaField.optional(DatapackSchema.KEY_ITEM, SchemaType.RESOURCE_LOCATION, null),
                SchemaField.optional(DatapackSchema.KEY_TAG, SchemaType.STRING, null),
                SchemaField.required(DatapackSchema.KEY_VALUE_KEY, SchemaType.STRING),
                SchemaField.required(DatapackSchema.KEY_AMOUNT, SchemaType.FLOAT)
        ));
    }

    public static SchemaDefinition forFoodOverride() {
        return new SchemaDefinition(DatapackSchema.FOOD_OVERRIDES_DIR, VERSION, List.of(
                SchemaField.optional(DatapackSchema.KEY_SCHEMA_VERSION, SchemaType.INT, VERSION),
                SchemaField.required(DatapackSchema.KEY_ITEM, SchemaType.RESOURCE_LOCATION),
                SchemaField.optional(DatapackSchema.KEY_NUTRIENTS, SchemaType.OBJECT, null),
                SchemaField.optional(DatapackSchema.KEY_CALORIES, SchemaType.INT, 0),
                SchemaField.optional(DatapackSchema.KEY_ENABLED, SchemaType.BOOLEAN, true)
        ));
    }

    public static SchemaDefinition forEffect() {
        return new SchemaDefinition(DatapackSchema.EFFECTS_DIR, VERSION, List.of(
                SchemaField.optional(DatapackSchema.KEY_SCHEMA_VERSION, SchemaType.INT, VERSION),
                SchemaField.required(DatapackSchema.KEY_VALUE_KEY, SchemaType.STRING),
                SchemaField.required(DatapackSchema.KEY_THRESHOLD, SchemaType.FLOAT),
                SchemaField.required(DatapackSchema.KEY_THRESHOLD_TYPE, SchemaType.STRING),
                SchemaField.required(DatapackSchema.KEY_EFFECT_ID, SchemaType.RESOURCE_LOCATION),
                SchemaField.optional(DatapackSchema.KEY_AMPLIFIER, SchemaType.INT, 0),
                SchemaField.optional(DatapackSchema.KEY_DURATION, SchemaType.INT, 200)
        ));
    }

    public static SchemaDefinition forSynergy() {
        return new SchemaDefinition(DatapackSchema.SYNERGIES_DIR, VERSION, List.of(
                SchemaField.optional(DatapackSchema.KEY_SCHEMA_VERSION, SchemaType.INT, VERSION),
                SchemaField.required(DatapackSchema.KEY_VALUE_A_KEY, SchemaType.STRING),
                SchemaField.required(DatapackSchema.KEY_VALUE_A_CONDITION, SchemaType.STRING),
                SchemaField.required(DatapackSchema.KEY_VALUE_B_KEY, SchemaType.STRING),
                SchemaField.required(DatapackSchema.KEY_VALUE_B_CONDITION, SchemaType.STRING),
                SchemaField.optional(DatapackSchema.KEY_BONUS_EFFECT_ID, SchemaType.RESOURCE_LOCATION, null),
                SchemaField.optional(DatapackSchema.KEY_AMPLIFIER, SchemaType.INT, 0),
                SchemaField.optional(DatapackSchema.KEY_EFFECT_DURATION, SchemaType.INT, 200),
                SchemaField.optional(DatapackSchema.KEY_IS_PENALTY, SchemaType.BOOLEAN, false)
        ));
    }

    public static SchemaDefinition forSourcePairSynergy() {
        return new SchemaDefinition(DatapackSchema.SOURCE_SYNERGIES_DIR, VERSION, List.of(
                SchemaField.optional(DatapackSchema.KEY_SCHEMA_VERSION, SchemaType.INT, VERSION),
                SchemaField.required(DatapackSchema.KEY_SOURCE_A, SchemaType.RESOURCE_LOCATION),
                SchemaField.required(DatapackSchema.KEY_SOURCE_B, SchemaType.RESOURCE_LOCATION),
                SchemaField.optional(DatapackSchema.KEY_TIME_WINDOW_TICKS, SchemaType.INT, 100),
                SchemaField.required(DatapackSchema.KEY_BONUS_VALUE_KEY, SchemaType.STRING),
                SchemaField.required(DatapackSchema.KEY_BONUS_AMOUNT, SchemaType.FLOAT)
        ));
    }

    public static SchemaDefinition forMilestone() {
        return new SchemaDefinition(DatapackSchema.MILESTONES_DIR, VERSION, List.of(
                SchemaField.optional(DatapackSchema.KEY_SCHEMA_VERSION, SchemaType.INT, VERSION),
                SchemaField.required(DatapackSchema.KEY_VALUE_KEY, SchemaType.STRING),
                SchemaField.required(DatapackSchema.KEY_CUMULATIVE_GOAL, SchemaType.FLOAT),
                SchemaField.optional(DatapackSchema.KEY_REWARD_EFFECT_ID, SchemaType.RESOURCE_LOCATION, null),
                SchemaField.optional(DatapackSchema.KEY_AMPLIFIER, SchemaType.INT, 0),
                SchemaField.optional(DatapackSchema.KEY_REWARD_DURATION, SchemaType.INT, 200),
                SchemaField.optional(DatapackSchema.KEY_ADVANCEMENT_ID, SchemaType.RESOURCE_LOCATION, null)
        ));
    }

    public static SchemaDefinition forTrackerMilestone() {
        return new SchemaDefinition(DatapackSchema.TRACKER_MILESTONES_DIR, VERSION, List.of(
                SchemaField.optional(DatapackSchema.KEY_SCHEMA_VERSION, SchemaType.INT, VERSION),
                SchemaField.required(DatapackSchema.KEY_TRACKER_ID, SchemaType.RESOURCE_LOCATION),
                SchemaField.required(DatapackSchema.KEY_GOAL, SchemaType.FLOAT),
                SchemaField.required(DatapackSchema.KEY_SCOPE, SchemaType.STRING),
                SchemaField.optional(DatapackSchema.KEY_REWARD_EFFECT_ID, SchemaType.RESOURCE_LOCATION, null),
                SchemaField.optional(DatapackSchema.KEY_AMPLIFIER, SchemaType.INT, 0),
                SchemaField.optional(DatapackSchema.KEY_REWARD_DURATION, SchemaType.INT, 200),
                SchemaField.optional(DatapackSchema.KEY_ADVANCEMENT_ID, SchemaType.RESOURCE_LOCATION, null)
        ));
    }

    public static SchemaDefinition forTrackingProfile() {
        return new SchemaDefinition(DatapackSchema.TRACKING_PROFILES_DIR, VERSION, List.of(
                SchemaField.optional(DatapackSchema.KEY_SCHEMA_VERSION, SchemaType.INT, VERSION),
                SchemaField.required(DatapackSchema.KEY_DISPLAY_NAME, SchemaType.STRING),
                SchemaField.optional(DatapackSchema.KEY_DESCRIPTION, SchemaType.STRING, null),
                SchemaField.optional(DatapackSchema.KEY_CUSTOM_THRESHOLDS, SchemaType.OBJECT, null),
                SchemaField.optional(DatapackSchema.KEY_CUSTOM_DECAY_RATES, SchemaType.OBJECT, null),
                SchemaField.optional(DatapackSchema.KEY_BONUS_EFFECTS, SchemaType.ARRAY, null)
        ));
    }

    public static SchemaDefinition forCompat() {
        return new SchemaDefinition(DatapackSchema.COMPAT_DIR, VERSION, List.of(
                SchemaField.optional(DatapackSchema.KEY_SCHEMA_VERSION, SchemaType.INT, VERSION),
                SchemaField.required(DatapackSchema.KEY_MOD_ID, SchemaType.STRING),
                SchemaField.optional(DatapackSchema.KEY_CATEGORY, SchemaType.STRING, "SOURCE_MOD"),
                SchemaField.optional(DatapackSchema.KEY_MAPPINGS, SchemaType.OBJECT, null)
        ));
    }

    public static SchemaDefinition forSourceFamilies() {
        return new SchemaDefinition(DatapackSchema.SOURCE_FAMILIES_DIR, VERSION, List.of(
                SchemaField.optional(DatapackSchema.KEY_SCHEMA_VERSION, SchemaType.INT, VERSION),
                SchemaField.required(DatapackSchema.KEY_FAMILIES, SchemaType.OBJECT)
        ));
    }

    public static SchemaDefinition forModuleLocks() {
        return new SchemaDefinition(DatapackSchema.MODULE_LOCKS_DIR, VERSION, List.of(
                SchemaField.optional(DatapackSchema.KEY_SCHEMA_VERSION, SchemaType.INT, VERSION),
                SchemaField.optional(DatapackSchema.KEY_LOCKED, SchemaType.ARRAY, null),
                SchemaField.optional(DatapackSchema.KEY_SERVER_ONLY, SchemaType.ARRAY, null)
        ));
    }
}
