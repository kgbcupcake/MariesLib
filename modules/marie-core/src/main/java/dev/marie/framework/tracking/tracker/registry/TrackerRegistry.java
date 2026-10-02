package dev.marie.framework.tracking.tracker.registry;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.core.IMarieConfig;
import dev.marie.framework.core.MarieContext;
import dev.marie.framework.registry.AbstractRegistry;
import dev.marie.framework.tracking.tracker.definition.TrackerDefinition;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Internal storage for tracker definitions registered via the public API.
 */
@ApiStatus.Internal
public final class TrackerRegistry {

    private static final class Core extends AbstractRegistry<ResourceLocation, TrackerDefinition> {
        Core() {
            super("TrackerRegistry");
        }
    }

    private static final Core INSTANCE = new Core();

    /** Tracks which mod registered each tracker id; see {@link dev.marie.framework.api.registry.ValueRegistry#ownerModId(String)}. */
    private static final Map<ResourceLocation, String> OWNERS = new ConcurrentHashMap<>();

    private TrackerRegistry() {}

    @ApiStatus.Internal
    public static void freezeInternal() {
        INSTANCE.freeze();
    }

    @ApiStatus.Internal
    public static void resetInternal() {
        INSTANCE.reset();
        OWNERS.clear();
    }

    /**
     * Temporarily reopens the registry for re-registration during
     * {@code MarieContext.reloadBroadcastHook()}. Must be paired with a subsequent
     * {@link #freezeInternal()} in a {@code finally} block.
     */
    @ApiStatus.Internal
    public static void unfreezeInternal() {
        INSTANCE.unfreeze();
    }

    public static boolean isFrozen() {
        return INSTANCE.isFrozen();
    }

    /**
     * Registers a tracker definition, enforcing the configured retention cap. Re-registering an
     * already-registered id replaces the existing definition rather than throwing — trackers are
     * expected to be re-registered on every reload (see {@code MarieContext.reloadBroadcastHook()}).
     *
     * @throws IllegalArgumentException if {@code definition} is null, retention is less than 1,
     *                                   or retention exceeds {@code IMarieConfig#trackerMaxRetention()}
     */
    public static void register(TrackerDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("definition cannot be null");
        }
        int retention = definition.getRetention();
        int maxRetention = IMarieConfig.get().trackerMaxRetention();
        if (retention < 1) {
            throw new IllegalArgumentException(
                    "TrackerDefinition '" + definition.getId() + "': retention must be >= 1, got " + retention);
        }
        if (retention > maxRetention) {
            throw new IllegalArgumentException(
                    "TrackerDefinition '" + definition.getId() + "': retention " + retention +
                    " exceeds trackerMaxRetention " + maxRetention);
        }
        INSTANCE.upsert(definition.getId(), definition);
        if (MarieContext.isRegistered()) {
            OWNERS.put(definition.getId(), MarieContext.get().modId());
        }
    }

    @Nullable
    public static TrackerDefinition get(ResourceLocation id) {
        return INSTANCE.get(id);
    }

    public static List<TrackerDefinition> getAll() {
        return INSTANCE.values();
    }

    /**
     * Returns the modId that registered {@code trackerId}, or {@code null} if unknown or
     * registered before any {@link MarieContext} was attached.
     */
    @Nullable
    @ApiStatus.Internal
    public static String ownerModId(ResourceLocation trackerId) {
        return OWNERS.get(trackerId);
    }
}
