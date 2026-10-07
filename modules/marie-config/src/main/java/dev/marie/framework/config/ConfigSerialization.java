package dev.marie.framework.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import dev.marie.framework.api.ApiStatus;

/**
 * Generic export/import over every value in {@link ConfigRegistry}, driven by each definition's
 * {@link ConfigValueType} instead of a bespoke per-field JSON shape — generalizes what {@code
 * dev.marie.framework.config.MariesLibConfigBridge} (marie-core) hand-builds today for MarieLib's
 * own scanner/debug settings. One flat object, id to value; nesting/grouping by category is a
 * presentation concern for whatever reads this, not encoded here.
 */
@ApiStatus.Experimental
public final class ConfigSerialization {

    private ConfigSerialization() {}

    /**
     * Builds a flat {@code {id: value}} JSON object from every currently registered config
     * value's live value (via {@link ConfigValueDefinition#get()}).
     */
    public static JsonObject exportAll() {
        JsonObject root = new JsonObject();
        for (ConfigValueDefinition<?> def : ConfigRegistry.getAll()) {
            root.add(def.id(), toJson(def));
        }
        return root;
    }

    /**
     * Applies every matching entry in {@code json} onto the live registered value (via {@link
     * ConfigValueDefinition#set}). Ids present in {@code json} but not currently registered, and
     * registered values with no matching id in {@code json}, are silently skipped — this is a
     * best-effort merge, not a strict schema.
     */
    public static void importAll(JsonObject json) {
        for (ConfigValueDefinition<?> def : ConfigRegistry.getAll()) {
            if (json.has(def.id())) {
                applyJson(def, json.get(def.id()).getAsJsonPrimitive());
            }
        }
    }

    private static JsonPrimitive toJson(ConfigValueDefinition<?> def) {
        Object value = def.get();
        return switch (def.type()) {
            case BOOLEAN -> new JsonPrimitive((Boolean) value);
            case INT -> new JsonPrimitive((Integer) value);
            case FLOAT, DOUBLE -> new JsonPrimitive((Number) value);
            case STRING -> new JsonPrimitive((String) value);
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> void applyJson(ConfigValueDefinition<T> def, JsonPrimitive json) {
        T value = switch (def.type()) {
            case BOOLEAN -> (T) (Boolean) json.getAsBoolean();
            case INT -> (T) (Integer) json.getAsInt();
            case FLOAT -> (T) (Float) json.getAsFloat();
            case DOUBLE -> (T) (Double) json.getAsDouble();
            case STRING -> (T) json.getAsString();
        };
        def.set(value);
    }
}
