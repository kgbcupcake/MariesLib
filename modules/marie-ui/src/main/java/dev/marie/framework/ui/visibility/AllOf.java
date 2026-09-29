package dev.marie.framework.ui.visibility;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.ui.VisibilityRule;

import java.util.List;

/**
 * Visible iff every wrapped rule reports visible (logical AND). Short-circuits: stops evaluating
 * wrapped rules as soon as one returns false.
 *
 * <p>With zero wrapped rules, {@link #isVisible()} returns {@code true} — an empty AND is
 * conventionally true, consistent with there being no rule left to suppress the component.
 */
@ApiStatus.Experimental
public final class AllOf implements VisibilityRule {

    private final List<VisibilityRule> rules;

    public AllOf(VisibilityRule... rules) {
        this(List.of(rules));
    }

    public AllOf(List<VisibilityRule> rules) {
        this.rules = List.copyOf(rules);
    }

    @Override
    public boolean isVisible() {
        for (VisibilityRule rule : rules) {
            if (!rule.isVisible()) {
                return false;
            }
        }
        return true;
    }
}
