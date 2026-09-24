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

    /** Forgets the separate icon size, so icons follow the text size again. */
    public static void clearIconScale(PersistenceProvider p, String panelId) {
        p.remove(panelId + ICON_SCALE_SUFFIX);
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
        p.remove(panelId + BAR_SCALE_SUFFIX);
        p.remove(panelId + HEADER_SCALE_SUFFIX);
        p.remove(panelId + TEXT_BRIGHTNESS_SUFFIX);
        p.remove(panelId + ICON_BRIGHTNESS_SUFFIX);
    }
}
