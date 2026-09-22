package dev.marie.framework.ui.edit;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.api.EditModeCoordinator;

/**
 * Domain-agnostic edit-mode contract any HUD/screen can implement or adapt a lambda onto for
 * {@link EditModeCoordinator}. A component registered via {@link EditModeCoordinator#registerGroupCapable}
 * is forwarded input alongside other group members in one shared overlay; it's trusted to self-gate
 * on mouseX/mouseY exactly as it already must for single-target {@link EditModeController} use — a
 * component that doesn't hit-test itself internally will misbehave (react to clicks/drags meant for
 * a different member) when forwarded blindly like this.
 */
@ApiStatus.Experimental
public interface EditableComponent {

    /** Enters edit mode for this component. No-op if already active. */
    void enterEditMode();

    /** Exits edit mode for this component. No-op if not active. */
    void exitEditMode();

    /** Whether this component's edit mode is currently active. */
    boolean isEditModeActive();
}
