package dev.marie.framework.ui.toolbox.colorpicker;

import dev.marie.framework.ui.color.HexColors;
import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.ThemeKey;
import dev.marie.framework.ui.geometry.Bounds;
import dev.marie.framework.ui.toolbox.OptionRow;
import dev.marie.framework.ui.toolbox.OptionStyle;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** A swatch row: the slot's live color, its label and its hex value; a click reports the slot to the host through {@code onClick}. */
@ApiStatus.Internal
public final class ColorSlotRow implements OptionRow {

    private static final int HEIGHT = 11;
    private static final int SWATCH = 9;
    private static final int SWATCH_GAP = 5;

    private final ColorSlot slot;
    private final Consumer<ColorSlot> onClick;
    private BooleanSupplier enabled = () -> true;
    private Bounds bounds = new Bounds(0, 0, 0, 0);

    public ColorSlotRow(ColorSlot slot, Consumer<ColorSlot> onClick) {
        this.slot = slot;
        this.onClick = onClick;
    }

    @Override
    public int height() {
        return HEIGHT;
    }

    @Override
    public void enabledWhen(BooleanSupplier enabled) {
        this.enabled = enabled;
    }

    @Override
    public void render(RenderContext context, Bounds bounds) {
        this.bounds = bounds;
        boolean on = enabled.getAsBoolean();
        int swatchY = bounds.y() + (HEIGHT - SWATCH) / 2;
        int color = 0xFF000000 | slot.rgb();
        context.fillRect(bounds.x(), swatchY, SWATCH, SWATCH, on ? color : OptionStyle.dimmed(color));
        context.drawBorder(bounds.x(), swatchY, SWATCH, SWATCH, 1, context.theme().color(ThemeKey.BORDER));
        int textX = bounds.x() + SWATCH + SWATCH_GAP;
        OptionStyle.drawLabelAndValue(context, slot.label(), HexColors.formatRgbHex(slot.rgb()), textX, bounds.y() + 1,
                bounds.width() - SWATCH - SWATCH_GAP, OptionStyle.labelColor(context, on), OptionStyle.accentColor(on));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!bounds.contains((int) mouseX, (int) mouseY)) {
            return false;
        }
        if (enabled.getAsBoolean()) {
            onClick.accept(slot);
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
