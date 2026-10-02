package dev.marie.framework.compat;

import dev.marie.framework.api.ApiStatus;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Map;

/**
 * Defines a compatibility entry for mapping another mod's source items to
 * value keys used by the consuming mod.
 *
 * <p>Use the {@link Builder} to construct instances and register them
 * via the consuming mod's API.</p>
 */
@ApiStatus.Stable
public final class CompatDefinition {

    /**
     * Categorizes the type of mod being integrated with.
     */
    public enum CompatCategory {
        /** A mod that primarily adds new source items. */
        SOURCE_MOD,
        /** A mod focused on farming and crop mechanics. */
        FARMING_MOD,
        /** A mod that overhauls survival mechanics broadly. */
        SURVIVAL_OVERHAUL
    }

    /**
     * A source item's assigned value key and the amount it contributes when consumed.
     *
     * @param valueKey the value identifier this source item is classified under
     * @param amount   the amount contributed per use
     */
    public record SourceMapping(String valueKey, float amount) {
    }

    private final String modId;
    private final CompatCategory category;
    private final Map<ResourceLocation, SourceMapping> sourceMappings;

    private CompatDefinition(String modId, CompatCategory category, Map<ResourceLocation, SourceMapping> sourceMappings) {
        this.modId = modId;
        this.category = category;
        this.sourceMappings = Collections.unmodifiableMap(sourceMappings);
    }

    /**
     * Creates a new builder for a compatibility definition targeting the given mod.
     *
     * @param modId the mod identifier (e.g. "othermod")
     * @return a new {@link Builder} instance
     */
    public static Builder builder(String modId) {
        return new Builder(modId);
    }

    /**
     * Returns the target mod's identifier.
     *
     * @return the mod id string
     */
    public String getModId() {
        return modId;
    }

    /**
     * Returns the category classification of the target mod.
     *
     * @return the {@link CompatCategory} enum value
     */
    public CompatCategory getCategory() {
        return category;
    }

    /**
     * Returns an unmodifiable map of source item identifiers to their assigned
     * value keys.
     *
     * @return the source-to-value mapping
     * @deprecated amounts are lost through this accessor; use {@link #getSourceMappingDetails()}
     *             to also get each mapping's contribution amount.
     */
    @Deprecated(since = "1.2.0")
    public Map<ResourceLocation, String> getSourceMappings() {
        Map<ResourceLocation, String> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, SourceMapping> entry : sourceMappings.entrySet()) {
            result.put(entry.getKey(), entry.getValue().valueKey());
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Returns an unmodifiable map of source item identifiers to their assigned
     * value key and contribution amount.
     *
     * @return the source-to-mapping map
     */
    public Map<ResourceLocation, SourceMapping> getSourceMappingDetails() {
        return sourceMappings;
    }

    /**
     * Builder for constructing {@link CompatDefinition} instances.
     */
    public static final class Builder {

        private static final float DEFAULT_AMOUNT = 1.0f;

        private final String modId;
        private CompatCategory category = CompatCategory.SOURCE_MOD;
        private final Map<ResourceLocation, SourceMapping> sourceMappings = new java.util.LinkedHashMap<>();

        private Builder(String modId) {
            this.modId = modId;
        }

        /**
         * Sets the category classification for the target mod.
         *
         * @param category the {@link CompatCategory} value
         * @return this builder for chaining
         */
        public Builder category(CompatCategory category) {
            this.category = category;
            return this;
        }

        /**
         * Adds a source item to value key mapping with a default contribution
         * amount of {@value #DEFAULT_AMOUNT}.
         *
         * @param sourceId      the registry identifier of the source item
         * @param valueKey the value key to assign (e.g. "value_a")
         * @return this builder for chaining
         * @deprecated use {@link #addSourceMapping(ResourceLocation, String, float)} to specify
         *             the actual contribution amount instead of relying on the default.
         */
        @Deprecated(since = "1.2.0")
        public Builder addSourceMapping(ResourceLocation sourceId, String valueKey) {
            return addSourceMapping(sourceId, valueKey, DEFAULT_AMOUNT);
        }

        /**
         * Adds a source item to value key mapping, with the amount it contributes
         * per use.
         *
         * @param sourceId the registry identifier of the source item
         * @param valueKey the value key to assign (e.g. "value_a")
         * @param amount   the amount contributed per use
         * @return this builder for chaining
         */
        public Builder addSourceMapping(ResourceLocation sourceId, String valueKey, float amount) {
            this.sourceMappings.put(sourceId, new SourceMapping(valueKey, amount));
            return this;
        }

        /**
         * Adds all source-to-value mappings from the provided map, each using the
         * default contribution amount of {@value #DEFAULT_AMOUNT}.
         *
         * @param mappings a map of source identifiers to value keys
         * @return this builder for chaining
         * @deprecated use {@link #addSourceMapping(ResourceLocation, String, float)} to specify
         *             the actual contribution amount instead of relying on the default.
         */
        @Deprecated(since = "1.2.0")
        public Builder addAllSourceMappings(Map<ResourceLocation, String> mappings) {
            for (Map.Entry<ResourceLocation, String> entry : mappings.entrySet()) {
                addSourceMapping(entry.getKey(), entry.getValue(), DEFAULT_AMOUNT);
            }
            return this;
        }

        /**
         * Builds and returns the immutable {@link CompatDefinition}.
         *
         * @return the constructed definition
         * @throws IllegalStateException if required fields are missing or invalid
         */
        public CompatDefinition build() {
            if (modId == null || modId.trim().isEmpty()) {
                throw new IllegalStateException("CompatDefinition requires a non-empty modId.");
            }

            CompatCategory resolvedCategory = category != null ? category : CompatCategory.SOURCE_MOD;
            Map<ResourceLocation, SourceMapping> resolvedTagMappings =
                    sourceMappings != null ? sourceMappings : Collections.emptyMap();

            return new CompatDefinition(modId, resolvedCategory, resolvedTagMappings);
        }
    }
}
