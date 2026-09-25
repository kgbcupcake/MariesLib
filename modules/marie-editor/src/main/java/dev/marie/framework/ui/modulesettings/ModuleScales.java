package dev.marie.framework.ui.modulesettings;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.component.ComponentState;

/**
 * A module's size multipliers, all in the module's own {@link ComponentState} store. Text size is its
 * {@link ComponentState#contentScale()} and padding its {@link ComponentState#paddingScale()}; icon
 * size lives under a sibling {@code <panelId>#iconScale} key (its {@code contentScale} field). While
 * that key has never been written, icons follow the text size, so a module that predates the split
 * looks unchanged; the first text-size edit pins the icon size at its current value, so from then on
 * the two move independently.
 */
@ApiStatus.Internal
public final class ModuleScales {

    private static final String ICON_SCALE_SUFFIX = "#iconScale";
    private static final String ICON_INNER_SCALE_SUFFIX = "#iconInnerScale";
    private static final String BAR_SCALE_SUFFIX = "#barScale";
    private static final String HEADER_SCALE_SUFFIX = "#headerScale";
    private static final String TEXT_BRIGHTNESS_SUFFIX = "#textBrightness";
    private static final String ICON_BRIGHTNESS_SUFFIX = "#iconBrightness";
    private static final ComponentState BLANK = new ComponentState(0, 0, 0, 0, false, false, false, 0);

    private ModuleScales() {}

    public static double textScale(PersistenceProvider p, String panelId) {
        return p.load(panelId).orElse(BLANK).contentScale();
    }

    public static double paddingScale(PersistenceProvider p, String panelId) {
        return p.load(panelId).orElse(BLANK).paddingScale();
    }

    /** The icon size multiplier; the text size until an icon size has been set. */
    public static double iconScale(PersistenceProvider p, String panelId) {
        return iconScale(p, panelId, true);
    }

    /**
     * Same, but {@code followText} false never falls back to the text size — unset resolves to the
     * plain default (100%) instead. For a module whose Sizes panel was built with {@link
     * dev.marie.framework.ui.api.StandardPanelBuilder#independentIconSize}: without this, a module
     * that previously had only one combined size slider (before an icon size was split out, or after
     * its Text size row is removed entirely in favor of some other slider) leaves icon size reading a
     * stale/leftover {@code contentScale} value the player can no longer see or intend as an icon size.
     */
    public static double iconScale(PersistenceProvider p, String panelId, boolean followText) {
        return followText ? iconScaleFollowingText(p, panelId) : independentIconScale(p, panelId);
    }

    private static double iconScaleFollowingText(PersistenceProvider p, String panelId) {
        return p.load(panelId + ICON_SCALE_SUFFIX)
                .map(ComponentState::contentScale)
                .orElseGet(() -> textScale(p, panelId));
    }

    /**
     * The icon size multiplier, exactly like {@link #barScale}/{@link #headerScale}: reads only its
     * own {@code #iconScale}-suffixed key, defaulting to 100% — no fallback branch, no shared code
     * with {@link #textScale}/{@link #iconScaleFollowingText} at all. The {@code followText}-true path
     * above stays a separate method rather than an {@code if} inside this one, so a module built with
     * {@link dev.marie.framework.ui.api.StandardPanelBuilder#independentIconSize} never executes a
     * single line in common with the text-following behavior other modules still rely on.
     */
    private static double independentIconScale(PersistenceProvider p, String panelId) {
        return p.load(panelId + ICON_SCALE_SUFFIX).map(ComponentState::contentScale).orElse(ComponentState.DEFAULT_CONTENT_SCALE);
    }

    public static void setTextScale(PersistenceProvider p, String panelId, double value) {
        setTextScale(p, panelId, value, true);
    }

    /** Same, but {@code pinIcon} false never auto-pins the icon size to the pre-edit text size — see {@link #iconScale(PersistenceProvider, String, boolean)}. */
    public static void setTextScale(PersistenceProvider p, String panelId, double value, boolean pinIcon) {
        if (pinIcon && p.load(panelId + ICON_SCALE_SUFFIX).isEmpty()) {
            setIconScale(p, panelId, textScale(p, panelId));
        }
        setContentScale(p, panelId, value);
    }

    /** Writes only the text size, leaving the icon size (or its follow-the-text default) alone — for modules with no separate icon size. */
    public static void setContentScale(PersistenceProvider p, String panelId, double value) {
        ComponentState base = p.load(panelId).orElse(BLANK);
        p.save(panelId, new ComponentState(base.x(), base.y(), base.width(), base.height(), base.collapsed(),
                base.widthManual(), base.heightManual(), base.leftMargin(), value, base.paddingScale()));
    }

    public static void setPaddingScale(PersistenceProvider p, String panelId, double value) {
        // Read fresh and touch only one field so position/size and the other scale are never clobbered.
        ComponentState base = p.load(panelId).orElse(BLANK);
        p.save(panelId, new ComponentState(base.x(), base.y(), base.width(), base.height(), base.collapsed(),
                base.widthManual(), base.heightManual(), base.leftMargin(), base.contentScale(), value));
    }

