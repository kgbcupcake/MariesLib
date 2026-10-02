package dev.marie.framework.ui.component;

import dev.marie.framework.api.ApiStatus;

/**
 * Creates a registered module's {@link MarieComponent} for the current frame.
 *
 * <p>The consuming screen provides its layout/context and module start position.
 *
 * @param <L> screen-specific layout/context type
 */

@ApiStatus.Experimental
public interface ModuleFactory<L>
{
    MarieComponent create(L layoutContext, int startLocalY);
}
