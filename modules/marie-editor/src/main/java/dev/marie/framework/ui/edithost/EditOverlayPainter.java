package dev.marie.framework.ui.edithost;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.api.EditCapabilities;
import dev.marie.framework.ui.api.EditGesture;
import dev.marie.framework.ui.api.EditableModule;
import dev.marie.framework.ui.api.MarieModuleSettings;
import dev.marie.framework.ui.api.MoveDrag;
import dev.marie.framework.ui.drag.DraggableResizable;
import dev.marie.framework.ui.drag.DraggableResizable.Edge;
import dev.marie.framework.ui.geometry.Bounds;

/**
 * Draws a host's edit chrome over its modules: resize handles for what each module allows, or — while a
 * move mode is on — a dashed outline around just the part that mode drags; plus extra outline parts
 * and a size label next to whatever handle is being dragged.
 */
@ApiStatus.Internal
final class EditOverlayPainter {

    private final EditHostConfig config;

    EditOverlayPainter(EditHostConfig config) {
        this.config = config;
    }

    void paint(RenderContext context, EditableModule module, Bounds box, MoveDrag.Mode mode, DraggableResizable drag,
               EditGesture gesture, int mx, int my) {
        int outline = config.outlineColor().getAsInt();
        if (mode != null) {
            Bounds part = MarieModuleSettings.moveOutline(config.store(), module.id(), mode);
            dashed(context, box, part, outline);
        } else {
            if (config.outlineIdleBoxes()) {
                dashed(context, box, null, outline);
            }
            drawHandles(context, module.editCapabilities(), box, drag, mx, my);
        }
        for (Bounds extra : module.extraOutlineParts(mode)) {
            dashed(context, box, extra, outline);
        }
        if (config.sizeLabels() && gesture != null && gesture.kind() != EditGesture.Kind.CONTENT_MOVE
                && gesture.moduleId().equals(module.id())) {
            drawSizeLabel(context, drag, box);
        }
    }

    /**
     * The recorded part is where the content drew itself, not clipped to the box — so the outline is
     * clipped to the box exactly like the content, or a box dragged smaller than its content would
     * show the dashed line sticking out past its edge.
     */
    private static void dashed(RenderContext context, Bounds box, Bounds part, int color) {
        context.pushClip(box.x(), box.y(), box.width(), box.height());
        try {
            if (part == null) {
                context.drawDashedBorder(box.x() + 2, box.y() + 2, box.width() - 4, box.height() - 4, color);
            } else {
                context.drawDashedBorder(part.x() - 3, part.y() - 3, part.width() + 6, part.height() + 6, color);
            }
        } finally {
            context.popClip();
        }
    }

    private static void drawHandles(RenderContext context, EditCapabilities caps, Bounds box, DraggableResizable drag, int mx, int my) {
        if (caps.bottomRightCorner()) {
            Bounds handle = DraggableResizable.handleBounds(box);
            context.drawResizeHandle(handle.x(), handle.y(), drag != null && drag.isHandleHovered(mx, my, box), drag != null && drag.isCornerActive());
        }
        if (caps.bottomLeftCorner()) {
            Bounds handle = DraggableResizable.handleBoundsBottomLeft(box);
            context.drawResizeHandle(handle.x(), handle.y(), drag != null && drag.isHandleBottomLeftHovered(mx, my, box),
                    drag != null && drag.isBottomLeftCornerActive());
        }
        for (Edge edge : caps.resizeEdges()) {
            Bounds strip = DraggableResizable.edgeHandleBounds(box, edge);
            context.drawEdgeHandle(strip.x(), strip.y(), strip.width(), strip.height(), mx, my,
                    drag != null && drag.isEdgeHovered(mx, my, box, edge), drag != null && drag.isEdgeActive(edge));
        }
    }

    /** The live pixel size, next to whichever handle or edge drives the gesture (top-left for a plain move). */
    private void drawSizeLabel(RenderContext context, DraggableResizable drag, Bounds bounds) {
        String label = bounds.width() + " x " + bounds.height();
        int lx;
        int ly;
        Edge activeEdge = null;
        for (Edge edge : Edge.values()) {
            if (drag != null && drag.isEdgeActive(edge)) {
                activeEdge = edge;
            }
        }
        if (drag != null && drag.isCornerActive()) {
            Bounds handle = DraggableResizable.handleBounds(bounds);
            lx = handle.x() + DraggableResizable.RESIZE_HANDLE_SIZE + 4;
            ly = handle.y() + DraggableResizable.RESIZE_HANDLE_SIZE + 4;
        } else if (drag != null && drag.isBottomLeftCornerActive()) {
            Bounds handle = DraggableResizable.handleBoundsBottomLeft(bounds);
            lx = handle.x();
            ly = handle.y() + DraggableResizable.RESIZE_HANDLE_SIZE + 4;
        } else if (activeEdge == Edge.LEFT) {
            lx = bounds.x() - 40;
            ly = bounds.y() + bounds.height() / 2 - 4;
        } else if (activeEdge == Edge.RIGHT) {
            lx = bounds.x() + bounds.width() + 6;
            ly = bounds.y() + bounds.height() / 2 - 4;
        } else if (activeEdge == Edge.TOP) {
            lx = bounds.x() + bounds.width() / 2 - 20;
            ly = bounds.y() - 12;
        } else if (activeEdge == Edge.BOTTOM) {
            lx = bounds.x() + bounds.width() / 2 - 20;
            ly = bounds.y() + bounds.height() + 6;
        } else {
            lx = bounds.x() + 4;
            ly = bounds.y() - 12;
        }
        context.drawText(label, lx + 1, ly + 1, config.labelShadowColor().getAsInt(), 0.75f);
        context.drawText(label, lx, ly, config.labelColor().getAsInt(), 0.75f);
    }
}
