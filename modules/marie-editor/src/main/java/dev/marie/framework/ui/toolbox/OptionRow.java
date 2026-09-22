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

    /** Sets the value this slider returns to on "Reset This Tab". Only sliders take a double. */
    default void defaultTo(double value) {
        throw new IllegalStateException("a double default only applies to a slider");
    }

    /** Sets the value this toggle returns to on "Reset This Tab". Only toggles take a boolean. */
    default void defaultTo(boolean value) {
        throw new IllegalStateException("a boolean default only applies to a toggle");
    }

    /** Sets the choice index this cycle returns to on "Reset This Tab". Only cycles take an int. */
    default void defaultTo(int index) {
        throw new IllegalStateException("an index default only applies to a cycle");
    }

    /** Replaces the reset with a custom action, for values that don't reset by writing a plain number. */
    default void resetWith(Runnable reset) {
        throw new IllegalStateException("this row has no value to reset");
    }

    /** Puts the row back to its default (then calls its commit callback); no-op if none was set. */
    default void resetToDefault() {}

    void render(RenderContext context, Bounds bounds);

    boolean mouseClicked(double mouseX, double mouseY);

    boolean mouseDragged(double mouseX, double mouseY);

    boolean mouseReleased(double mouseX, double mouseY);

    boolean mouseScrolled(double mouseX, double mouseY, double scrollY);
}
