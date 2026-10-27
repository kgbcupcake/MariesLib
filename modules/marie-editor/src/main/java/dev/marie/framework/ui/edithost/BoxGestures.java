package dev.marie.framework.ui.edithost;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.api.EditCapabilities;
import dev.marie.framework.ui.api.EditGesture;
import dev.marie.framework.ui.api.EditableModule;
import dev.marie.framework.ui.api.MarieModuleSettings;
import dev.marie.framework.ui.drag.DraggableResizable;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Whole-box move and edge/corner resize: one lazily created {@link DraggableResizable} per module id,
 * the gesture in progress, its live preview bounds, and (for a left/top resize) the content offsets to
 * shift so the module's content stays where it was on screen.
 */
@ApiStatus.Internal
final class BoxGestures {

    private final Map<String, DraggableResizable> drags = new HashMap<>();
    private final Map<String, Bounds> previews = new HashMap<>();
    private final Map<String, Bounds> previewsView = Collections.unmodifiableMap(previews);
    private final BiConsumer<EditableModule, Bounds> onCommit;

    private EditableModule activeModule;
    private DraggableResizable activeDrag;
    private EditGesture gesture;
    /** Content offsets at the press for a left/top resize: text, icon, bar, header (x, y each); null otherwise. */
    private int[] anchor;

    BoxGestures(BiConsumer<EditableModule, Bounds> onCommit) {
        this.onCommit = onCommit;
    }

    /** Modules are often rebuilt every frame, so the commit goes to the instance that started the gesture, not the first one seen. */
    DraggableResizable dragFor(EditableModule module) {
        return drags.computeIfAbsent(module.id(), id ->
                new DraggableResizable(module, module.resizeConstraint(), (target, bounds) -> onCommit.accept(activeModule, bounds)));
    }

    DraggableResizable existingDrag(String id) {
        return drags.get(id);
    }

    Iterable<String> trackedIds() {
        return drags.keySet();
    }

    boolean isActive() {
        return activeDrag != null;
    }

    EditableModule activeModule() {
        return activeModule;
    }

    EditGesture gesture() {
        return gesture;
    }

    Bounds preview(String id) {
        return previews.get(id);
    }

    Map<String, Bounds> previews() {
        return previewsView;
    }

    boolean tryStart(EditableModule module, Bounds bounds, int mx, int my, PersistenceProvider st) {
        EditCapabilities caps = module.editCapabilities();
        DraggableResizable drag = dragFor(module);
        if (!drag.mouseClicked(mx, my, bounds, caps.canBoxMove(), caps.bottomRightCorner(), caps.bottomLeftCorner(), caps.resizeEdges())) {
            return false;
        }
        boolean resizing = drag.isResizing();
        boolean left = drag.isEdgeActive(DraggableResizable.Edge.LEFT) || drag.isBottomLeftCornerActive();
        boolean right = drag.isEdgeActive(DraggableResizable.Edge.RIGHT) || drag.isCornerActive();
        boolean top = drag.isEdgeActive(DraggableResizable.Edge.TOP);
        boolean bottom = drag.isEdgeActive(DraggableResizable.Edge.BOTTOM) || drag.isCornerActive() || drag.isBottomLeftCornerActive();
        activeModule = module;
        activeDrag = drag;
        gesture = new EditGesture(module.id(), resizing ? EditGesture.Kind.RESIZE : EditGesture.Kind.BOX_MOVE, bounds, null,
                left, right, top, bottom);
        previews.put(module.id(), bounds);
        anchor = resizing && caps.keepsContentOnEdgeResize() && (left || top) ? captureOffsets(st, module.id()) : null;
        return true;
    }

    /** Whether the running resize is shifting content offsets to keep the content where it was on screen. */
    boolean keepsContentInPlace() {
        return anchor != null;
    }

    void drag(int mx, int my, PersistenceProvider st) {
        Bounds preview = activeDrag.mouseDragged(mx, my);
        if (preview == null) {
            return;
        }
        previews.put(activeModule.id(), preview);
        if (anchor != null) {
            keepContentInPlace(st, activeModule.id(), preview);
        }
    }

    void release(int mx, int my, PersistenceProvider st) {
        // Commit runs inside mouseReleased via the drag's callback, while gesture() is still set.
        activeDrag.mouseReleased(mx, my);
        if (anchor != null) {
            String id = activeModule.id();
            MarieModuleSettings.commitTextOffset(st, id);
            MarieModuleSettings.commitIconOffset(st, id);
            MarieModuleSettings.commitBarOffset(st, id);
            MarieModuleSettings.commitHeaderOffset(st, id);
        }
        previews.remove(activeModule.id());
        anchor = null;
        activeModule = null;
        activeDrag = null;
        gesture = null;
    }

    private static int[] captureOffsets(PersistenceProvider st, String id) {
        return new int[]{
                MarieModuleSettings.textOffsetX(st, id), MarieModuleSettings.textOffsetY(st, id),
                MarieModuleSettings.iconOffsetX(st, id), MarieModuleSettings.iconOffsetY(st, id),
                MarieModuleSettings.barOffsetX(st, id), MarieModuleSettings.barOffsetY(st, id),
                MarieModuleSettings.headerOffsetX(st, id), MarieModuleSettings.headerOffsetY(st, id)};
    }

    private void keepContentInPlace(PersistenceProvider st, String id, Bounds live) {
        int dx = gesture.leftEdge() ? gesture.start().x() - live.x() : 0;
        int dy = gesture.topEdge() ? gesture.start().y() - live.y() : 0;
        MarieModuleSettings.setTextOffset(st, id, anchor[0] + dx, anchor[1] + dy);
        MarieModuleSettings.setIconOffset(st, id, anchor[2] + dx, anchor[3] + dy);
        MarieModuleSettings.setBarOffset(st, id, anchor[4] + dx, anchor[5] + dy);
        MarieModuleSettings.setHeaderOffset(st, id, anchor[6] + dx, anchor[7] + dy);
    }
}
