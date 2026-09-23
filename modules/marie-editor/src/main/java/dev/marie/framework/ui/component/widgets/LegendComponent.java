package dev.marie.framework.ui.component.widgets;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.api.MarieModuleSettings;
import dev.marie.framework.ui.component.Constraint;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.component.SelfPositioningModule;
import dev.marie.framework.ui.geometry.Bounds;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Generic bottom-of-panel-style "legend" box: a centered title, N equal columns each with a colored
 * swatch and two lines of text, separated by vertical divider lines — ported from Nourished's Diet
 * Screen intake legend ({@code drawLegendBar}/{@code drawLegendEntry}) so any host with a similar
 * "here's what these colors mean" box can reuse the same drawing code instead of hand-rolling it.
 */
@ApiStatus.Experimental
public final class LegendComponent implements MarieComponent, SelfPositioningModule {

    /** One column of the legend: two lines of text (both colored with {@code accentColor}, no separate swatch — see {@link #drawEntry}) and small per-entry pixel offsets (in local units, before scale) preserved from the original hand-tuned Diet Screen layout. */
    public record LegendEntry(
            IntSupplier accentColor,
            Supplier<String> line1,
            Supplier<String> line2,
            int line1OffsetX,
            int line2OffsetX
    ) {
        public LegendEntry(IntSupplier accentColor, Supplier<String> line1, Supplier<String> line2) {
            this(accentColor, line1, line2, 0, 0);
        }
    }

    /** ~12% darker (brightness reduction) — matches the classic legend's dimmed swatches/text. */
    public static int dimLegend(int argb) {
        float f = 0.88f;
        int a = (argb >>> 24) & 0xFF;
        int r = Mth.clamp((int) (((argb >> 16) & 0xFF) * f), 0, 255);
        int g = Mth.clamp((int) (((argb >> 8) & 0xFF) * f), 0, 255);
        int b = Mth.clamp((int) ((argb & 0xFF) * f), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private final String id;
    private final PersistenceProvider store;
    private final Bounds resolvedBounds;
    private final boolean visible;
    private final int naturalLocalHeight;
    private final double contentScale;
    private final Supplier<String> titleSupplier;
    private final IntSupplier titleColorSupplier;
    private final IntSupplier dividerColorSupplier;
    private final IntSupplier boxFillColorSupplier;
    private final IntSupplier boxBorderColorSupplier;
    private final List<LegendEntry> entries;

    public LegendComponent(
            String id,
            PersistenceProvider store,
            Bounds resolvedBounds,
            boolean visible,
            int naturalLocalHeight,
            double contentScale,
            Supplier<String> titleSupplier,
            IntSupplier titleColorSupplier,
            IntSupplier dividerColorSupplier,
            IntSupplier boxFillColorSupplier,
            IntSupplier boxBorderColorSupplier,
            List<LegendEntry> entries
    ) {
        this.id = id;
        this.store = store;
        this.resolvedBounds = resolvedBounds;
        this.visible = visible;
        this.naturalLocalHeight = naturalLocalHeight;
        this.contentScale = contentScale;
        this.titleSupplier = titleSupplier;
        this.titleColorSupplier = titleColorSupplier;
        this.dividerColorSupplier = dividerColorSupplier;
        this.boxFillColorSupplier = boxFillColorSupplier;
        this.boxBorderColorSupplier = boxBorderColorSupplier;
        this.entries = entries;
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
        if (!visible || entries.isEmpty() || MarieModuleSettings.isWindowHidden(store, id)) {
            return;
        }
        // Fixed content scale (the host's own panel scale), independent of this box's live bounds —
        // see BarRowComponent's render() for why (resizing must change only the box, not the text) —
        // additionally multiplied by this module's own Text size slider, same split BarRowComponent
        // uses, so that slider (present in this box's Style tab) actually does something.
        double scale = contentScale * MarieModuleSettings.textScale(store, id);
        float fscale = (float) scale;

        context.drawRoundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 1,
                boxFillColorSupplier.getAsInt(), boxBorderColorSupplier.getAsInt());

        context.pushClip(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        try {
        String title = titleSupplier.get();
        int titleW = context.textWidth(title, fscale);
        int titleX = bounds.x() + (bounds.width() - titleW) / 2;
        int titleY = bounds.y() + (int) Math.round(3 * scale);
        context.drawText(title, titleX, titleY, dimLegend(titleColorSupplier.getAsInt()), fscale);
        MarieModuleSettings.recordHeaderExtent(store, id, titleX, titleY, titleW, Math.max(1, (int) Math.round(7 * scale)));

        int colLeft = bounds.x() + (int) Math.round(6 * scale);
        int colW = (bounds.width() - (int) Math.round(12 * scale)) / entries.size();
        int lineTop = bounds.y() + (int) Math.round(12 * scale);
        int lineH = Math.max(0, bounds.height() - (int) Math.round(4 * scale) - (int) Math.round(12 * scale));
        int dividerColor = dividerColorSupplier.getAsInt();
        for (int i = 1; i < entries.size(); i++) {
            context.fillRect(colLeft + colW * i, lineTop, Math.max(1, (int) Math.round(scale)), lineH, dividerColor);
        }

        int rowY = bounds.y() + (int) Math.round(14 * scale);
        for (int i = 0; i < entries.size(); i++) {
            LegendEntry entry = entries.get(i);
            int colX = colLeft + colW * i;
            // Per-column clip: content is drawn at a fixed scale (see above), so a column narrower
            // than an entry's text (the box resized smaller than its natural width, or entries.size()
            // grows) must stop that entry's text at its own column boundary instead of letting it
            // bleed into (and visually merge with) the next column's text.
            context.pushClip(colX, bounds.y(), Math.max(1, colW), bounds.height());
            try {
                drawEntry(context, colX, rowY, colW, scale, entry, fscale);
            } finally {
                context.popClip();
            }
        }
        } finally {
            context.popClip();
        }
    }

    /** No swatch square — both lines are colored with the entry's own accent instead, carrying the same color-coding without a separate decorative element. */
    private void drawEntry(RenderContext context, int x, int y, int w, double scale, LegendEntry entry, float fscale) {
        int accent = dimLegend(entry.accentColor().getAsInt());
        String line1 = entry.line1().get();
        String line2 = entry.line2().get();
        int line1X = x + ((w - context.textWidth(line1, fscale)) / 2) + (int) Math.round(entry.line1OffsetX() * scale);
        int line2X = x + ((w - context.textWidth(line2, fscale)) / 2) + (int) Math.round(entry.line2OffsetX() * scale);
        context.drawText(line1, line1X, y, accent, fscale);
        context.drawText(line2, line2X, y + (int) Math.round(10 * scale), accent, fscale);
    }
}