    /** Forgets the separate icon size, so icons follow the text size again. */
    public static void clearIconScale(PersistenceProvider p, String panelId) {
        p.remove(panelId + ICON_SCALE_SUFFIX);
    }

    /**
     * The icon-in-box size multiplier — how large the icon graphic itself draws relative to its own
     * icon box (e.g. {@code BarRowComponent}'s icon box), independent of {@link #iconScale}, which
     * still sizes the box. 1.0 (unchanged) until set — mirrors {@link #iconInnerOffsetX} being
     * independent of {@link #iconScale}'s box-moving counterpart.
     */
    public static double iconInnerScale(PersistenceProvider p, String panelId) {
        return p.load(panelId + ICON_INNER_SCALE_SUFFIX).map(ComponentState::contentScale).orElse(ComponentState.DEFAULT_CONTENT_SCALE);
    }

    public static void setIconInnerScale(PersistenceProvider p, String panelId, double value) {
        p.save(panelId + ICON_INNER_SCALE_SUFFIX,
                new ComponentState(0, 0, 0, 0, false, false, false, 0, value, ComponentState.DEFAULT_PADDING_SCALE));
    }

    /** Forgets the separate icon-in-box size, so the icon graphic draws at its box's own size again. */
    public static void clearIconInnerScale(PersistenceProvider p, String panelId) {
        p.remove(panelId + ICON_INNER_SCALE_SUFFIX);
    }

    /** The bar size multiplier (bar length and thickness, and the value text at the bar's end); 1.0 until set. */
    public static double barScale(PersistenceProvider p, String panelId) {
        return p.load(panelId + BAR_SCALE_SUFFIX).map(ComponentState::contentScale).orElse(ComponentState.DEFAULT_CONTENT_SCALE);
    }

    /** The header size multiplier (a module's title/header text, independent of its body text size); 1.0 until set. */
    public static double headerScale(PersistenceProvider p, String panelId) {
        return p.load(panelId + HEADER_SCALE_SUFFIX).map(ComponentState::contentScale).orElse(ComponentState.DEFAULT_CONTENT_SCALE);
    }

    /** Text/icon brightness a module keeps in its own store (1.0 = unchanged) — for modules whose brightness isn't a config value. */
    public static double textBrightness(PersistenceProvider p, String panelId) {
        return p.load(panelId + TEXT_BRIGHTNESS_SUFFIX).map(ComponentState::contentScale).orElse(1.0d);
    }

    public static double iconBrightness(PersistenceProvider p, String panelId) {
        return p.load(panelId + ICON_BRIGHTNESS_SUFFIX).map(ComponentState::contentScale).orElse(1.0d);
    }

    public static void setTextBrightness(PersistenceProvider p, String panelId, double value) {
        p.save(panelId + TEXT_BRIGHTNESS_SUFFIX,
                new ComponentState(0, 0, 0, 0, false, false, false, 0, value, ComponentState.DEFAULT_PADDING_SCALE));
    }

    public static void setIconBrightness(PersistenceProvider p, String panelId, double value) {
        p.save(panelId + ICON_BRIGHTNESS_SUFFIX,
                new ComponentState(0, 0, 0, 0, false, false, false, 0, value, ComponentState.DEFAULT_PADDING_SCALE));
    }

    public static void setBarScale(PersistenceProvider p, String panelId, double value) {
        p.save(panelId + BAR_SCALE_SUFFIX,
                new ComponentState(0, 0, 0, 0, false, false, false, 0, value, ComponentState.DEFAULT_PADDING_SCALE));
    }

    public static void setIconScale(PersistenceProvider p, String panelId, double value) {
        p.save(panelId + ICON_SCALE_SUFFIX,
                new ComponentState(0, 0, 0, 0, false, false, false, 0, value, ComponentState.DEFAULT_PADDING_SCALE));
    }

    public static void setHeaderScale(PersistenceProvider p, String panelId, double value) {
        p.save(panelId + HEADER_SCALE_SUFFIX,
                new ComponentState(0, 0, 0, 0, false, false, false, 0, value, ComponentState.DEFAULT_PADDING_SCALE));
    }

    /**
     * Clears every stored scale/brightness key for this module — icon size, bar size, header size,
     * text brightness, icon brightness — for a "reset this whole module" action. Deliberately does NOT
     * touch text size or padding: those live as fields on the module's own position/size {@link
     * ComponentState} record (keyed bare by {@code panelId}, no suffix), so a caller resetting those
     * too should clear that record itself rather than duplicate its layout here.
     */
    public static void resetSizesAndBrightness(PersistenceProvider p, String panelId) {
        p.remove(panelId + ICON_SCALE_SUFFIX);
        p.remove(panelId + ICON_INNER_SCALE_SUFFIX);
        p.remove(panelId + BAR_SCALE_SUFFIX);
        p.remove(panelId + HEADER_SCALE_SUFFIX);
        p.remove(panelId + TEXT_BRIGHTNESS_SUFFIX);
        p.remove(panelId + ICON_BRIGHTNESS_SUFFIX);
    }
}
