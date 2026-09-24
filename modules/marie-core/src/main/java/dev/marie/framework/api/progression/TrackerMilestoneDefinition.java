package dev.marie.framework.api.progression;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.api.registry.TrackerMilestoneRegistry;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * Defines a milestone that fires once when a player reaches a goal value on a generic
 * MarieLib tracker (see {@code MarieTracking}). Structurally parallel to
 * {@link MilestoneDefinition} but tracks tracker values instead of a consumer's own registered value keys, and
 * is fully decoupled from it — separate storage, separate events, no shared feature flag.
 *
 * <p>Use the {@link Builder} to construct instances and register them via
 * {@link TrackerMilestoneRegistry#register(TrackerMilestoneDefinition)}.</p>
 *
 * <p><b>Java-side registration example:</b></p>
 * <pre>{@code
 * TrackerMilestoneRegistry.register(TrackerMilestoneDefinition.builder("hundred_blocks_mined")
 *         .trackerId(ResourceLocation.fromNamespaceAndPath("mymod", "blocks_mined"))
 *         .goal(100f)
 *         .scope(MilestoneScope.LIFETIME)
 *         .rewardEffect(ResourceLocation.withDefaultNamespace("haste"))
 *         .rewardAmplifier(1)
 *         .rewardDuration(600)
 *         .advancement(ResourceLocation.fromNamespaceAndPath("mymod", "hundred_blocks_mined"))
 *         .build());
 * }</pre>
 *
 * <p><b>Equivalent datapack file</b>
 * ({@code data/mymod/<modid>/tracker_milestones/hundred_blocks_mined.json}):</p>
 * <pre>{@code
 * {
 *   "tracker_id": "mymod:blocks_mined",
 *   "goal": 100.0,
 *   "scope": "lifetime",
 *   "reward_effect_id": "minecraft:haste",
 *   "amplifier": 1,
 *   "reward_duration": 600,
 *   "advancement_id": "mymod:hundred_blocks_mined"
 * }
 * }</pre>
 */
@ApiStatus.Stable
public final class TrackerMilestoneDefinition {

    /**
     * Determines which accumulated value a {@link TrackerMilestoneDefinition} is compared
     * against when checking for completion.
     */
    @ApiStatus.Stable
    public enum MilestoneScope {
        /** Compares against the player's all-time cumulative total for the tracker. */
        LIFETIME,
        /** Compares against the tracker's current-period accumulator value. */
        CURRENT_PERIOD
    }

    private final String id;
    private final ResourceLocation trackerId;
    private final float goal;
    private final MilestoneScope scope;
    @Nullable
    private final ResourceLocation rewardEffectId;
    private final int rewardAmplifier;
    private final int rewardDuration;
    @Nullable
    private final ResourceLocation advancementId;

    private TrackerMilestoneDefinition(
            String id,
            ResourceLocation trackerId,
            float goal,
            MilestoneScope scope,
            @Nullable ResourceLocation rewardEffectId,
            int rewardAmplifier,
            int rewardDuration,
            @Nullable ResourceLocation advancementId
    ) {
        this.id = id;
        this.trackerId = trackerId;
        this.goal = goal;
        this.scope = scope;
        this.rewardEffectId = rewardEffectId;
        this.rewardAmplifier = rewardAmplifier;
        this.rewardDuration = rewardDuration;
        this.advancementId = advancementId;
    }

    /**
     * Creates a new builder for a tracker milestone.
     *
     * @param id the unique identifier for this milestone (e.g. "milestone_id")
     * @return a new {@link Builder} instance
     */
    @ApiStatus.Stable
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /**
     * Returns the unique identifier of this milestone.
     *
     * @return the milestone id string
     */
    @ApiStatus.Stable
    public String getId() {
        return id;
    }

    /**
     * Returns the tracker this milestone tracks.
     *
     * @return the tracker's {@link ResourceLocation}
     */
    @ApiStatus.Stable
    public ResourceLocation getTrackerId() {
        return trackerId;
    }

    /**
     * Returns the goal value required to trigger this milestone.
     *
     * @return the goal value
     */
    @ApiStatus.Stable
    public float getGoal() {
        return goal;
    }

    /**
     * Returns which accumulated value this milestone is checked against.
     *
     * @return the milestone scope
     */
    @ApiStatus.Stable
    public MilestoneScope getScope() {
        return scope;
    }

    /**
     * Returns the mob effect granted as a reward when the milestone triggers,
     * or {@code null} if no effect reward is configured.
     *
     * @return the reward effect's {@link ResourceLocation}, or {@code null}
     */
    @Nullable
    @ApiStatus.Stable
    public ResourceLocation getRewardEffectId() {
        return rewardEffectId;
    }

    /**
     * Returns the amplifier for the reward effect.
     *
     * @return the effect amplifier (0-indexed)
     */
    @ApiStatus.Stable
    public int getRewardAmplifier() {
        return rewardAmplifier;
    }

    /**
     * Returns the duration of the reward effect in game ticks.
     *
     * @return the reward duration in ticks
     */
    @ApiStatus.Stable
    public int getRewardDuration() {
        return rewardDuration;
    }

    /**
     * Returns the advancement to grant when this milestone triggers,
     * or {@code null} if no advancement is configured.
     *
     * @return the advancement's {@link ResourceLocation}, or {@code null}
     */
    @Nullable
    @ApiStatus.Stable
    public ResourceLocation getAdvancementId() {
        return advancementId;
    }

    /**
     * Builder for constructing {@link TrackerMilestoneDefinition} instances.
     */
    @ApiStatus.Stable
    public static final class Builder {

        private final String id;
        private ResourceLocation trackerId;
        private float goal;
        private MilestoneScope scope;
        @Nullable
        private ResourceLocation rewardEffectId;
        private int rewardAmplifier = 0;
        private int rewardDuration = 200;
        @Nullable
        private ResourceLocation advancementId;

        private Builder(String id) {
            this.id = id;
        }

        /**
         * Sets the tracker this milestone tracks.
         *
         * @param trackerId the registered tracker's {@link ResourceLocation}
         * @return this builder for chaining
         */
        @ApiStatus.Stable
        public Builder trackerId(ResourceLocation trackerId) {
            this.trackerId = trackerId;
            return this;
        }

        /**
         * Sets the goal value required to trigger this milestone.
         *
         * @param goal the goal value
         * @return this builder for chaining
         */
        @ApiStatus.Stable
        public Builder goal(float goal) {
            this.goal = goal;
            return this;
        }

        /**
         * Sets which accumulated value this milestone is checked against.
         *
         * @param scope the milestone scope
         * @return this builder for chaining
         */
        @ApiStatus.Stable
        public Builder scope(MilestoneScope scope) {
            this.scope = scope;
            return this;
        }

        /**
         * Sets the mob effect granted as a one-time reward.
         *
         * @param effectId the reward effect's {@link ResourceLocation}
         * @return this builder for chaining
         */
        @ApiStatus.Stable
        public Builder rewardEffect(ResourceLocation effectId) {
            this.rewardEffectId = effectId;
            return this;
        }

        /**
         * Sets the amplifier for the reward effect.
         *
         * @param amplifier the effect amplifier (0-indexed)
         * @return this builder for chaining
         */
        @ApiStatus.Stable
        public Builder rewardAmplifier(int amplifier) {
            this.rewardAmplifier = amplifier;
            return this;
        }

        /**
         * Sets the duration for the reward effect.
         *
         * @param duration the duration in game ticks
         * @return this builder for chaining
         */
        @ApiStatus.Stable
        public Builder rewardDuration(int duration) {
            this.rewardDuration = duration;
            return this;
        }

        /**
         * Sets an advancement to grant when the milestone triggers.
         *
         * @param advancementId the advancement's {@link ResourceLocation}
         * @return this builder for chaining
         */
        @ApiStatus.Stable
        public Builder advancement(ResourceLocation advancementId) {
            this.advancementId = advancementId;
            return this;
        }

        /**
         * Builds and returns the immutable {@link TrackerMilestoneDefinition}.
         *
         * @return the constructed definition
         * @throws IllegalStateException if required fields are missing or invalid
         */
        @ApiStatus.Stable
        public TrackerMilestoneDefinition build() {
            if (id == null) {
                throw new IllegalStateException("id is required");
            }
            if (trackerId == null) {
                throw new IllegalStateException("trackerId is required");
            }
            if (scope == null) {
                throw new IllegalStateException("scope is required");
            }
            return new TrackerMilestoneDefinition(
                    id,
                    trackerId,
                    goal,
                    scope,
                    rewardEffectId,
                    rewardAmplifier,
                    rewardDuration,
                    advancementId
            );
        }
    }
}
