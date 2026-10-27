package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.component.AutoGrowPanelContainer;
import dev.marie.framework.ui.component.ComponentState;
import dev.marie.framework.ui.geometry.Anchor;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.function.Supplier;

/**
 * Saves a free-floating box (a HUD panel, say) by its offset from a screen anchor plus its size in
 * screen pixels. With {@link Anchor#TOP_LEFT} that's simply its absolute position; with e.g. {@link
 * Anchor#BOTTOM_RIGHT} the box keeps its distance from that corner when the window is resized. An axis
 * the player never resized follows the module's natural size, and a left-edge resize grows the
 * record's {@code leftMargin} so content can stay put (see {@link AutoGrowPanelContainer#leftMarginAfterResize}).
 *
 * <pre>{@code
 * PlacementStrategy hud = ScreenAnchoredPlacement.of(() -> new Bounds(0, 0, screenW, screenH), Anchor.TOP_LEFT);
 * }</pre>
 */
@ApiStatus.Experimental
public final class ScreenAnchoredPlacement implements PlacementStrategy {

    private static final ComponentState BLANK = new ComponentState(0, 0, 0, 0, false, false, false, 0);

    private final Supplier<Bounds> screen;
    private final Anchor anchor;
    private final boolean clampToScreen;

    private ScreenAnchoredPlacement(Supplier<Bounds> screen, Anchor anchor, boolean clampToScreen) {
        this.screen = screen;
        this.anchor = anchor;
        this.clampToScreen = clampToScreen;
    }

    public static ScreenAnchoredPlacement of(Supplier<Bounds> screen, Anchor anchor) {
        return new ScreenAnchoredPlacement(screen, anchor, false);
    }

    /** Also keeps boxes fully on screen while dragging and when resolving. */
    public ScreenAnchoredPlacement clampedToScreen() {
        return new ScreenAnchoredPlacement(screen, anchor, true);
    }

    @Override
    public ComponentState toState(Bounds committed, EditCommit commit, ComponentState existing) {
        Bounds sc = screen.get();
        ComponentState base = existing != null ? existing : BLANK;
        int leftMargin = AutoGrowPanelContainer.leftMarginAfterResize(base.leftMargin(), commit.start().width(),
                committed.width(), commit.resized() && commit.leftEdge());
        return new ComponentState(
                (int) Math.round(committed.x() + fx() * committed.width() - (sc.x() + fx() * sc.width())),
                (int) Math.round(committed.y() + fy() * committed.height() - (sc.y() + fy() * sc.height())),
                committed.width(), committed.height(),
                base.collapsed(), commit.widthManual(existing), commit.heightManual(existing),
                leftMargin, base.contentScale(), base.paddingScale());
    }

    @Override
    public Bounds toBounds(ComponentState state, Bounds natural) {
        if (state == null) {
            return keepOnScreen(natural);
        }
        Bounds sc = screen.get();
        int w = state.widthManual() ? state.width() : natural.width();
        int h = state.heightManual() ? state.height() : natural.height();
        int x = (int) Math.round(sc.x() + fx() * sc.width() + state.x() - fx() * w);
        int y = (int) Math.round(sc.y() + fy() * sc.height() + state.y() - fy() * h);
        return keepOnScreen(new Bounds(x, y, w, h));
    }

    @Override
    public Bounds clampRegion() {
        return clampToScreen ? screen.get() : null;
    }

    private Bounds keepOnScreen(Bounds b) {
        if (!clampToScreen) {
            return b;
        }
        Bounds sc = screen.get();
        int x = Math.max(sc.x(), Math.min(b.x(), sc.x() + sc.width() - b.width()));
        int y = Math.max(sc.y(), Math.min(b.y(), sc.y() + sc.height() - b.height()));
        return new Bounds(x, y, b.width(), b.height());
    }

    private double fx() {
        return switch (anchor) {
            case TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT -> 0.0d;
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> 0.5d;
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> 1.0d;
        };
    }

    private double fy() {
        return switch (anchor) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> 0.0d;
            case CENTER_LEFT, CENTER, CENTER_RIGHT -> 0.5d;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> 1.0d;
        };
    }
}
