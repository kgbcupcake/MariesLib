package dev.marie.framework.ui.visibility;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.ui.VisibilityRule;

/** The default rule: the component is always visible. */
@ApiStatus.Experimental
public enum AlwaysVisible implements VisibilityRule {
    INSTANCE;

    @Override
    public boolean isVisible() {
        return true;
    }
}
