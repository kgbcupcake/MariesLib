package dev.marie.framework.config;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.registry.AbstractRegistry;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Internal storage for {@link ConfigValueDefinition}s registered via {@link ConfigAPI}. Structurally
 * parallel to {@code dev.marie.framework.api.registry.TrackerMilestoneRegistry} in marie-core —
 * same {@link AbstractRegistry}-backed, string-keyed, register/get/freeze/reset shape.
 *
 * <p>Config registration is treated like milestones/synergies in MariesLib's reload contract
 * (see {@code API.md}'s "Registration window" section), not like trackers/colors: it's static
 * structural metadata a mod declares once at init, not datapack-driven content, so it is not
 * expected to be wiped and manually re-registered on every {@code /reload} — {@link
 * #freezeInternal()}/{@link #resetInternal()} exist for MariesLib's own bootstrap lifecycle to
 * call, not for a consuming mod to call directly.
 */
@ApiStatus.Internal
public final class ConfigRegistry {

    private static final class Core extends AbstractRegistry<String, ConfigValueDefinition<?>> {
        Core() {
            super("ConfigRegistry");
        }
    }

    private static final Core INSTANCE = new Core();

    private ConfigRegistry() {}

    @ApiStatus.Internal
    public static void freezeInternal() {
        INSTANCE.freeze();
    }

    @ApiStatus.Internal
    public static void resetInternal() {
        INSTANCE.reset();
    }

    /**
     * Registers a config value definition.
     *
     * @param definition the value to register
     * @throws IllegalStateException    if the registry is frozen or a value with the same id already exists
     * @throws IllegalArgumentException if {@code definition} is null
     */
    public static void register(ConfigValueDefinition<?> definition) {
        if (definition == null) {
            throw new IllegalArgumentException("definition cannot be null");
        }
        INSTANCE.register(definition.id(), definition);
    }

    /**
     * Returns a registered config value by id, or {@code null} if not found.
     *
     * @param id the value's identifier
     * @return the value definition, or {@code null}
     */
    @Nullable
    public static ConfigValueDefinition<?> get(String id) {
        return INSTANCE.get(id);
    }

    /**
     * Returns all registered config values.
     *
     * @return an unmodifiable list of all value definitions
     */
    public static List<ConfigValueDefinition<?>> getAll() {
        return INSTANCE.values();
    }

    /**
     * Returns all config values filed under a given category.
     *
     * @param category the category to filter by
     * @return an unmodifiable list of matching value definitions
     */
    public static List<ConfigValueDefinition<?>> getForCategory(ConfigCategory category) {
        return INSTANCE.values().stream()
                .filter(v -> category.equals(v.category()))
                .toList();
    }

    /**
     * Returns all config values owned by a given mod.
     *
     * @param modId the mod id to filter by
     * @return an unmodifiable list of matching value definitions
     */
    public static List<ConfigValueDefinition<?>> getForMod(String modId) {
        return INSTANCE.values().stream()
                .filter(v -> modId.equals(v.modId()))
                .toList();
    }
}
