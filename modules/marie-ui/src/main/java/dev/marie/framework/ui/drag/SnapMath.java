package dev.marie.framework.ui.drag;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.List;

/** Snapping and parent-clamp math behind {@link DraggableResizable}. */
@ApiStatus.Internal
final class SnapMath {

    private SnapMath() {}

    /**
     * Snaps whichever of {@code rawPos}'s two edges (start, start+size) is nearer a candidate line,
     * within {@code threshold}; the other edge follows along at the fixed {@code size}. Falls back to
     * {@code rawPos} unsnapped if neither edge is close enough to anything.
     */
    static int snapPosition(int rawPos, int size, List<Integer> lines, int threshold) {
        int nearStart = nearestLine(rawPos, lines);
        int nearEnd = nearestLine(rawPos + size, lines);
        int startDist = nearStart == Integer.MIN_VALUE ? Integer.MAX_VALUE : Math.abs(rawPos - nearStart);
        int endDist = nearEnd == Integer.MIN_VALUE ? Integer.MAX_VALUE : Math.abs(rawPos + size - nearEnd);
        if (startDist > threshold && endDist > threshold) {
            return rawPos;
        }
        return startDist <= endDist ? nearStart : nearEnd - size;
    }

    /**
     * Snaps a resized extent measured from the fixed {@code anchor} edge: either its moving edge to
     * the nearest edge line, or the size itself to the nearest candidate size — whichever is closer,
     * within {@code threshold}. A tie goes to the size match. Unchanged if neither is close.
     */
    static int snapSize(int rawSize, int anchor, boolean growsPositive, List<Integer> edgeLines, List<Integer> sizes, int threshold) {
        int movingEdge = growsPositive ? anchor + rawSize : anchor - rawSize;
        int nearEdge = nearestLine(movingEdge, edgeLines);
        int edgeDist = nearEdge == Integer.MIN_VALUE ? Integer.MAX_VALUE : Math.abs(movingEdge - nearEdge);
        int nearSize = nearestLine(rawSize, sizes);
        int sizeDist = nearSize == Integer.MIN_VALUE ? Integer.MAX_VALUE : Math.abs(rawSize - nearSize);
        if (sizeDist <= threshold && sizeDist <= edgeDist) {
            return nearSize;
        }
        if (edgeDist <= threshold) {
            return Math.abs(nearEdge - anchor);
        }
        return rawSize;
    }

    /** The candidate line closest to {@code value}, or {@code Integer.MIN_VALUE} if {@code lines} is empty. */
    static int nearestLine(int value, List<Integer> lines) {
        int best = Integer.MIN_VALUE;
        int bestDist = Integer.MAX_VALUE;
        for (int line : lines) {
            int dist = Math.abs(value - line);
            if (dist < bestDist) {
                bestDist = dist;
                best = line;
            }
        }
        return best;
    }

    /**
     * Clamps {@code b}'s position so it stays fully inside {@code parent}, leaving its size untouched;
     * a no-op if {@code parent} is {@code null}. If {@code b} is wider/taller than the parent, it's
     * pinned to the parent's near edge (top-left) and allowed to overflow the far edge, rather than
     * producing an inverted clamp range.
     */
    static Bounds clampToParent(Bounds b, Bounds parent) {
        if (parent == null) {
            return b;
        }
        int minX = parent.x();
        int minY = parent.y();
        int maxX = Math.max(minX, parent.x() + parent.width() - b.width());
        int maxY = Math.max(minY, parent.y() + parent.height() - b.height());
        int clampedX = Math.min(Math.max(b.x(), minX), maxX);
        int clampedY = Math.min(Math.max(b.y(), minY), maxY);
        if (clampedX == b.x() && clampedY == b.y()) {
            return b;
        }
        return new Bounds(clampedX, clampedY, b.width(), b.height());
    }
}
