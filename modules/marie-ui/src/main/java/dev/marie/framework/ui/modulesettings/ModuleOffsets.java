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
    private static final Map<PersistenceProvider, Map<String, int[]>> OFFSETS = new WeakHashMap<>();

    private ModuleOffsets() {}

    public static int barX(PersistenceProvider p, String panelId) {
        return offset(p, panelId, BAR_KEY)[0];
    }

    public static int barY(PersistenceProvider p, String panelId) {
        return offset(p, panelId, BAR_KEY)[1];
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

    /** Id whose {@link MoveFlags} flag is this module's "move bars" mode (the bare panel id is its "move text" mode). */
    public static String moveBarsFlagId(String panelId) {
        return panelId + ".bars";
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
