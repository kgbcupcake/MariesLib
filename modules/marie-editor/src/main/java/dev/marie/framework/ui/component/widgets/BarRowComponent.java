package dev.marie.framework.ui.component.widgets;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.api.MarieModuleSettings;
import dev.marie.framework.ui.component.Constraint;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.component.SelfPositioningModule;
import dev.marie.framework.ui.geometry.Bounds;
import net.minecraft.world.item.ItemStack;

import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Generic "icon box + label + fill bar + percent + trend arrow" row — the shape Nourished's Diet
 * Screen intake rows, and any similar HUD/screen row (icon, label, a 0..1 progress bar, a percent
 * readout, an up/down trend arrow against a previous value) share. Data-driven entirely through
 * suppliers so one class serves every row instead of one bespoke class per row kind.
 *
 * <p>Follows the same self-positioning module shape as Nourished's {@code BalanceComponent}: the
 * host resolves this row's screen {@link Bounds} itself (see {@code ComponentPersistence}) and hands
 * them to the constructor — this class does not know about panels, columns, or local-unit layout
 * coordinate systems, only the screen-pixel {@link Bounds} it was given and a natural reference
 * height to derive a proportional content scale from.
 */
@ApiStatus.Experimental
public final class BarRowComponent implements MarieComponent, SelfPositioningModule {

    /** Alpha byte for the dimmed percent text — matches the classic Diet Screen intake row. */
    public static final int DEFAULT_PERCENT_DIM_ALPHA = 0x99;

    private final String id;
    private final PersistenceProvider store;
    private final Bounds resolvedBounds;
    private final boolean visible;
    private final int naturalLocalHeight;
    private final double contentScale;
    private final int percentDimAlpha;

    private final Supplier<ItemStack> iconSupplier;
    private final Supplier<String> labelSupplier;
    private final IntSupplier labelColorSupplier;
    private final DoubleSupplier currentFillSupplier;
    private final DoubleSupplier previousFillSupplier;
    private final IntSupplier fillColorSupplier;
    private final IntSupplier trackColorSupplier;
    private final IntSupplier percentColorSupplier;
    private final IntSupplier arrowUpColorSupplier;
    private final IntSupplier arrowDownColorSupplier;
    private final IntSupplier boxFillColorSupplier;
    private final IntSupplier boxBorderColorSupplier;
    /** Nullable — see {@link #render}; a row that doesn't pass one draws no overlay at all. */
    private final IntSupplier overlayColorSupplier;
    /** Nullable — see {@link #render}; a row that doesn't pass one compares {@link #currentFillSupplier} against {@link #previousFillSupplier} for the trend arrow, same as before this field existed. */
    private final DoubleSupplier trendCurrentSupplier;

    public BarRowComponent(
            String id,
            PersistenceProvider store,
            Bounds resolvedBounds,
            boolean visible,
            int naturalLocalHeight,
            double contentScale,
            Supplier<ItemStack> iconSupplier,
            Supplier<String> labelSupplier,
            IntSupplier labelColorSupplier,
            DoubleSupplier currentFillSupplier,
            DoubleSupplier previousFillSupplier,
            IntSupplier fillColorSupplier,
            IntSupplier trackColorSupplier,
            IntSupplier percentColorSupplier,
            IntSupplier arrowUpColorSupplier,
            IntSupplier arrowDownColorSupplier,
            IntSupplier boxFillColorSupplier,
            IntSupplier boxBorderColorSupplier
    ) {
        this(id, store, resolvedBounds, visible, naturalLocalHeight, contentScale, DEFAULT_PERCENT_DIM_ALPHA,
                iconSupplier, labelSupplier, labelColorSupplier, currentFillSupplier, previousFillSupplier,
                fillColorSupplier, trackColorSupplier, percentColorSupplier,
                arrowUpColorSupplier, arrowDownColorSupplier, boxFillColorSupplier, boxBorderColorSupplier, null, null);
    }

