package dev.marie.framework.ui;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.visibility.AlwaysVisible;

/** Decides whether a {@link MarieComponent} should render/receive input this frame. */
@ApiStatus.Experimental
public interface VisibilityRule {

    VisibilityRule ALWAYS_VISIBLE = AlwaysVisible.INSTANCE;

    boolean isVisible();
}
