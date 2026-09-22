package dev.marie.framework.ui.drag;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.drag.DraggableResizable;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Public facade and static registry for cross-component drag/resize snapping: lets any consumer
 * mod register its own draggable components' bounds so that other {@link DraggableResizable}
 * instances — including ones owned by a different mod entirely — can snap to their edges while
 * dragging or resizing.
 *
 * <p>Registration is bounds-only, mirroring {@link EditModeCoordinator}'s registry shape: a
 * {@link Supplier} that resolves one target's current {@link Bounds}, evaluated fresh on every
 * {@link #computeSnapLines} call rather than captured at registration time. This registry has no
 * lifecycle methods and no dependency on {@link EditModeCoordinator} or any other MarieLib
 * edit-mode state — register/unregister are plain static map operations, safe to call from any
 * mod's own client bootstrap at any time and in any order.
 *
 * <pre>{@code
 * // once, e.g. during client setup, for a component with its own DraggableResizable:
 * SnapRegistry.register("mymod.my_panel", () -> myPanel.currentBounds());
 *
 * // and whenever the component goes away for good:
 * SnapRegistry.unregister("mymod.my_panel");
 * }</pre>
 */
@ApiStatus.Experimental
public final class SnapRegistry {

    // id -> lazy bounds resolver; resolved fresh on every computeSnapLines call, never cached
    private static final Map<String, Supplier<Bounds>> ENTRIES = new LinkedHashMap<>();

    private SnapRegistry() {}

    /**
     * Registers {@code boundsSupplier} under {@code id} so other draggable components can snap to
     * its edges. {@code id} must be globally unique across every registrant in the game instance
     * (a good pattern is {@code "<modid>.<component>"}); registering an id that's already in use
     * silently replaces the existing entry.
     *
     * @param id globally unique id for this registrant
     * @param boundsSupplier resolves the target's current {@link Bounds}; invoked fresh on every
     *     {@link #computeSnapLines} call, never cached
     */
    public static void register(String id, Supplier<Bounds> boundsSupplier) {
        ENTRIES.put(id, boundsSupplier);
    }

    /** Unregisters {@code id}. No-op if not registered. */
    public static void unregister(String id) {
        ENTRIES.remove(id);
    }

    /**
     * Computes candidate snap lines from every registered entry except {@code excludeId} — pass a
     * dragging component's own id here so it never snaps to its own edges. Each entry's
     * {@link Bounds} contributes its left and right edges as X candidates and its top and bottom
     * edges as Y candidates, in the shape {@link DraggableResizable#setSnapTargets} takes.
     *
     * @param excludeId id to omit from the result, or {@code null} to include every registrant
     * @return the computed X and Y candidate lines
     */
    public static SnapLines computeSnapLines(String excludeId) {
        List<Integer> xLines = new ArrayList<>();
        List<Integer> yLines = new ArrayList<>();
        for (Map.Entry<String, Supplier<Bounds>> entry : ENTRIES.entrySet()) {
            if (entry.getKey().equals(excludeId)) {
                continue;
            }
            Bounds bounds = entry.getValue().get();
            xLines.add(bounds.x());
            xLines.add(bounds.x() + bounds.width());
            yLines.add(bounds.y());
            yLines.add(bounds.y() + bounds.height());
        }
        return new SnapLines(xLines, yLines);
    }

    /** X and Y candidate snap lines, in the shape {@link DraggableResizable#setSnapTargets} takes. */
    public record SnapLines(List<Integer> xLines, List<Integer> yLines) {}
}
