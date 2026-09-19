package dev.marie.framework.ui.toolbox;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.function.BooleanSupplier;

/** One-shot action row: the label on the left and a "RESET"-style caption on the right; a click runs the action, then {@code onCommit}. */
@ApiStatus.Internal
public final class ButtonOption implements OptionRow {

    private final String label;
    private final String caption;
    private final Runnable action;
    private final Runnable onCommit;
    private BooleanSupplier enabled = () -> true;

    private Bounds bounds = new Bounds(0, 0, 0, 0);

    public ButtonOption(String label, String caption, Runnable action, Runnable onCommit) {
        this.label = label;
        this.caption = caption;
        this.action = action;
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
        OptionStyle.drawLabelAndValue(context, label, caption, bounds.x(), bounds.y(), bounds.width(),
                OptionStyle.labelColor(context, on), OptionStyle.accentColor(on));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!bounds.contains((int) mouseX, (int) mouseY)) {
            return false;
        }
        if (enabled.getAsBoolean()) {
            action.run();
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
