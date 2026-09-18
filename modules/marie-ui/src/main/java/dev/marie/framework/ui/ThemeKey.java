package dev.marie.framework.ui;

/** Semantic color slots a {@link Theme} must resolve. Components ask for meaning, not RGB values. */
public enum ThemeKey {
    PANEL_BACKGROUND,
    BORDER,
    BORDER_HOVER,
    TEXT_PRIMARY,
    TEXT_SECONDARY,
    BAR_BACKGROUND,
    BAR_FILL_POSITIVE,
    BAR_FILL_WARNING,
    BAR_FILL_CRITICAL,
    HANDLE_BACKGROUND,
    HANDLE_HOVER,
    HANDLE_ACTIVE,
    EDIT_OVERLAY,
    EDIT_BANNER_TEXT,
    EDIT_BANNER_BACKGROUND,
    DASHED_PREVIEW,
    SUBBOX_GLOW
}
