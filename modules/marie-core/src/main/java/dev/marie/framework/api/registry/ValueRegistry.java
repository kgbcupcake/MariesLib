package dev.marie.framework.api.registry;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.api.value.ValueDefinition;
import dev.marie.framework.core.MarieContext;
import dev.marie.framework.registry.AbstractRegistry;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Internal storage for value definitions registered via the public API.
 */
@ApiStatus.Internal
public final class ValueRegistry {

    private static final class Core extends AbstractRegistry<String, ValueDefinition> {
        Core() {
            super("ValueRegistry");
        }
    }

    private static final Core INSTANCE = new Core();

    /**
     * Tracks which mod registered each value key, captured from {@link MarieContext#get()} at the
     * moment of registration (mirroring the existing convention that a mod calls
     * {@code MarieAPI.registerValue} synchronously right after {@code MarieBootstrap.attach}, while
     * its own context is the currently active one). Backs {@link MarieContext#forValue(String)} so
     * gameplay hooks dispatch to the mod that actually owns a value, instead of whichever mod last
     * called {@code attach}.
     */
    private static final Map<String, String> OWNERS = new ConcurrentHashMap<>();

    private ValueRegistry() {}

    @ApiStatus.Internal
    public static void freezeInternal() {
        INSTANCE.freeze();
    }

    @ApiStatus.Internal
    public static void resetInternal() {
        INSTANCE.reset();
        OWNERS.clear();
    }

    public static boolean isFrozen() {
        return INSTANCE.isFrozen();
    }

    public static void register(ValueDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("definition cannot be null");
        }
        INSTANCE.register(definition.getId(), definition);
        if (MarieContext.isRegistered()) {
            OWNERS.put(definition.getId(), MarieContext.get().modId());
        }
    }

    /**
     * Returns the modId that registered {@code valueKey}, or {@code null} if the key is unknown or
     * was registered before any {@link MarieContext} was attached.
     */
    @Nullable
    @ApiStatus.Internal
    public static String ownerModId(String valueKey) {
        return OWNERS.get(valueKey);
    }

    @Nullable
    public static ValueDefinition get(String id) {
        return INSTANCE.get(id);
    }

    public static List<ValueDefinition> getAll() {
        return INSTANCE.values();
    }
}
