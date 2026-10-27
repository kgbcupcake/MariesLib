package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.component.MarieComponent;

/**
 * Implemented by a component that decorates another one (adds a gutter, applies brightness, ...), so
 * hosts like {@code HubPanel} can see through it — e.g. to wire the color picker to an options panel
 * built with {@link MarieToolbox} that sits inside the wrapper.
 */
@ApiStatus.Experimental
public interface ContentWrapper {

    /** The component this one draws and forwards input to. */
    MarieComponent wrappedContent();
}
