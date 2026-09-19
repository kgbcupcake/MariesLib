package dev.marie.framework.ui.toolbox;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Choice row for enum-style values: shows {@code labels[index]} on the right, a click advances (wrapping) then calls {@code onCommit}. The consumer maps its own type to and from the index. */
@ApiStatus.Internal
public final class CycleOption implements OptionRow {

    private final String label;
    private final String[] labels;
    private final IntSupplier getter;
    private final IntConsumer setter;
    private final Runnable onCommit;
    private BooleanSupplier enabled = () -> true;

    private Bounds bounds = new Bounds(0, 0, 0, 0);

    public CycleOption(String label, String[] labels, IntSupplier getter, IntConsumer setter, Runnable onCommit) {
        if (labels.length == 0) {
            throw new IllegalArgumentException("cycle '" + label + "' needs at least one choice");
        }
        this.label = label;
        this.labels = labels.clone();
        this.getter = getter;
        this.setter = setter;
        this.onCommit = onCommit;
    }

    @Override
    public int height() {
        return OptionStyle.LABEL_HEIGHT;
    }

    @Override
    public void enabledWhen(BooleanSupplier enabled) {
        this.enabled = enabled;
    }

    @Override
    public void render(RenderContext context, Bounds bounds) {
        this.bounds = bounds;
        boolean on = enabled.getAsBoolean();
        OptionStyle.drawLabelAndValue(context, label, labels[current()], bounds.x(), bounds.y(), bounds.width(),
                OptionStyle.labelColor(context, on), OptionStyle.accentColor(on));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!bounds.contains((int) mouseX, (int) mouseY)) {
            return false;
        }
        if (enabled.getAsBoolean()) {
            setter.accept((current() + 1) % labels.length);
            onCommit.run();
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY) {
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY) {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        return false;
    }

    /** The bound index, clamped so an out-of-range value from the consumer can't throw. */
    private int current() {
        return Math.min(labels.length - 1, Math.max(0, getter.getAsInt()));
    }
}
