package dev.marie.framework.color;

import dev.marie.framework.api.ApiStatus;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * A color profile: maps each {@link ColorKey} to the key this profile reads and writes instead, so a
 * second copy of the same screen can be colored independently. Activate one with {@link
 * MarieColors#withColorScope}; inside it, {@link MarieColors#resolveColor(ColorKey)} and color slots
 * built through MariesLib use the scoped key, falling back to the original definition's default.
 *
 * <pre>{@code
 * ColorScope compact = ColorScope.prefixed("compact/");
 * MarieColors.withColorScope(compact, () -> { drawMyScreen(); return null; });
 * }</pre>
 */
@ApiStatus.Experimental
public interface ColorScope {

    /** The unscoped profile: every key maps to itself. */
    ColorScope NONE = key -> key;

    /** The key this profile stores {@code key}'s value under; return {@code key} itself to share it. */
    ColorKey scoped(ColorKey key);

    /** Whether an unset scoped color falls back to the base key's customized value (true) or straight to its default (false). */
    default boolean inheritsBaseValue() {
        return false;
    }

    /** A profile that prefixes every key's path, e.g. {@code mymod:panel.bg} becomes {@code mymod:compact/panel.bg}. */
    static ColorScope prefixed(String pathPrefix) {
        String prefix = sanitize(pathPrefix);
        if (prefix.isEmpty()) {
            return NONE;
        }
        return key -> ColorKey.of(ResourceLocation.fromNamespaceAndPath(key.id().getNamespace(), prefix + key.id().getPath()));
    }

    private static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (char c : raw.toLowerCase(Locale.ROOT).toCharArray()) {
            out.append(ResourceLocation.validPathChar(c) ? c : '_');
        }
        return out.toString();
    }
}
