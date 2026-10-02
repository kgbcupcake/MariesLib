package dev.marie.framework.ui;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.ui.theme.DarkTheme;
import dev.marie.framework.ui.theme.LightTheme;

/** Resolves an ARGB color for a semantic {@link ThemeKey}. Components must never hardcode colors. */
@ApiStatus.Experimental
public interface Theme {

    Theme DARK = DarkTheme.INSTANCE;
    Theme LIGHT = LightTheme.INSTANCE;

    int color(ThemeKey key);
}
