package dev.marie.framework.data;

import dev.marie.framework.api.ApiStatus;

@ApiStatus.Experimental
public record SchemaField(String name, SchemaType type, boolean required, Object defaultValue) {

    public static SchemaField required(String name, SchemaType type) {
        return new SchemaField(name, type, true, null);
    }

    public static SchemaField optional(String name, SchemaType type, Object defaultValue) {
        return new SchemaField(name, type, false, defaultValue);
    }
}
