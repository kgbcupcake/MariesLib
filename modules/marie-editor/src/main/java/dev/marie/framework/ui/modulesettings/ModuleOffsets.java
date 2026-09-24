package dev.marie.framework.ui.modulesettings;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.component.ComponentState;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A module's "move bars" and "move icons" offsets — where its bars (and their value text) or its icons
 * sit relative to where they would otherwise be, each independent of the module's own text offset. Held
 * in memory so a per-frame render path never touches the {@link PersistenceProvider}; loaded lazily from
 * it, and written back only by {@link #commitBar}/{@link #commitIcon} when a drag ends.
 */
@ApiStatus.Internal
public final class ModuleOffsets {

    private static final String BAR_KEY = "#barOffset";
    private static final String ICON_KEY = "#iconOffset";
    private static final String ICON_INNER_KEY = "#iconInnerOffset";
    private static final String TEXT_KEY = "#textOffset";
    private static final String HEADER_KEY = "#headerOffset";
    private static final Map<PersistenceProvider, Map<String, int[]>> OFFSETS = new WeakHashMap<>();

    private ModuleOffsets() {}

    public static int barX(PersistenceProvider p, String panelId) {
        return offset(p, panelId, BAR_KEY)[0];
    }

    public static int barY(PersistenceProvider p, String panelId) {
        return offset(p, panelId, BAR_KEY)[1];
    }

    /** The text offset a module keeps here when it has no storage of its own for it (HUD boxes with their own text-offset key don't use this). */
    public static int textX(PersistenceProvider p, String panelId) {
        return offset(p, panelId, TEXT_KEY)[0];
    }

    public static int textY(PersistenceProvider p, String panelId) {
        return offset(p, panelId, TEXT_KEY)[1];
    }

    public static void setText(PersistenceProvider p, String panelId, int x, int y) {
        set(p, panelId, TEXT_KEY, x, y);
    }

    public static void commitText(PersistenceProvider p, String panelId) {
        commit(p, panelId, TEXT_KEY);
    }

    public static int iconX(PersistenceProvider p, String panelId) {
        return offset(p, panelId, ICON_KEY)[0];
    }

    public static int iconY(PersistenceProvider p, String panelId) {
        return offset(p, panelId, ICON_KEY)[1];
    }

    /** Updates the in-memory offset (live drag preview); nothing is written until {@link #commitBar}. */
    public static void setBar(PersistenceProvider p, String panelId, int x, int y) {
        set(p, panelId, BAR_KEY, x, y);
    }

    public static void setIcon(PersistenceProvider p, String panelId, int x, int y) {
        set(p, panelId, ICON_KEY, x, y);
    }

    public static void commitBar(PersistenceProvider p, String panelId) {
        commit(p, panelId, BAR_KEY);
    }

    public static void commitIcon(PersistenceProvider p, String panelId) {
        commit(p, panelId, ICON_KEY);
    }

    /** Where the icon itself sits relative to its icon box, independent of {@link #iconX}/{@link #iconY} (the box's own offset). */
    public static int iconInnerX(PersistenceProvider p, String panelId) {
        return offset(p, panelId, ICON_INNER_KEY)[0];
    }

    public static int iconInnerY(PersistenceProvider p, String panelId) {
        return offset(p, panelId, ICON_INNER_KEY)[1];
    }

    public static void setIconInner(PersistenceProvider p, String panelId, int x, int y) {
        set(p, panelId, ICON_INNER_KEY, x, y);
    }

    public static void commitIconInner(PersistenceProvider p, String panelId) {
        commit(p, panelId, ICON_INNER_KEY);
    }

    /** Id whose {@link MoveFlags} flag is this module's "move icon" (inside its box) mode. */
    public static String moveIconInnerFlagId(String panelId) {
        return panelId + ".iconInner";
    }

    /** Where a module's header (a title separate from its body text) sits relative to its default place. */
    public static int headerX(PersistenceProvider p, String panelId) {
        return offset(p, panelId, HEADER_KEY)[0];
    }

    public static int headerY(PersistenceProvider p, String panelId) {
        return offset(p, panelId, HEADER_KEY)[1];
    }

    public static void setHeader(PersistenceProvider p, String panelId, int x, int y) {
        set(p, panelId, HEADER_KEY, x, y);
    }

    public static void commitHeader(PersistenceProvider p, String panelId) {
        commit(p, panelId, HEADER_KEY);
    }

    /** Id whose {@link MoveFlags} flag is this module's "move header" mode. */
    public static String moveHeaderFlagId(String panelId) {
        return panelId + ".header";
    }

    /** Id whose {@link MoveFlags} flag is this module's "move bars" mode (the bare panel id is its "move text" mode). */
    public static String moveBarsFlagId(String panelId) {
        return panelId + ".bars";
    }

    /** Id whose {@link MoveFlags} flag is this module's "move all" mode (text, icons and bars together). */
    public static String moveAllFlagId(String panelId) {
        return panelId + ".all";
    }

    /** Id whose {@link MoveFlags} flag is this module's "move icons" mode. */
    public static String moveIconsFlagId(String panelId) {
        return panelId + ".icons";
    }

    private static void set(PersistenceProvider p, String panelId, String key, int x, int y) {
        int[] offset = offset(p, panelId, key);
        offset[0] = x;
        offset[1] = y;
    }

    private static void commit(PersistenceProvider p, String panelId, String key) {
        int[] offset = offset(p, panelId, key);
        p.save(panelId + key, new ComponentState(offset[0], offset[1], 0, 0, false, false, false, 0));
    }

    private static synchronized int[] offset(PersistenceProvider p, String panelId, String key) {
        return OFFSETS.computeIfAbsent(p, k -> new HashMap<>()).computeIfAbsent(panelId + key, id -> {
            int[] offset = new int[2];
            p.load(id).ifPresent(state -> {
                offset[0] = state.x();
                offset[1] = state.y();
            });
            return offset;
        });
    }
}
