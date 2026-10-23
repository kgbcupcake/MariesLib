package dev.marie.framework.config;

import dev.marie.framework.api.ApiStatus;

/**
 * The primitive shape a {@link ConfigValueDefinition} holds. Drives generic (de)serialization in
 * {@link ConfigSerialization} today, and will drive widget selection (toggle vs. slider vs. text
 * field) once the Dynamic Config Editor is built on top of this registry.
 */
@ApiStatus.Experimental
public enum ConfigValueType {
    BOOLEAN,
    INT,
    FLOAT,
    DOUBLE,
    STRING
}