    public BarRowComponent(
            String id,
            PersistenceProvider store,
            Bounds resolvedBounds,
            boolean visible,
            int naturalLocalHeight,
            double contentScale,
            int percentDimAlpha,
            Supplier<ItemStack> iconSupplier,
            Supplier<String> labelSupplier,
            IntSupplier labelColorSupplier,
            DoubleSupplier currentFillSupplier,
            DoubleSupplier previousFillSupplier,
            IntSupplier fillColorSupplier,
            IntSupplier trackColorSupplier,
            IntSupplier percentColorSupplier,
            IntSupplier arrowUpColorSupplier,
            IntSupplier arrowDownColorSupplier,
            IntSupplier boxFillColorSupplier,
            IntSupplier boxBorderColorSupplier
    ) {
        this(id, store, resolvedBounds, visible, naturalLocalHeight, contentScale, percentDimAlpha,
                iconSupplier, labelSupplier, labelColorSupplier, currentFillSupplier, previousFillSupplier,
                fillColorSupplier, trackColorSupplier, percentColorSupplier,
                arrowUpColorSupplier, arrowDownColorSupplier, boxFillColorSupplier, boxBorderColorSupplier, null, null);
    }

    /**
     * Full constructor, with an optional {@code overlayColorSupplier} and {@code trendCurrentSupplier}.
     *
     * <p>{@code overlayColorSupplier} — a brief highlight overlay drawn over the bar's fill rect each
     * frame it returns a non-transparent ARGB color (alpha byte {@code > 0}), for a "this value just
     * changed" pulse affordance (e.g. Nourished's Diet Screen intake rows flash briefly when a
     * nutrient is gained — see {@code MarieClientCache#flashAlpha}). {@code null} (every other
     * constructor) draws no overlay at all, so existing/other consumers of this class aren't forced
     * to wire one.
     *
     * <p>{@code trendCurrentSupplier} — the "current" value compared against {@code
     * previousFillSupplier} for the trend arrow, if it needs to differ from what's actually drawn as
     * the bar's fill/percent ({@code currentFillSupplier}) — e.g. a host that eases {@code
     * currentFillSupplier} toward its target for a smooth animated fill, but still wants the arrow to
     * reflect the real, un-eased value's direction rather than the animation's current position
     * (which can briefly point the wrong way while easing toward a value that's already reversed
     * again). {@code null} (every other constructor) compares {@code currentFillSupplier} itself
     * against {@code previousFillSupplier}, unchanged from before this parameter existed.
     */
    public BarRowComponent(
            String id,
            PersistenceProvider store,
            Bounds resolvedBounds,
            boolean visible,
            int naturalLocalHeight,
            double contentScale,
            int percentDimAlpha,
            Supplier<ItemStack> iconSupplier,
            Supplier<String> labelSupplier,
            IntSupplier labelColorSupplier,
            DoubleSupplier currentFillSupplier,
            DoubleSupplier previousFillSupplier,
            IntSupplier fillColorSupplier,
            IntSupplier trackColorSupplier,
            IntSupplier percentColorSupplier,
            IntSupplier arrowUpColorSupplier,
            IntSupplier arrowDownColorSupplier,
            IntSupplier boxFillColorSupplier,
            IntSupplier boxBorderColorSupplier,
            IntSupplier overlayColorSupplier,
            DoubleSupplier trendCurrentSupplier
    ) {
        this.id = id;
        this.store = store;
        this.resolvedBounds = resolvedBounds;
        this.visible = visible;
        this.naturalLocalHeight = naturalLocalHeight;
        this.contentScale = contentScale;
        this.percentDimAlpha = percentDimAlpha;
        this.iconSupplier = iconSupplier;
        this.labelSupplier = labelSupplier;
        this.labelColorSupplier = labelColorSupplier;
        this.currentFillSupplier = currentFillSupplier;
        this.previousFillSupplier = previousFillSupplier;
        this.fillColorSupplier = fillColorSupplier;
        this.trackColorSupplier = trackColorSupplier;
        this.percentColorSupplier = percentColorSupplier;
        this.arrowUpColorSupplier = arrowUpColorSupplier;
        this.arrowDownColorSupplier = arrowDownColorSupplier;
        this.boxFillColorSupplier = boxFillColorSupplier;
        this.boxBorderColorSupplier = boxBorderColorSupplier;
        this.overlayColorSupplier = overlayColorSupplier;
        this.trendCurrentSupplier = trendCurrentSupplier;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public int localHeight() {
        return visible ? naturalLocalHeight : 0;
    }

    public int naturalLocalHeight() {
        return naturalLocalHeight;
    }

    @Override
    public Bounds resolvedBounds() {
        return resolvedBounds;
    }

    public boolean isVisible() {
        return visible;
    }

    @Override
    public Constraint constraint() {
        return Constraint.preferred(resolvedBounds.width(), resolvedBounds.height());
    }

    @Override
    public void render(RenderContext baseContext, Bounds bounds) {
        RenderContext context = MarieModuleSettings.withDisplaySettings(baseContext, store, id);
        if (!visible) {
            return;
        }
        // Content is drawn at a fixed scale (the host's own panel scale), independent of this box's
        // live bounds — resizing the box changes only how much of that fixed-size content is visible
        // (via the clip below), matching every other Diet Screen sub-box (see RecentMealsComponent),
        // instead of rescaling the icon/text/bar proportionally to the box's own size.
        double scale = contentScale;
        float fscale = (float) scale;

        int boxFill = boxFillColorSupplier.getAsInt();
        int boxBorder = boxBorderColorSupplier.getAsInt();
        context.drawRoundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 1, boxFill, boxBorder);

        context.pushClip(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        try {
        int iconSize = Math.max(1, (int) Math.round(20 * scale));
        int iconX = bounds.x() + (int) Math.round(2 * scale);
        int iconY = bounds.y() + (int) Math.round(2 * scale);
        context.drawRoundedRect(iconX, iconY, iconSize, iconSize, 1, boxFill, boxBorder);
        context.drawItem(iconSupplier.get(), iconX, iconY, fscale);

        int labelX = bounds.x() + (int) Math.round(26 * scale);
        int labelY = bounds.y() + (int) Math.round(4 * scale);
        String label = labelSupplier.get();
        context.drawText(label, labelX, labelY, labelColorSupplier.getAsInt(), fscale);

        // Percent sits right after the label (e.g. "Proteins 87%"), not at the far end of the bar —
        // it travels with the label as one text draw call, so it moves under "Move Text" like the
        // label does, not "Move Bars".
        double disp = currentFillSupplier.getAsDouble();
        String pctStr = Math.round(disp * 100) + "%";
        int pctColor = percentColorSupplier.getAsInt();
        int dimmedPct = (pctColor & 0x00FFFFFF) | (percentDimAlpha << 24);
        int pctGap = (int) Math.round(4 * scale);
        int pctX = labelX + context.textWidth(label, fscale) + pctGap;
        context.drawText(pctStr, pctX, labelY, dimmedPct, fscale);

        // Anchored off resolvedBounds' natural width, not the live (possibly resized) bounds' width —
        // otherwise the bar stretches/shrinks with the box instead of staying fixed-size like the icon
        // box above, with the resize only changing how much of it the clip reveals.
        int arrowSlot = (int) Math.round(10 * scale);
        int arrowLeft = bounds.x() + resolvedBounds.width() - arrowSlot;
        int barLeft = labelX;
        int barGap = (int) Math.round(4 * scale);
        int barW = Math.max(0, (arrowLeft - barGap) - barLeft);
        int barH = Math.max(1, (int) Math.round(9 * scale));
        int barY = bounds.y() + (int) Math.round(14 * scale);

        double prev = previousFillSupplier.getAsDouble();
        int fillColor = fillColorSupplier.getAsInt();
        context.drawBar(barLeft, barY, barW, barH, (float) disp, trackColorSupplier.getAsInt(), fillColor);
        MarieModuleSettings.recordBarExtent(store, id, barLeft, barY, Math.max(1, barW), barH);

        if (overlayColorSupplier != null) {
            int overlay = overlayColorSupplier.getAsInt();
            if (((overlay >>> 24) & 0xFF) > 0) {
                context.fillRect(barLeft, barY, barW, barH, overlay);
            }
        }

        double trendCur = trendCurrentSupplier != null ? trendCurrentSupplier.getAsDouble() : disp;
        if (trendCur > prev + 0.005d) {
            context.drawText("↑", arrowLeft, labelY, arrowUpColorSupplier.getAsInt(), fscale);
        } else if (trendCur < prev - 0.005d) {
            context.drawText("↓", arrowLeft, labelY, arrowDownColorSupplier.getAsInt(), fscale);
        }
        } finally {
            context.popClip();
        }
    }
}
