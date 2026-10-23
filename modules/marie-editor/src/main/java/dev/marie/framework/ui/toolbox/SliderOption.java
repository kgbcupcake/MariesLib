package dev.marie.framework.ui.toolbox;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.ThemeKey;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Slider row: label and right-aligned percentage above a bar. Edits a value it does not own via
 * getter/setter, within the caller's {@code [min, max]} on {@code step} increments. The setter is
 * called live on every drag tick whose snapped value changed (so the edited thing previews as it is
 * dragged); {@code onCommit} runs once, on release, for the caller to persist. A scroll notch is a
 * complete edit: setter then {@code onCommit}.
 */
@ApiStatus.Internal
public final class SliderOption implements OptionRow {

    private final String label;
    private final DoubleSupplier getter;
    private final DoubleConsumer setter;
    private final double min;
    private final double max;
    private final double step;
    private final Runnable onCommit;
    /** Suffix shown after the value in place of a percentage ("60 px"); null shows the value as a percent. */
    private final String unit;
    /** Decimal places shown for a value with a unit; 0 rounds to a whole number. Ignored for percent sliders. */
    private final int decimals;
    private BooleanSupplier enabled = () -> true;
    private Runnable resetAction;

    private Bounds bounds = new Bounds(0, 0, 0, 0);
    private boolean dragging;
    /** Last value handed to the setter during the current drag, so unchanged ticks don't re-write. */
    private double liveValue;

    public SliderOption(String label, DoubleSupplier getter, DoubleConsumer setter,
                        double min, double max, double step, Runnable onCommit) {
        this(label, getter, setter, min, max, step, null, onCommit);
    }

    /** Whole-number slider over {@code [min, max]} that shows {@code value + " " + unit}; the setter always receives an int. */
    public static SliderOption ofInt(String label, IntSupplier getter, IntConsumer setter,
                                     int min, int max, int step, String unit, Runnable onCommit) {
        return new SliderOption(label, getter::getAsInt, v -> setter.accept((int) Math.round(v)), min, max, step, unit, 0, onCommit);
    }

    /**
     * Decimal slider over {@code [min, max]} that shows the raw value with {@code decimals} places, followed
     * by {@code unit} when it isn't empty ("293.15 K", "0.050"). Unlike {@link #SliderOption(String,
     * DoubleSupplier, DoubleConsumer, double, double, double, Runnable) the percent slider}, 1.0 shows as "1.00".
     */
    public static SliderOption ofDecimal(String label, DoubleSupplier getter, DoubleConsumer setter,
                                         double min, double max, double step, int decimals, String unit, Runnable onCommit) {
        return new SliderOption(label, getter, setter, min, max, step, unit == null ? "" : unit, Math.max(0, decimals), onCommit);
    }

    private SliderOption(String label, DoubleSupplier getter, DoubleConsumer setter,
                         double min, double max, double step, String unit, Runnable onCommit) {
        this(label, getter, setter, min, max, step, unit, 0, onCommit);
    }

    private SliderOption(String label, DoubleSupplier getter, DoubleConsumer setter,
                         double min, double max, double step, String unit, int decimals, Runnable onCommit) {
        if (!(max > min) || !(step > 0)) {
            throw new IllegalArgumentException("slider '" + label + "' needs max > min and step > 0");
        }
        this.decimals = decimals;
        this.label = label;
        this.getter = getter;
        this.setter = setter;
        this.min = min;
        this.max = max;
        this.step = step;
        this.onCommit = onCommit;
        this.unit = unit;
    }

    /** The row's label, as shown to the left of the slider. */
    public String label() {
        return label;
    }

    @Override
    public int height() {
        return OptionStyle.LABEL_HEIGHT + OptionStyle.LABEL_TRACK_GAP + OptionStyle.SLIDER_HEIGHT;
    }

    @Override
    public void defaultTo(double value) {
        this.resetAction = () -> setter.accept(clamp(value));
    }

    @Override
    public void resetWith(Runnable reset) {
        this.resetAction = reset;
    }

    @Override
    public void resetToDefault() {
        if (resetAction != null) {
            resetAction.run();
            onCommit.run();
        }
    }

    @Override
    public void enabledWhen(BooleanSupplier enabled) {
        this.enabled = enabled;
    }

