package dev.marie.framework.util;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.core.MarieContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Small helpers for registry keys, tags, and value id strings.
 */
@ApiStatus.Stable
public final class MarieRegistryUtils {

    private MarieRegistryUtils() {}

    /** Turns a tag string into an item {@link TagKey}. */
    public static TagKey<Item> itemTagKey(String tagStr) {
        return TagKey.create(Registries.ITEM, ResourceLocation.parse(tagStr));
    }

    /** Gets a {@link ResourceLocation} key for an {@link ItemStack}. */
    public static ResourceLocation itemKey(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    /** Gets a {@link ResourceLocation} key for an {@link Item}. */
    public static ResourceLocation itemKey(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    /**
     * Validates a value key is registered, throws {@link IllegalArgumentException} if not.
     */
    public static void requireValueKey(String key, String context) {
        if (!MarieContext.isRegistered()) {
            throw new IllegalStateException("MarieContext not registered");
        }
        if (!MarieContext.get().valueKeys().contains(key)) {
            throw new IllegalArgumentException(
                    "Unknown value key '" + key + "' in " + context);
        }
    }

    /** Capitalizes the first letter of a string safely. */
    public static String capitalizeFirst(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
