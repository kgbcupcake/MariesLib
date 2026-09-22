package dev.marie.framework.ui.scaleconfig;

import dev.marie.framework.ui.geometry.Anchor;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Where a {@link ScaleConfigPanel}'s collapsed-tab stack sits on screen for its {@link Anchor}, and the
 * collision-avoidance between several panels anchored to the same corner.
 *
 * <p>{@code VISIBLE_CLAIMS} records, per anchor, the panels currently claiming a vertical stacking slot
 * in render order. There's no explicit "hide" call a panel can make when it stops being visible (a host
 * screen simply stops calling render), so staleness is self-healing instead: a panel already present in
 * its anchor's list when it renders again means a new render pass has started, so the whole list —
 * including any now-invisible panels — is cleared before this pass's claims are recorded.
 */
final class AnchorStack {

    private static final int PANEL_MARGIN = 8;
    private static final Map<Anchor, List<Claim>> VISIBLE_CLAIMS = new EnumMap<>(Anchor.class);

    private record Claim(Object owner, int height) {}

    private AnchorStack() {}

    static int anchorX(Bounds bounds, Anchor anchor) {
        return switch (anchor) {
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> bounds.x() + bounds.width() - PANEL_MARGIN - ScaleConfigPanel.CARD_WIDTH;
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> bounds.x() + (bounds.width() - ScaleConfigPanel.CARD_WIDTH) / 2;
            default -> bounds.x() + PANEL_MARGIN;
        };
    }

    static int anchorY(Bounds bounds, Anchor anchor, int panelHeight, int stackOffset) {
        return switch (anchor) {
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT ->
                    bounds.y() + bounds.height() - PANEL_MARGIN - panelHeight - stackOffset;
            case CENTER_LEFT, CENTER, CENTER_RIGHT -> bounds.y() + (bounds.height() - panelHeight) / 2 + stackOffset;
            default -> bounds.y() + PANEL_MARGIN + stackOffset;
        };
    }

    /** Whether a panel anchored at {@code anchor} stacks upward from the screen edge (bottom anchors) rather than downward. */
    static boolean stacksUpward(Anchor anchor) {
        return switch (anchor) {
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> true;
            default -> false;
        };
    }

    /**
     * Claims {@code owner}'s vertical stacking slot for {@code anchor}: returns the combined height of
     * other panels already claiming that same anchor earlier in this render pass (0 when {@code owner}
     * is the only visible panel there), then records {@code owner}'s own height so panels registering
     * after it stack below/above accordingly.
     */
    static int claimStackOffset(Object owner, Anchor anchor, int panelHeight) {
        List<Claim> claims = VISIBLE_CLAIMS.computeIfAbsent(anchor, key -> new ArrayList<>());
        for (Claim claim : claims) {
            if (claim.owner() == owner) {
                claims.clear();
                break;
            }
        }
        int offset = 0;
        for (Claim claim : claims) {
            offset += claim.height();
        }
        claims.add(new Claim(owner, panelHeight));
        return offset;
    }
}
