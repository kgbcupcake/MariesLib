package dev.marie.framework.ui.visibility;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.ui.VisibilityRule;

import java.util.List;

/**
 * Visible iff at least one wrapped rule reports visible (logical OR). Short-circuits: stops
 * evaluating wrapped rules as soon as one returns true.
 *
 * <p>With zero wrapped rules, {@link #isVisible()} returns {@code false} — an empty OR is
 * conventionally false, and that's also the least-surprising default for a component that ended
 * up with no rules to show it.
 */
@ApiStatus.Experimental
public final class AnyOf implements VisibilityRule {

    private final List<VisibilityRule> rules;

    public AnyOf(VisibilityRule... rules) {
        this(List.of(rules));
    }

    public AnyOf(List<VisibilityRule> rules) {
        this.rules = List.copyOf(rules);
    }

    @Override
    public boolean isVisible() {
        for (VisibilityRule rule : rules) {
            if (rule.isVisible()) {
                return true;
            }
        }
        return false;
    }
}
