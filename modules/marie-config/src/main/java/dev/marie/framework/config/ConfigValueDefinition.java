package dev.marie.framework.config;

import dev.marie.framework.api.ApiStatus;

import javax.annotation.Nullable;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A single registered config value: identity/display metadata plus a getter/setter pair into
 * wherever the consuming mod actually stores it (a {@code ModConfigSpec} value, a plain field,
 * whatever it already uses) — mirrors how {@code MarieContext.Builder} wires gameplay settings
 * as {@code Supplier}/{@code Consumer} pairs rather than owning storage itself. The registry only
 * tracks metadata and forwards reads/writes; this is a foundation for a future Dynamic Config
 * Editor and generic export/import, not a replacement storage layer.
 *
 * @param id           fully qualified id, e.g. {@code "nourished.enableDiminishingReturns"}
 * @param modId        the owning mod's id
 * @param category     the {@link ConfigCategory} this value is filed under
 * @param type         the value's primitive shape, for generic serialization/widget selection
 * @param defaultValue the value used when nothing else has been set yet
 * @param getter       reads the current value from wherever the consuming mod stores it
 * @param setter       writes a new value back into that same storage
 * @param label        display label shown to the player
 * @param description  optional longer explanation, shown as a tooltip/subtitle
 * @param <T>          the value's Java type, consistent with {@code type}
 */
@ApiStatus.Experimental
public record ConfigValueDefinition<T>(
        String id,
        String modId,
        ConfigCategory category,
        ConfigValueType type,
        T defaultValue,
        Supplier<T> getter,
        Consumer<T> setter,
        String label,
        @Nullable String description
) {

    public ConfigValueDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id cannot be null or blank");
        }
        if (modId == null || modId.isBlank()) {
            throw new IllegalArgumentException("modId cannot be null or blank");
        }
        if (category == null) {
            throw new IllegalArgumentException("category cannot be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("type cannot be null");
        }
        if (getter == null) {
            throw new IllegalArgumentException("getter cannot be null");
        }
        if (setter == null) {
            throw new IllegalArgumentException("setter cannot be null");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label cannot be null or blank");
        }
    }

    /** Reads the current value via {@link #getter()}. */
    public T get() {
        return getter.get();
    }

    /** Writes {@code value} via {@link #setter()}. */
    public void set(T value) {
        setter.accept(value);
    }
}
