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
    private static final String BAR_SCALE_SUFFIX = "#barScale";
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
        return p.load(panelId + ICON_SCALE_SUFFIX)
                .map(ComponentState::contentScale)
                .orElseGet(() -> textScale(p, panelId));
    }

    public static void setTextScale(PersistenceProvider p, String panelId, double value) {
        if (p.load(panelId + ICON_SCALE_SUFFIX).isEmpty()) {
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

    /** The bar size multiplier (bar length and thickness, and the value text at the bar's end); 1.0 until set. */
    public static double barScale(PersistenceProvider p, String panelId) {
        return p.load(panelId + BAR_SCALE_SUFFIX).map(ComponentState::contentScale).orElse(ComponentState.DEFAULT_CONTENT_SCALE);
    }

    public static void setBarScale(PersistenceProvider p, String panelId, double value) {
        p.save(panelId + BAR_SCALE_SUFFIX,
                new ComponentState(0, 0, 0, 0, false, false, false, 0, value, ComponentState.DEFAULT_PADDING_SCALE));
    }

    public static void setIconScale(PersistenceProvider p, String panelId, double value) {
        p.save(panelId + ICON_SCALE_SUFFIX,
                new ComponentState(0, 0, 0, 0, false, false, false, 0, value, ComponentState.DEFAULT_PADDING_SCALE));
    }
}
