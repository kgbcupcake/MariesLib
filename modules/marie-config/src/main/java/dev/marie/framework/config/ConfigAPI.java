package dev.marie.framework.config;

import dev.marie.framework.api.ApiStatus;

/**
 * Public entry point for registering config values with MariesLib's config registry — the
 * foundation for a future Dynamic Config Editor and generic Cloth Config generation. Lives here
 * rather than on marie-core's {@code MarieAPI} because this registry is owned by marie-config,
 * which depends on marie-core, not the other way around.
 *
 * <p>A mod registers its existing settings the same way it already registers nutrients, foods,
 * or tracker milestones — this does not replace wherever the mod actually stores the value
 * ({@code ModConfigSpec}, a plain field, etc.), it only publishes read/write access to it plus
 * display metadata.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * ConfigCategory advanced = ConfigAPI.registerConfigCategory(
 *         new ConfigCategory("mymod.advanced", "Advanced", 0));
 *
 * ConfigAPI.registerConfigValue(new ConfigValueDefinition<>(
 *         "mymod.enableDiminishingReturns",
 *         "mymod",
 *         advanced,
 *         ConfigValueType.BOOLEAN,
 *         true,
 *         MyModConfig.get()::enableDiminishingReturns,
 *         MyModConfig.get()::setEnableDiminishingReturns,
 *         "Diminishing Returns",
 *         "Turns off the diminishing-returns curve for all food when disabled."));
 * }</pre>
 */
@ApiStatus.Experimental
public final class ConfigAPI {

    private ConfigAPI() {}

    /**
     * Registers a config value.
     *
     * @param definition the value to register
     * @throws IllegalStateException    if the registry is frozen or a value with the same id already exists
     * @throws IllegalArgumentException if {@code definition} is null
     */
    public static void registerConfigValue(ConfigValueDefinition<?> definition) {
        ConfigRegistry.register(definition);
    }

    /**
     * Registers a config category. Registering a category is optional bookkeeping for display
     * grouping — {@link #registerConfigValue} does not require the category to have been
     * registered first, since a {@link ConfigCategory} is a plain value object a mod can also
     * just construct inline and reuse.
     *
     * @param category the category to register
     * @throws IllegalStateException    if the registry is frozen or a category with the same id already exists
     * @throws IllegalArgumentException if {@code category} is null
     */
    public static ConfigCategory registerConfigCategory(ConfigCategory category) {
        if (category == null) {
            throw new IllegalArgumentException("category cannot be null");
        }
        ConfigCategoryRegistry.register(category);
        return category;
    }
}
