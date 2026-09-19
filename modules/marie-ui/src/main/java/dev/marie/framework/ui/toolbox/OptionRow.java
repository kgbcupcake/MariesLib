package dev.marie.framework.ui.toolbox;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.function.BooleanSupplier;

/**
 * One stacked row of an {@link OptionLayout}. A shared contract rather than an abstract base:
 * the layout holds a mixed, ordered list of slider/toggle/cycle rows and needs one way to size,
 * draw and route input to each. Rows remember the bounds of their last render for hit-testing.
 */
@ApiStatus.Internal
public interface OptionRow {

    /** Row height in pixels, excluding the gap the layout adds between rows. */
    int height();

    /** While {@code enabled} reports false the row draws dimmed and ignores input. */
    void enabledWhen(BooleanSupplier enabled);

    void render(RenderContext context, Bounds bounds);

    boolean mouseClicked(double mouseX, double mouseY);

    boolean mouseDragged(double mouseX, double mouseY);

    boolean mouseReleased(double mouseX, double mouseY);

    boolean mouseScrolled(double mouseX, double mouseY, double scrollY);
}
