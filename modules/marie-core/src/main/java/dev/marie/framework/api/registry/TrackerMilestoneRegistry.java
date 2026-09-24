package dev.marie.framework.api.registry;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.api.progression.TrackerMilestoneDefinition;
import dev.marie.framework.registry.AbstractRegistry;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Internal storage for tracker milestone definitions registered via the public API.
 * Structurally parallel to {@link MilestoneRegistry} but keyed against generic MarieLib
 * trackers rather than a consumer's own registered value keys, and fully decoupled from it.
 */
public final class TrackerMilestoneRegistry {

    private static final class Core extends AbstractRegistry<String, TrackerMilestoneDefinition> {
        Core() {
            super("TrackerMilestoneRegistry");
        }
    }

    private static final Core INSTANCE = new Core();

    private TrackerMilestoneRegistry() {}

    @ApiStatus.Internal
    public static void freezeInternal() {
        INSTANCE.freeze();
    }

    @ApiStatus.Internal
    public static void resetInternal() {
        INSTANCE.reset();
    }

    /**
     * Registers a tracker milestone definition.
     *
     * <p><b>Java-side registration example:</b></p>
     * <pre>{@code
     * TrackerMilestoneRegistry.register(TrackerMilestoneDefinition.builder("hundred_blocks_mined")
     *         .trackerId(ResourceLocation.fromNamespaceAndPath("mymod", "blocks_mined"))
     *         .goal(100f)
     *         .scope(TrackerMilestoneDefinition.MilestoneScope.LIFETIME)
     *         .build());
     * }</pre>
     *
     * <p><b>Equivalent datapack file</b>
     * ({@code data/mymod/<modid>/tracker_milestones/hundred_blocks_mined.json}):</p>
     * <pre>{@code
     * {
     *   "tracker_id": "mymod:blocks_mined",
     *   "goal": 100.0,
     *   "scope": "lifetime"
     * }
     * }</pre>
     *
     * @param definition the milestone to register
     * @throws IllegalStateException    if the registry is frozen or a milestone with the same id already exists
     * @throws IllegalArgumentException if {@code definition} is null
     */
    public static void register(TrackerMilestoneDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("definition cannot be null");
        }
        INSTANCE.register(definition.getId(), definition);
    }

    /**
     * Returns a registered tracker milestone by id, or {@code null} if not found.
     *
     * @param id the milestone identifier
     * @return the milestone definition, or {@code null}
     */
    @Nullable
    public static TrackerMilestoneDefinition get(String id) {
        return INSTANCE.get(id);
    }

    /**
     * Returns all registered tracker milestones.
     *
     * @return an unmodifiable list of all milestone definitions
     */
    public static List<TrackerMilestoneDefinition> getAll() {
        return INSTANCE.values();
    }

    /**
     * Returns all milestones that track a specific tracker.
     *
     * @param trackerId the tracker id to filter by
     * @return an unmodifiable list of matching milestones
     */
    public static List<TrackerMilestoneDefinition> getForTracker(ResourceLocation trackerId) {
        return INSTANCE.values().stream()
                .filter(m -> trackerId.equals(m.getTrackerId()))
                .toList();
    }
}
