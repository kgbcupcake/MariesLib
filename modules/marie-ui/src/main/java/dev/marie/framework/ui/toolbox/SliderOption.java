package dev.marie.framework.ui.toolbox;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.ThemeKey;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

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
    private BooleanSupplier enabled = () -> true;

    private Bounds bounds = new Bounds(0, 0, 0, 0);
    private boolean dragging;
    /** Last value handed to the setter during the current drag, so unchanged ticks don't re-write. */
    private double liveValue;

    public SliderOption(String label, DoubleSupplier getter, DoubleConsumer setter,
                        double min, double max, double step, Runnable onCommit) {
        if (!(max > min) || !(step > 0)) {
            throw new IllegalArgumentException("slider '" + label + "' needs max > min and step > 0");
        }
        this.label = label;
        this.getter = getter;
        this.setter = setter;
        this.min = min;
        this.max = max;
        this.step = step;
        this.onCommit = onCommit;
    }

    @Override
    public int height() {
        return OptionStyle.LABEL_HEIGHT + OptionStyle.LABEL_TRACK_GAP + OptionStyle.SLIDER_HEIGHT;
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
        OptionStyle.drawLabelAndValue(context, label, Math.round(value * 100) + "%", bounds.x(), bounds.y(), bounds.width(),
                OptionStyle.labelColor(context, on), OptionStyle.labelColor(context, on));
        Bounds track = track();
        float fillPct = (float) ((value - min) / (max - min));
        context.drawBar(track.x(), track.y(), track.width(), track.height(), fillPct,
                context.theme().color(ThemeKey.BAR_BACKGROUND), OptionStyle.accentColor(on));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!bounds.contains((int) mouseX, (int) mouseY)) {
            return false;
        }
        if (enabled.getAsBoolean()) {
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
        return new Bounds(bounds.x(), bounds.y() + OptionStyle.LABEL_HEIGHT + OptionStyle.LABEL_TRACK_GAP,
                bounds.width(), OptionStyle.SLIDER_HEIGHT);
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

    /** Rounds to the nearest {@code min + n * step}. */
    private double snap(double value) {
        return min + Math.round((value - min) / step) * step;
    }

    private double clamp(double value) {
        return Math.min(max, Math.max(min, value));
    }
}
