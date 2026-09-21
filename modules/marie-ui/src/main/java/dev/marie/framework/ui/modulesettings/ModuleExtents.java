package dev.marie.framework.ui.modulesettings;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Where a module last drew its text, icons and bars (screen coordinates, after the module's offsets), recorded by
 * {@link ModuleRenderContext} as the module renders. An edit screen reads it to outline just the part a move mode
 * is dragging, wherever the module happened to put it, instead of outlining the whole box. In memory only; a
 * module's record is cleared when it starts its next render, so it always describes the latest frame.
 */
@ApiStatus.Internal
public final class ModuleExtents {

    public enum Kind { TEXT, ICON, BAR }

    private static final Map<PersistenceProvider, Map<String, int[][]>> EXTENTS = new WeakHashMap<>();

    private ModuleExtents() {}

    /** Forgets everything recorded for {@code panelId}; called as the module begins a render. */
    static synchronized void begin(PersistenceProvider p, String panelId) {
        EXTENTS.computeIfAbsent(p, k -> new HashMap<>()).remove(panelId);
    }

    /** Grows {@code kind}'s extent to cover the rectangle. */
    static synchronized void add(PersistenceProvider p, String panelId, Kind kind, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int[][] boxes = EXTENTS.computeIfAbsent(p, k -> new HashMap<>()).computeIfAbsent(panelId, k -> new int[Kind.values().length][]);
        int[] box = boxes[kind.ordinal()];
        if (box == null) {
            boxes[kind.ordinal()] = new int[]{x, y, x + width, y + height};
        } else {
            box[0] = Math.min(box[0], x);
            box[1] = Math.min(box[1], y);
            box[2] = Math.max(box[2], x + width);
            box[3] = Math.max(box[3], y + height);
        }
    }

    /** The area {@code kind} covered in the module's latest render, or null if it drew none. */
    public static synchronized Bounds of(PersistenceProvider p, String panelId, Kind kind) {
        return toBounds(box(p, panelId, kind));
    }

    /** The area all of text, icons and bars covered together, or null if the module drew nothing. */
    public static synchronized Bounds all(PersistenceProvider p, String panelId) {
        int[] union = null;
        for (Kind kind : Kind.values()) {
            int[] box = box(p, panelId, kind);
            if (box == null) {
                continue;
            }
            union = union == null ? box.clone()
                    : new int[]{Math.min(union[0], box[0]), Math.min(union[1], box[1]), Math.max(union[2], box[2]), Math.max(union[3], box[3])};
        }
        return toBounds(union);
    }

    private static int[] box(PersistenceProvider p, String panelId, Kind kind) {
        Map<String, int[][]> byPanel = EXTENTS.get(p);
        int[][] boxes = byPanel == null ? null : byPanel.get(panelId);
        return boxes == null ? null : boxes[kind.ordinal()];
    }

    private static Bounds toBounds(int[] box) {
        return box == null ? null : new Bounds(box[0], box[1], box[2] - box[0], box[3] - box[1]);
    }
}
