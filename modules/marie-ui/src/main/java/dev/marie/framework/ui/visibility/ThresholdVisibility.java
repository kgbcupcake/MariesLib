package dev.marie.framework.ui.visibility;

import dev.marie.framework.ui.VisibilityRule;

import java.util.function.Supplier;

/**
 * Visible based on a live value compared against optional hide/show thresholds: once the value
 * reaches {@code hideAtOrAbove} the component is suppressed, until it climbs back past
 * {@code showAtOrAbove}, which overrides the suppression. Either threshold may be null to disable
 * that half of the rule. Generalizes the hide/show-above logic an earlier, consumer-specific
 * visibility-rules implementation hand-rolled, for any
 * {@link Comparable} value type.
 */
public final class ThresholdVisibility<T extends Comparable<T>> implements VisibilityRule {

    private final Supplier<T> valueSupplier;
    private final T hideAtOrAbove;
    private final T showAtOrAbove;

    public ThresholdVisibility(Supplier<T> valueSupplier, T hideAtOrAbove, T showAtOrAbove) {
        this.valueSupplier = valueSupplier;
        this.hideAtOrAbove = hideAtOrAbove;
        this.showAtOrAbove = showAtOrAbove;
    }

    @Override
    public boolean isVisible() {
        T value = valueSupplier.get();
        boolean hidden = hideAtOrAbove != null && value.compareTo(hideAtOrAbove) >= 0;
        boolean shown = showAtOrAbove != null && value.compareTo(showAtOrAbove) >= 0;
        return shown || !hidden;
    }
}
