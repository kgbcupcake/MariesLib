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
 * Generic "icon box + label + fill bar + percent + trend arrow" row — the shape an earlier
 * consumer mod's HUD intake rows, and any similar HUD/screen row (icon, label, a 0..1 progress bar,
 * a percent readout, an up/down trend arrow against a previous value) share. Data-driven entirely
 * through suppliers so one class serves every row instead of one bespoke class per row kind.
 *
 * <p>Follows the same self-positioning module shape as that earlier mod's own balance-row component: the
 * host resolves this row's screen {@link Bounds} itself (see {@code ComponentPersistence}) and hands
 * them to the constructor — this class does not know about panels, columns, or local-unit layout
 * coordinate systems, only the screen-pixel {@link Bounds} it was given and a natural reference
 * height to derive a proportional content scale from.
 */
@ApiStatus.Experimental
public final class BarRowComponent implements MarieComponent, SelfPositioningModule {

    /** Alpha byte for the dimmed percent text — matches the classic consumer-mod intake row this was ported from. */
    public static final int DEFAULT_PERCENT_DIM_ALPHA = 0x99;

    private final String id;
    private final PersistenceProvider store;
    private final Bounds resolvedBounds;
    private final boolean visible;
    private final int naturalLocalWidth;
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
            int naturalLocalWidth,
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
        this(id, store, resolvedBounds, visible, naturalLocalWidth, naturalLocalHeight, contentScale, DEFAULT_PERCENT_DIM_ALPHA,
                iconSupplier, labelSupplier, labelColorSupplier, currentFillSupplier, previousFillSupplier,
                fillColorSupplier, trackColorSupplier, percentColorSupplier,
                arrowUpColorSupplier, arrowDownColorSupplier, boxFillColorSupplier, boxBorderColorSupplier, null, null);
    }

    public BarRowComponent(
            String id,
            PersistenceProvider store,
            Bounds resolvedBounds,
            boolean visible,
            int naturalLocalWidth,
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
        this(id, store, resolvedBounds, visible, naturalLocalWidth, naturalLocalHeight, contentScale, percentDimAlpha,
                iconSupplier, labelSupplier, labelColorSupplier, currentFillSupplier, previousFillSupplier,
                fillColorSupplier, trackColorSupplier, percentColorSupplier,
                arrowUpColorSupplier, arrowDownColorSupplier, boxFillColorSupplier, boxBorderColorSupplier, null, null);
    }

    /**
     * Full constructor, with an optional {@code overlayColorSupplier} and {@code trendCurrentSupplier}.
     *
     * <p>{@code overlayColorSupplier} — a brief highlight overlay drawn over the bar's fill rect each
     * frame it returns a non-transparent ARGB color (alpha byte {@code > 0}), for a "this value just
     * changed" pulse affordance (e.g. an earlier consumer mod's HUD intake rows flash briefly when a
     * value is gained — see {@code MarieClientCache#flashAlpha}). {@code null} (every other
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
            int naturalLocalWidth,
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
        this.naturalLocalWidth = naturalLocalWidth;
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

    public int naturalLocalWidth() {
        return naturalLocalWidth;
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
        if (!visible || MarieModuleSettings.isWindowHidden(store, id)) {
            return;
        }
        // Position/layout geometry (where things sit) is always driven by the fixed base scale —
        // the host's own panel scale, independent of this box's live bounds (resizing changes only
        // how much of the fixed-size content the clip below reveals, matching this row's sibling components).
        // The SIZE each piece actually renders at additionally multiplies in that piece's own
        // Text/Icon/Bar size slider (see MarieModuleSettings#textScale/iconScale/barScale) — the same
        // "base scale for layout, independent per-element multiplier for render size" split the HUD's
        // other bar components already use, so those sliders (present in this row's Style tab) do
        // something instead of being silently ignored.
        double scale = contentScale;
        float textScale = (float) (scale * MarieModuleSettings.textScale(store, id));
        float iconScale = (float) (scale * MarieModuleSettings.iconScale(store, id));
        float barScale = (float) (scale * MarieModuleSettings.barScale(store, id));

        int boxFill = boxFillColorSupplier.getAsInt();
        int boxBorder = boxBorderColorSupplier.getAsInt();
        context.drawRoundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 1, boxFill, boxBorder);

        context.pushClip(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        try {
        int iconSize = Math.max(1, (int) Math.round(20 * iconScale));
        int iconX = bounds.x() + (int) Math.round(2 * scale);
        int iconY = bounds.y() + (int) Math.round(2 * scale);
        // The icon's background box is a plain rect draw, not one of the offset-aware categories
        // withDisplaySettings auto-shifts (drawItem/drawText/drawBar) — drawn at the same offset the
        // icon itself actually lands at (added explicitly here) so the two never separate. Without
        // this, any non-zero icon offset (a manual "Move Icons" drag, or the edit target's left/top-
        // edge-resize content-anchor compensation) drew the icon shifted away while its box stayed
        // behind at the un-shifted position.
        int iconOffX = MarieModuleSettings.iconOffsetX(store, id);
        int iconOffY = MarieModuleSettings.iconOffsetY(store, id);
        context.drawRoundedRect(iconX + iconOffX, iconY + iconOffY, iconSize, iconSize, 1, boxFill, boxBorder);
        // `context` here is the withDisplaySettings-wrapped RenderContext, whose drawItem already
        // multiplies whatever scale it's given by iconScale/textScale internally (see
        // ModuleRenderContext#drawItem) — so the argument must be on the TEXT-scale timeline
        // (textScale, not the already-icon-scaled `iconScale` local var above) for that internal
        // ratio to land on the intended contentScale * iconScale result. Passing `iconScale` here
        // double-applied the icon multiplier and divided by textScale, coupling the rendered icon
        // size to the Text size slider (dragging Text size visibly resized the icon too, even though
        // the persisted Icon size value itself never changed).
        //
        // iconInner is added here only, on top of what drawItem already adds for iconOffX/iconOffY —
        // it moves the icon relative to its box (drawn just above, unaffected by it) instead of the
        // box itself, the "Move Icon" toggle's whole purpose distinct from "Move Icons".
        int iconInnerOffX = MarieModuleSettings.iconInnerOffsetX(store, id);
        int iconInnerOffY = MarieModuleSettings.iconInnerOffsetY(store, id);
        context.drawItem(iconSupplier.get(), iconX + iconInnerOffX, iconY + iconInnerOffY, textScale);

        int labelX = bounds.x() + (int) Math.round(26 * scale);
        int labelY = bounds.y() + (int) Math.round(4 * scale);
        String label = labelSupplier.get();
        context.drawText(label, labelX, labelY, labelColorSupplier.getAsInt(), textScale);

        // Percent sits right after the label (e.g. "Proteins 87%"), not at the far end of the bar —
        // it travels with the label as one text draw call, so it moves under "Move Text" like the
        // label does, not "Move Bars" — and sizes with the same Text size slider as the label.
        double disp = currentFillSupplier.getAsDouble();
        String pctStr = Math.round(disp * 100) + "%";
        int pctColor = percentColorSupplier.getAsInt();
        int dimmedPct = (pctColor & 0x00FFFFFF) | (percentDimAlpha << 24);
        int pctGap = (int) Math.round(4 * scale);
        int pctX = labelX + context.textWidth(label, textScale) + pctGap;
        context.drawText(pctStr, pctX, labelY, dimmedPct, textScale);

        // Anchored off naturalLocalWidth * scale, not any bounds-derived width — resolvedBounds
        // itself tracks a live drag/resize preview (see ComponentPersistence), so deriving the bar's
        // extent from bounds.width() OR resolvedBounds.width() both stretch/shrink the bar with the
        // box. naturalLocalWidth is the row's fixed design width (the same constant the host used to
        // resolve resolvedBounds' natural size in the first place), scaled the same fixed way the icon
        // box above already is, so resizing the box only changes how much of it the clip reveals.
        int arrowSlot = (int) Math.round(10 * scale);
        int arrowLeft = bounds.x() + (int) Math.round(naturalLocalWidth * scale) - arrowSlot;
        int barLeft = labelX;
        int barGap = (int) Math.round(4 * scale);
        int barW = Math.max(0, (arrowLeft - barGap) - barLeft);
        int barH = Math.max(1, (int) Math.round(9 * barScale));
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
            context.drawText("↑", arrowLeft, labelY, arrowUpColorSupplier.getAsInt(), barScale);
        } else if (trendCur < prev - 0.005d) {
            context.drawText("↓", arrowLeft, labelY, arrowDownColorSupplier.getAsInt(), barScale);
        }
        } finally {
            context.popClip();
        }
    }
}
