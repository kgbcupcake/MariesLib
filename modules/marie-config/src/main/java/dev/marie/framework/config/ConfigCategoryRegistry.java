package dev.marie.framework.config;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.registry.AbstractRegistry;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Internal storage for {@link ConfigCategory}s registered via {@link ConfigAPI}. Same shape as
 * {@link ConfigRegistry}; kept as a separate registry since categories and values have
 * independent lifecycles — a category can exist with no values filed under it yet.
 */
@ApiStatus.Internal
public final class ConfigCategoryRegistry {

    private static final class Core extends AbstractRegistry<String, ConfigCategory> {
        Core() {
            super("ConfigCategoryRegistry");
        }
    }

    private static final Core INSTANCE = new Core();

    private ConfigCategoryRegistry() {}

    @ApiStatus.Internal
    public static void freezeInternal() {
        INSTANCE.freeze();
    }

    @ApiStatus.Internal
    public static void resetInternal() {
        INSTANCE.reset();
    }

    /**
     * Registers a config category.
     *
     * @param category the category to register
     * @throws IllegalStateException    if the registry is frozen or a category with the same id already exists
     * @throws IllegalArgumentException if {@code category} is null
     */
    public static void register(ConfigCategory category) {
        if (category == null) {
            throw new IllegalArgumentException("category cannot be null");
        }
        INSTANCE.register(category.id(), category);
    }

    /**
     * Returns a registered category by id, or {@code null} if not found.
     *
     * @param id the category's identifier
     * @return the category, or {@code null}
     */
    @Nullable
    public static ConfigCategory get(String id) {
        return INSTANCE.get(id);
    }

    /**
     * Returns all registered categories.
     *
     * @return an unmodifiable list of all categories
     */
    public static List<ConfigCategory> getAll() {
        return INSTANCE.values();
    }
}
