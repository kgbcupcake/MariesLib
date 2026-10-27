package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.component.ComponentState;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Saves a module inside a panel in the panel's local units: offset from an origin and size, both
 * divided by the panel's scale, so the layout survives the panel moving or being rescaled. Bounds are
 * kept inside a clamp region (the panel, or one column of it). An axis the player never resized
 * follows the module's natural size.
 *
 * <pre>{@code
 * PlacementStrategy inPanel = PanelRelativePlacement.of(() -> panelBounds, () -> panelScale);
 * }</pre>
 */
@ApiStatus.Experimental
public final class PanelRelativePlacement implements PlacementStrategy {

    private static final ComponentState BLANK = new ComponentState(0, 0, 0, 0, false, false, false, 0);

    private final Supplier<Bounds> region;
    private final IntSupplier originX;
    private final IntSupplier originY;
    private final DoubleSupplier scale;

    private PanelRelativePlacement(Supplier<Bounds> region, IntSupplier originX, IntSupplier originY, DoubleSupplier scale) {
        this.region = region;
        this.originX = originX;
        this.originY = originY;
        this.scale = scale;
    }

    /** Local (0, 0) is the panel's top-left, and boxes stay inside the panel. */
    public static PanelRelativePlacement of(Supplier<Bounds> panel, DoubleSupplier scale) {
        return new PanelRelativePlacement(panel, () -> panel.get().x(), () -> panel.get().y(), scale);
    }

    /** Local (0, 0) is at ({@code originX}, {@code originY}) on screen, and boxes stay inside {@code region} (e.g. one column). */
    public static PanelRelativePlacement of(Supplier<Bounds> region, IntSupplier originX, IntSupplier originY, DoubleSupplier scale) {
        return new PanelRelativePlacement(region, originX, originY, scale);
    }

    @Override
    public ComponentState toState(Bounds committed, EditCommit commit, ComponentState existing) {
        Bounds b = clamp(committed, region.get());
        double s = safeScale();
        ComponentState base = existing != null ? existing : BLANK;
        return new ComponentState(
                (int) Math.round((b.x() - originX.getAsInt()) / s),
                (int) Math.round((b.y() - originY.getAsInt()) / s),
                (int) Math.round(b.width() / s),
                (int) Math.round(b.height() / s),
                base.collapsed(), commit.widthManual(existing), commit.heightManual(existing),
                base.leftMargin(), base.contentScale(), base.paddingScale());
    }

    @Override
    public Bounds toBounds(ComponentState state, Bounds natural) {
        if (state == null) {
            return clamp(natural, region.get());
        }
        double s = safeScale();
        return clamp(new Bounds(
                originX.getAsInt() + (int) Math.round(state.x() * s),
                originY.getAsInt() + (int) Math.round(state.y() * s),
                state.widthManual() ? (int) Math.round(state.width() * s) : natural.width(),
                state.heightManual() ? (int) Math.round(state.height() * s) : natural.height()), region.get());
    }

    @Override
    public Bounds clampRegion() {
        return region.get();
    }

    private double safeScale() {
        double s = scale.getAsDouble();
        return s > 0 ? s : 1.0d;
    }

    /** Shrinks {@code b} to fit {@code area} if needed, then keeps it inside. */
    private static Bounds clamp(Bounds b, Bounds area) {
        if (area == null) {
            return b;
        }
        int w = Math.max(1, Math.min(b.width(), area.width()));
        int h = Math.max(1, Math.min(b.height(), area.height()));
        int x = Math.max(area.x(), Math.min(b.x(), area.x() + area.width() - w));
        int y = Math.max(area.y(), Math.min(b.y(), area.y() + area.height() - h));
        return new Bounds(x, y, w, h);
    }
}
