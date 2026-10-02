package dev.marie.framework.ui.widget;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.component.Constraint;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.geometry.Bounds;

/**
 * A single-series rolling line graph. Push samples with {@link #push}; the newest {@code capacity}
 * are kept and drawn left (oldest) to right (newest) with {@link RenderContext#drawLine}.
 */
@ApiStatus.Experimental
public final class MarieGraph implements MarieComponent {

    private final String id;
    private final double[] ring;
    private int size;
    private int head;
    private int lineColor = 0xFF55FFAA;
    private String unit = "";

    public MarieGraph(String id, int capacity) {
        this.id = id;
        this.ring = new double[Math.max(2, capacity)];
    }

    public MarieGraph withLineColor(int argb) {
        this.lineColor = argb;
        return this;
    }

    /** Suffix for the min/max axis labels, e.g. {@code " MB"}. */
    public MarieGraph withUnit(String unit) {
        this.unit = unit;
        return this;
    }

    public void push(double value) {
        ring[head] = value;
        head = (head + 1) % ring.length;
        size = Math.min(size + 1, ring.length);
    }

    public void clear() {
        size = 0;
        head = 0;
    }

    public int sampleCount() {
        return size;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Constraint constraint() {
        return Constraint.preferred(200, 80);
    }

    /** Samples oldest-first as a fresh array. */
    double[] samples() {
        double[] out = new double[size];
        int start = (head - size + ring.length) % ring.length;
        for (int i = 0; i < size; i++) {
            out[i] = ring[(start + i) % ring.length];
        }
        return out;
    }

    /** Smallest vertical span the graph will zoom in to, so a flat series does not turn noise into a mountain. */
    static final double MIN_SPAN = 1.0;

    /**
     * The vertical range the graph maps onto its plot area, as {@code {min, max}} (never empty
     * input, always {@code max > min}). Padded autoscale: the sample min/max with 10% headroom each
     * side, widened around the midpoint to at least {@link #MIN_SPAN}, so a slow drift is visible
     * while a flat line stays flat.
     */
    static double[] yRange(double[] values) {
        double lo = Double.POSITIVE_INFINITY;
        double hi = Double.NEGATIVE_INFINITY;
        for (double v : values) {
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
        }
        double span = Math.max(hi - lo, MIN_SPAN);
        double mid = (hi + lo) / 2;
        double pad = span * 0.1;
        return new double[]{mid - span / 2 - pad, mid + span / 2 + pad};
    }

    @Override
    public void render(RenderContext ctx, Bounds bounds) {
        ctx.fillRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 0x80000000);
        ctx.drawBorder(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 1, 0x40FFFFFF);
        if (size == 0) {
            return;
        }
        double[] values = samples();
        double[] range = yRange(values);

        int px = bounds.x() + 1;
        int py = bounds.y() + 1;
        int pw = Math.max(1, bounds.width() - 2);
        int ph = Math.max(1, bounds.height() - 2);

        ctx.pushClip(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        try {
            int prevX = 0;
            int prevY = 0;
            for (int i = 0; i < values.length; i++) {
                int x = px + (values.length == 1 ? pw - 1 : i * (pw - 1) / (values.length - 1));
                int y = py + ph - 1 - plotY(values[i], range, ph);
                if (i > 0) {
                    ctx.drawLine(prevX, prevY, x, y, lineColor);
                }
                prevX = x;
                prevY = y;
            }
            ctx.drawText(fmt(range[1]) + unit, px + 2, py + 1, 0xA0FFFFFF, 0.75f);
            ctx.drawText(fmt(range[0]) + unit, px + 2, py + ph - 8, 0xA0FFFFFF, 0.75f);
        } finally {
            ctx.popClip();
        }
    }

    /** Pixels above the plot bottom (0..ph-1) for {@code value}, clamped into the plot. */
    static int plotY(double value, double[] range, int ph) {
        double t = (value - range[0]) / (range[1] - range[0]);
        return (int) Math.round(Math.min(1, Math.max(0, t)) * (ph - 1));
    }

    private static String fmt(double v) {
        return Math.abs(v) >= 100 ? Long.toString(Math.round(v)) : String.format("%.1f", v);
    }
}
