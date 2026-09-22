package dev.marie.framework.ui.edit;

import dev.marie.framework.ui.component.MarieComponent;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Per-target edit-mode state and screen lifecycle. Generalizes Nourished's HUDEditMode/
 * DietScreenEditMode, which each tracked a single static boolean and could therefore only ever
 * have one editable target active at a time. Here each editable target (a HUD panel, a Diet
 * Screen, a future crafting-station panel, etc.) owns its own controller instance, so any number
 * of targets can independently be in edit mode.
 */
public final class EditModeController {

    private final MarieComponent target;
    private final String hintText;
    private final int exitKeyCode;
    private final Runnable onExit;

    private EditOverlayScreen screen;

    public EditModeController(MarieComponent target, String hintText, int exitKeyCode, Runnable onExit) {
        this.target = target;
        this.hintText = hintText;
        this.exitKeyCode = exitKeyCode;
        this.onExit = onExit;
    }

    public boolean isActive() {
        return screen != null;
    }

    /** Opens an {@link EditOverlayScreen} wrapping the target. No-op if already active. */
    public void enter() {
        if (isActive()) {
            return;
        }
        screen = new EditOverlayScreen(target, hintText, exitKeyCode, this::handleExit);
        Minecraft.getInstance().setScreen(screen);
    }

    /**
     * Closes the screen if this controller opened it, and runs onExit unless it already ran via
     * the screen's own exit path (exit key/Escape, or {@link EditOverlayScreen#onClose()}).
     */
    public void exit() {
        if (!isActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == screen) {
            mc.setScreen(null);
        }
        handleExit();
    }

    /** Idempotent: the screen may already have invoked this via its own exit path. */
    private void handleExit() {
        if (screen == null) {
            return;
        }
        screen = null;
        onExit.run();
    }

    // Group-entry lifecycle below: a single shared screen spanning multiple targets, owned
    // statically rather than by any one single-target instance — there is no natural per-instance
    // owner for a screen that wraps a whole list of targets.
    private static EditOverlayScreen groupScreen;
    private static Runnable groupOnExit;

    /** Opens one {@link EditOverlayScreen} wrapping every target in {@code targets}. No-op if a group session is already active. */
    public static void enterGroup(List<MarieComponent> targets, String hintText, int exitKeyCode, Runnable onExit) {
        if (isGroupActive()) {
            return;
        }
        groupOnExit = onExit;
        groupScreen = new EditOverlayScreen(targets, hintText, exitKeyCode, EditModeController::handleGroupExit);
        Minecraft.getInstance().setScreen(groupScreen);
    }

    public static boolean isGroupActive() {
        return groupScreen != null;
    }

    /** Closes the group screen if one is active, and runs its onExit unless it already ran via the screen's own exit path. */
    public static void exitGroup() {
        if (!isGroupActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == groupScreen) {
            mc.setScreen(null);
        }
        handleGroupExit();
    }

    /** Idempotent: the group screen may already have invoked this via its own exit path. */
    private static void handleGroupExit() {
        if (groupScreen == null) {
            return;
        }
        groupScreen = null;
        Runnable onExit = groupOnExit;
        groupOnExit = null;
        onExit.run();
    }
}
