package dev.marie.framework.ui.theme;

import dev.marie.framework.ui.Theme;
import dev.marie.framework.ui.ThemeKey;

import java.util.EnumMap;
import java.util.Map;

/** Built-in light palette. No prior consumer-mod analog exists yet — values chosen for contrast parity with {@link DarkTheme}. */
public final class LightTheme implements Theme {

    public static final Theme INSTANCE = new LightTheme();

    private static final Map<ThemeKey, Integer> COLORS = new EnumMap<>(ThemeKey.class);

    static {
        COLORS.put(ThemeKey.PANEL_BACKGROUND, 0xF0F0F0F0);
        COLORS.put(ThemeKey.BORDER, 0xFFB0B0B0);
        COLORS.put(ThemeKey.BORDER_HOVER, 0xFF3355FF);
        COLORS.put(ThemeKey.TEXT_PRIMARY, 0xFF202020);
        COLORS.put(ThemeKey.TEXT_SECONDARY, 0xFF555555);
        COLORS.put(ThemeKey.BAR_BACKGROUND, 0x99DDDDDD);
        COLORS.put(ThemeKey.BAR_FILL_POSITIVE, 0xFF2E9E2E);
        COLORS.put(ThemeKey.BAR_FILL_WARNING, 0xFFCC8400);
        COLORS.put(ThemeKey.BAR_FILL_CRITICAL, 0xFFCC3333);
        COLORS.put(ThemeKey.HANDLE_BACKGROUND, 0xCCD0D0D0);
        COLORS.put(ThemeKey.HANDLE_HOVER, 0xFF9AA6FF);
        COLORS.put(ThemeKey.HANDLE_ACTIVE, 0xFF2E9E2E);
        COLORS.put(ThemeKey.EDIT_OVERLAY, 0x99FFFFFF);
        COLORS.put(ThemeKey.EDIT_BANNER_TEXT, 0xFF101010);
        COLORS.put(ThemeKey.EDIT_BANNER_BACKGROUND, 0xCCFFFFFF);
        COLORS.put(ThemeKey.DASHED_PREVIEW, 0xFF1E7A63);
        COLORS.put(ThemeKey.SUBBOX_GLOW, 0xFFB8860B);
    }

    private LightTheme() {}

    @Override
    public int color(ThemeKey key) {
        Integer color = COLORS.get(key);
        return color != null ? color : 0xFFFF00FF;
    }
}
