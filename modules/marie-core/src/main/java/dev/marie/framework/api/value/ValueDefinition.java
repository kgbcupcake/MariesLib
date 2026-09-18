package dev.marie.framework.api.value;

import dev.marie.framework.api.ApiStatus;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Defines a custom value that can be registered with the MarieLib system.
 *
 * <p>Use the {@link Builder} to construct instances. Values registered via this
 * definition will participate in decay, threshold checks, and HUD rendering.</p>
 */
@ApiStatus.Stable
public final class ValueDefinition {

    private final String id;
    private final String displayName;
    private final int color;
    private final int tooltipColor;
    @Nullable
    private final Integer colorOverride;
    private final float defaultDecayRate;
    private final float criticalThreshold;
    private final float lowThreshold;
    private final float excessThreshold;
    @Nullable
    private final ValueRenderer customRenderer;
    private final boolean beneficial;
    private final double amountScale;
    @Nullable
    private final String icon;
    private final List<String> tags;

    private ValueDefinition(
            String id,
            String displayName,
            int color,
            int tooltipColor,
            @Nullable Integer colorOverride,
            float defaultDecayRate,
            float criticalThreshold,
            float lowThreshold,
            float excessThreshold,
            @Nullable ValueRenderer customRenderer,
            boolean beneficial,
            double amountScale,
            @Nullable String icon,
            List<String> tags
    ) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.tooltipColor = tooltipColor;
        this.colorOverride = colorOverride;
        this.defaultDecayRate = defaultDecayRate;
        this.criticalThreshold = criticalThreshold;
        this.lowThreshold = lowThreshold;
        this.excessThreshold = excessThreshold;
        this.customRenderer = customRenderer;
        this.beneficial = beneficial;
        this.amountScale = amountScale;
        this.icon = icon;
        this.tags = tags;
    }

    /**
     * Creates a new builder for a value with the given unique identifier.
     *
     * @param id the unique internal key for this value (e.g. "value_a")
     * @return a new {@link Builder} instance
     */
    @ApiStatus.Stable
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /**
     * Returns the unique internal identifier for this value.
     *
     * @return the value key string
     */
    @ApiStatus.Stable
    public String getId() {
        return id;
    }

    /**
     * Returns the human-readable display name for this value.
     *
     * @return the display name string
     */
    @ApiStatus.Stable
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the ARGB color used for rendering this value in the HUD.
     *
     * @return the color as a packed ARGB integer
     */
    @ApiStatus.Stable
    public int getColor() {
        return color;
    }

    /**
     * Returns the ARGB color used for rendering this value's tooltip.
     *
     * @return the tooltip color as a packed ARGB integer
     */
    @ApiStatus.Stable
    public int getTooltipColor() {
        return tooltipColor;
    }

    @Nullable
    @ApiStatus.Stable
    public Integer getColorOverride() {
        return colorOverride;
    }

    /**
     * Returns the default rate at which this value decays per tick.
     *
     * @return the decay rate per tick (e.g. 0.001 means 0.1% per tick)
     */
    @ApiStatus.Stable
    public float getDefaultDecayRate() {
        return defaultDecayRate;
    }

    /**
     * Returns the threshold below which the value level is considered critical.
     *
     * @return the critical threshold as a normalized float (0.0 to 1.0)
     */
    @ApiStatus.Stable
    public float getCriticalThreshold() {
        return criticalThreshold;
    }

    /**
     * Returns the threshold below which the value level is considered low.
     *
     * @return the low threshold as a normalized float (0.0 to 1.0)
     */
    @ApiStatus.Stable
    public float getLowThreshold() {
        return lowThreshold;
    }

    /**
     * Returns the threshold above which the value level is considered excessive.
     *
     * @return the excess threshold as a normalized float (0.0 to 1.0)
     */
    @ApiStatus.Stable
    public float getExcessThreshold() {
        return excessThreshold;
    }

    /**
     * Returns the optional custom renderer for this value, or {@code null}
     * if the default renderer should be used.
     *
     * @return the custom {@link ValueRenderer}, or {@code null}
     */
    @Nullable
    @ApiStatus.Stable
    public ValueRenderer getCustomRenderer() {
        return customRenderer;
    }

    /**
     * Returns whether this value is beneficial (high values are good).
     * When false, high values are treated as bad.
     *
     * @return {@code true} if high values are good, {@code false} if high values are bad
     */
    @ApiStatus.Stable
    public boolean isBeneficial() {
        return beneficial;
    }

    /**
     * Returns how many raw source amount units equal a full bar (1.0).
     *
     * @return the amount scale (default 1.0)
     */
    @ApiStatus.Stable
    public double getAmountScale() {
        return amountScale;
    }

    @Nullable
    @ApiStatus.Stable
    public String getIcon() {
        return icon;
    }

    @ApiStatus.Stable
    public List<String> getTags() {
        return tags;
    }

    /**
     * Builder for constructing {@link ValueDefinition} instances.
     */
    @ApiStatus.Stable
    public static final class Builder {

        private final String id;
        private String displayName = "";
        private int color = 0xFFFFFFFF;
        private int tooltipColor = 0xFFFFFFFF;
        private Integer colorOverride = null;
        private float defaultDecayRate = 0.001f;
        private float criticalThreshold = 0.1f;
        private float lowThreshold = 0.3f;
        private float excessThreshold = 0.9f;
        @Nullable
        private ValueRenderer customRenderer;
        private boolean beneficial = true;
        private double amountScale = 1.0;
        @Nullable
        private String icon = null;
        private List<String> tags = List.of();

        private Builder(String id) {
            this.id = id;
        }

        /**
         * Sets the human-readable display name for this value.
         *
         * @param displayName the display name (e.g. "Value A")
         * @return this builder for chaining
         */
        @ApiStatus.Stable
        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        /**
         * Sets the ARGB color used for HUD rendering.
         *
         * @param color the packed ARGB color integer
         * @return this builder for chaining
         */
        @ApiStatus.Stable
        public Builder color(int color) {
            this.color = color;
            this.colorOverride = color;
            return this;
        }

        /**
         * Sets the ARGB color used for tooltip rendering.
         *
         * @param tooltipColor the packed ARGB color integer
         * @return this builder for chaining
         */
        @ApiStatus.Stable
        public Builder tooltipColor(int tooltipColor) {
            this.tooltipColor = tooltipColor;
            return this;
        }

        /**
         * Sets the default decay rate per tick for this value.
         *
         * @param rate the decay rate (must be non-negative)
         * @return this builder for chaining
         */
        @ApiStatus.Stable
        public Builder defaultDecayRate(float rate) {
            this.defaultDecayRate = rate;
            return this;
        }

        /**
         * Sets the threshold below which the value is considered critically low.
         *
         * @param threshold the critical threshold (0.0 to 1.0)
         * @return this builder for chaining
         */
        @ApiStatus.Stable
        public Builder criticalThreshold(float threshold) {
            this.criticalThreshold = threshold;
            return this;
        }

        /**
         * Sets the threshold below which the value is considered low.
         *
         * @param threshold the low threshold (0.0 to 1.0)
         * @return this builder for chaining
         */
        @ApiStatus.Stable
        public Builder lowThreshold(float threshold) {
            this.lowThreshold = threshold;
            return this;
        }

        /**
         * Sets the threshold above which the value is considered excessive.
         *
         * @param threshold the excess threshold (0.0 to 1.0)
         * @return this builder for chaining
         */
        @ApiStatus.Stable
        public Builder excessThreshold(float threshold) {
            this.excessThreshold = threshold;
            return this;
        }

        /**
         * Sets an optional custom renderer to replace the default HUD visualization.
         *
         * @param renderer the custom {@link ValueRenderer} implementation, or {@code null} for default
         * @return this builder for chaining
         */
        @ApiStatus.Stable
        public Builder customRenderer(@Nullable ValueRenderer renderer) {
            this.customRenderer = renderer;
            return this;
        }

        /**
         * Sets whether this value is beneficial (high values are good).
         * When false, high values are treated as bad.
         * Default: true.
         *
         * @param beneficial {@code true} if high values are good, {@code false} if high values are bad
         * @return this builder for chaining
         */
        @ApiStatus.Stable
        public Builder beneficial(boolean beneficial) {
            this.beneficial = beneficial;
            return this;
        }

        /** How many raw source amount units equal a full bar (1.0). Default 1.0. */
        @ApiStatus.Stable
        public Builder amountScale(double scale) {
            this.amountScale = scale;
            return this;
        }

        /** Sets the icon for this value. Default: null. */
        @ApiStatus.Stable
        public Builder icon(@Nullable String icon) {
            this.icon = icon;
            return this;
        }

        /** Sets the food-tag list for this value, defensively copied. Default: empty list. */
        @ApiStatus.Stable
        public Builder tags(List<String> tags) {
            this.tags = List.copyOf(tags);
            return this;
        }

        /**
         * Builds and returns the immutable {@link ValueDefinition}.
         *
         * @return the constructed definition
         * @throws IllegalStateException if required fields are missing or invalid
         */
        @ApiStatus.Stable
        public ValueDefinition build() {
            if (id == null) {
                throw new IllegalStateException("id is required");
            }
            if (displayName == null) {
                throw new IllegalStateException("displayName is required");
            }
            if (amountScale <= 0 || !Double.isFinite(amountScale)) {
                throw new IllegalStateException("amountScale must be positive and finite");
            }
            return new ValueDefinition(
                    id,
                    displayName,
                    color,
                    tooltipColor,
                    colorOverride,
                    defaultDecayRate,
                    criticalThreshold,
                    lowThreshold,
                    excessThreshold,
                    customRenderer,
                    beneficial,
                    amountScale,
                    icon,
                    tags
            );
        }
    }
}
