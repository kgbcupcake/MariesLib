package dev.marie.framework.ui.theme;

import dev.marie.framework.ui.Theme;
import dev.marie.framework.ui.ThemeKey;

import java.util.EnumMap;
import java.util.Map;

/** Built-in dark palette, carried over from Nourished's existing HUD/edit-mode colors. */
public final class DarkTheme implements Theme {

    public static final Theme INSTANCE = new DarkTheme();

    private static final Map<ThemeKey, Integer> COLORS = new EnumMap<>(ThemeKey.class);

    static {
        COLORS.put(ThemeKey.PANEL_BACKGROUND, 0xF0101010);
        COLORS.put(ThemeKey.BORDER, 0xFF3A3A3A);
        COLORS.put(ThemeKey.BORDER_HOVER, 0xFFFFFFAA);
        COLORS.put(ThemeKey.TEXT_PRIMARY, 0xFFE0E0E0);
        COLORS.put(ThemeKey.TEXT_SECONDARY, 0xFFAAAAAA);
        COLORS.put(ThemeKey.BAR_BACKGROUND, 0x99111111);
        COLORS.put(ThemeKey.BAR_FILL_POSITIVE, 0xFF55FF55);
        COLORS.put(ThemeKey.BAR_FILL_WARNING, 0xFFFFAA00);
        COLORS.put(ThemeKey.BAR_FILL_CRITICAL, 0xFFFF5555);
        COLORS.put(ThemeKey.HANDLE_BACKGROUND, 0xCC2A2A2A);
        COLORS.put(ThemeKey.HANDLE_HOVER, 0xFFEFEF7A);
        COLORS.put(ThemeKey.HANDLE_ACTIVE, 0xFF55FF55);
        COLORS.put(ThemeKey.EDIT_OVERLAY, 0x99000000);
        COLORS.put(ThemeKey.EDIT_BANNER_TEXT, 0xFFFFFFFF);
        COLORS.put(ThemeKey.EDIT_BANNER_BACKGROUND, 0xCC000000);
        COLORS.put(ThemeKey.DASHED_PREVIEW, 0xFF6CFFD0);
        COLORS.put(ThemeKey.SUBBOX_GLOW, 0xFFE8B84B);
    }

    private DarkTheme() {}

    @Override
    public int color(ThemeKey key) {
        Integer color = COLORS.get(key);
        return color != null ? color : 0xFFFF00FF;
    }
}
