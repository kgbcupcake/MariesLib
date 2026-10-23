package dev.marie.framework.ui.component;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.VisibilityRule;
import dev.marie.framework.ui.geometry.Bounds;

/**
 * Base UI element contract: identity, constraint metadata, rendering, and input handling.
 * A {@link Container} is just a MarieComponent that owns children — there is no special case here
 * for windows; a future Window is a Container like any other. When multiple targets share one
 * {@code EditOverlayScreen} (group edit mode), every input event is forwarded to every target —
 * each is trusted to self-gate on the passed mouseX/mouseY internally, same as single-target edit
 * mode; a target that doesn't hit-test itself will misbehave under group forwarding.
 */
@ApiStatus.Experimental
public interface MarieComponent {

    /** Stable identity used for persistence lookups and diffing; not necessarily unique across an entire screen tree. */
    String id();

    Constraint constraint();

    /** Governs whether this component renders/receives input this frame. Defaults to always-visible. */
    default VisibilityRule visibilityRule() {
        return VisibilityRule.ALWAYS_VISIBLE;
    }

    void render(RenderContext context, Bounds bounds);

    default boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    /** A printable character was typed (e.g. a letter key, routed separately from {@link #keyPressed}'s raw keycodes — see GLFW's char-callback docs). Only a component that owns editable text needs this. */
    default boolean charTyped(char codePoint, int modifiers) {
        return false;
    }

    default boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }
}
