package dev.marie.framework.ui.toolbox;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.function.BooleanSupplier;

/** ON/OFF row with no track: a click anywhere on it flips the bound value, then calls {@code onCommit}. */
@ApiStatus.Internal
public final class ToggleOption implements OptionRow {

    private final String label;
    private final BooleanSupplier getter;
    private final BooleanSetter setter;
    private final Runnable onCommit;
    private BooleanSupplier enabled = () -> true;
    private Runnable resetAction;

    private Bounds bounds = new Bounds(0, 0, 0, 0);

    public ToggleOption(String label, BooleanSupplier getter, BooleanSetter setter, Runnable onCommit) {
        this.label = label;
        this.getter = getter;
        this.setter = setter;
        this.onCommit = onCommit;
    }

    @Override
    public int height() {
        return OptionStyle.LABEL_HEIGHT;
    }

    @Override
    public void defaultTo(boolean value) {
        this.resetAction = () -> setter.accept(value);
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
        boolean value = getter.getAsBoolean();
        OptionStyle.drawLabelAndValue(context, label, value ? "ON" : "OFF", bounds.x(), bounds.y(), bounds.width(),
                OptionStyle.labelColor(context, on), value ? OptionStyle.accentColor(on) : OptionStyle.labelColor(context, on));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!bounds.contains((int) mouseX, (int) mouseY)) {
            return false;
        }
        if (enabled.getAsBoolean()) {
            setter.accept(!getter.getAsBoolean());
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
}