    @Override
    public void render(RenderContext context, Bounds bounds) {
        this.bounds = bounds;
        boolean on = enabled.getAsBoolean();
        double value = dragging ? liveValue : clamp(getter.getAsDouble());
        OptionStyle.drawLabelAndValue(context, label, formatValue(value), bounds.x(), bounds.y(), bounds.width(),
                OptionStyle.labelColor(context, on), OptionStyle.labelColor(context, on));
        Bounds track = track();
        float fillPct = (float) ((value - min) / (max - min));
        int radius = track.height() / 2;
        int edge = on ? context.theme().color(ThemeKey.BORDER) : OptionStyle.dimmed(context.theme().color(ThemeKey.BORDER));
        // Rounded groove with its outline (so the drag area reads as a control even where the fill is empty), then the rounded fill inside it.
        context.drawRoundedRect(track.x(), track.y(), track.width(), track.height(), 1, radius,
                context.theme().color(ThemeKey.BAR_BACKGROUND), edge);
        int inner = track.width() - 2;
        int fillW = Math.round(inner * fillPct);
        if (fillW > 0) {
            int h = track.height() - 2;
            context.drawRoundedRect(track.x() + 1, track.y() + 1, Math.max(h, fillW), h, 0, h / 2,
                    OptionStyle.accentColor(on), OptionStyle.accentColor(on));
        }
        drawArrowButton(context, leftButton(), true, on, edge);
        drawArrowButton(context, rightButton(), false, on, edge);
    }

    /** The value text drawn right of the label: a percent, a whole number with its unit, or {@code decimals} places with its unit. */
    String formatValue(double value) {
        if (unit == null) {
            return Math.round(value * 100) + "%";
        }
        String number = decimals == 0
                ? Long.toString(Math.round(value))
                : String.format(java.util.Locale.ROOT, "%." + decimals + "f", value);
        return unit.isEmpty() ? number : number + " " + unit;
    }

    private void drawArrowButton(RenderContext context, Bounds b, boolean left, boolean on, int edge) {
        context.drawRoundedRect(b.x(), b.y(), b.width(), b.height(), 1, 2, 0x14FFFFFF, edge);
        int color = OptionStyle.labelColor(context, on);
        int cx = b.x() + b.width() / 2;
        int cy = b.y() + b.height() / 2;
        for (int i = 0; i < 3; i++) {
            int x = left ? cx - 1 + i : cx + 1 - i;
            context.fillRect(x, cy - i, 1, 1 + 2 * i, color);
        }
    }

    private Bounds leftButton() {
        return new Bounds(bounds.x(), bounds.y() + OptionStyle.LABEL_HEIGHT + OptionStyle.LABEL_TRACK_GAP,
                OptionStyle.ARROW_BUTTON, OptionStyle.SLIDER_HEIGHT);
    }

    private Bounds rightButton() {
        return new Bounds(bounds.x() + bounds.width() - OptionStyle.ARROW_BUTTON,
                bounds.y() + OptionStyle.LABEL_HEIGHT + OptionStyle.LABEL_TRACK_GAP, OptionStyle.ARROW_BUTTON, OptionStyle.SLIDER_HEIGHT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!bounds.contains((int) mouseX, (int) mouseY)) {
            return false;
        }
        if (enabled.getAsBoolean()) {
            if (leftButton().contains((int) mouseX, (int) mouseY)) {
                write(clamp(snap(getter.getAsDouble() - step)));
                return true;
            }
            if (rightButton().contains((int) mouseX, (int) mouseY)) {
                write(clamp(snap(getter.getAsDouble() + step)));
                return true;
            }
            dragging = true;
            liveValue = Double.NaN;
            preview(valueAt(mouseX));
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY) {
        if (!dragging) {
            return false;
        }
        preview(valueAt(mouseX));
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY) {
        if (!dragging) {
            return false;
        }
        dragging = false;
        onCommit.run();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!bounds.contains((int) mouseX, (int) mouseY)) {
            return false;
        }
        if (enabled.getAsBoolean() && scrollY != 0) {
            write(clamp(snap(getter.getAsDouble() + (scrollY > 0 ? step : -step))));
        }
        return true;
    }

    private Bounds track() {
        int inset = OptionStyle.ARROW_BUTTON + OptionStyle.ARROW_GAP;
        return new Bounds(bounds.x() + inset, bounds.y() + OptionStyle.LABEL_HEIGHT + OptionStyle.LABEL_TRACK_GAP,
                Math.max(1, bounds.width() - 2 * inset), OptionStyle.SLIDER_HEIGHT);
    }

    private void preview(double value) {
        if (value != liveValue) {
            setter.accept(value);
            liveValue = value;
        }
    }

    private void write(double value) {
        setter.accept(value);
        onCommit.run();
    }

    private double valueAt(double mouseX) {
        Bounds track = track();
        double pct = Math.min(1.0, Math.max(0.0, (mouseX - track.x()) / track.width()));
        return clamp(snap(min + pct * (max - min)));
    }

    /** Rounds to the nearest {@code min + n * step}, dropping floating-point noise (0.15000000000000002) past nine places. */
    private double snap(double value) {
        double snapped = min + Math.round((value - min) / step) * step;
        return Math.round(snapped * 1e9) / 1e9;
    }

    private double clamp(double value) {
        return Math.min(max, Math.max(min, value));
    }
}
