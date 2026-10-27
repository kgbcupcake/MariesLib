package dev.marie.framework.color;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorScopeTest {

    @Test
    void normalizedPrefixIsWhatPrefixedScopesKeyColorsBy() {
        assertEquals("compact/", ColorScope.normalizePrefix("Compact/"));
        assertEquals("my_profile/", ColorScope.normalizePrefix("My Profile/"));
        assertEquals("", ColorScope.normalizePrefix(null));

        ColorKey key = ColorKey.of(ResourceLocation.fromNamespaceAndPath("mymod", "panel.bg"));
        assertEquals(ColorScope.prefixed("Compact/").scoped(key), ColorScope.prefixed("compact/").scoped(key));
        assertEquals("mymod:compact/panel.bg", ColorScope.prefixed("compact/").scoped(key).id().toString());
    }
}
