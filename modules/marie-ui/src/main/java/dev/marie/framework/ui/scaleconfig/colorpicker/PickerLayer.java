package dev.marie.framework.ui.scaleconfig.colorpicker;

import dev.marie.framework.api.ApiStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Optional top layer for hosts that draw several {@link ScaleConfigPanel}s in one frame (e.g. an edit
 * overlay with a target per panel). A later panel's content or tabs would paint over an earlier panel's
 * picker window, and every target sees every click. A host that calls {@link #begin} before its targets
 * render and {@link #flush} after gets each picker drawn last, and can offer input to {@link
 * #mouseClicked} and friends first. Hosts that never call {@link #begin} are unaffected: pickers draw
 * inline. Single-threaded (render thread) state.
 */
@ApiStatus.Internal
public final class PickerLayer {

    private static final List<Runnable> PENDING = new ArrayList<>();
    private static final List<PickerWindow> SHOWN = new ArrayList<>();
    private static boolean deferring;

    private PickerLayer() {}

    /** Starts collecting pickers; call before rendering the targets. */
    public static void begin() {
        deferring = true;
        PENDING.clear();
        SHOWN.clear();
    }

    /** Draws every collected picker, in the order they were shown. */
    public static void flush() {
        deferring = false;
        for (Runnable draw : PENDING) {
            draw.run();
        }
        PENDING.clear();
    }

    /** Stops collecting without drawing; call from a {@code finally} so a throwing target can't leave later frames deferring. */
    public static void end() {
        deferring = false;
        PENDING.clear();
    }

    static boolean defer(PickerWindow window, Runnable draw) {
        if (!deferring) {
            return false;
        }
        PENDING.add(draw);
        SHOWN.add(window);
        return true;
    }

    public static boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = SHOWN.size() - 1; i >= 0; i--) {
            if (SHOWN.get(i).mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    public static boolean mouseDragged(double mouseX, double mouseY, int button) {
        for (int i = SHOWN.size() - 1; i >= 0; i--) {
            if (SHOWN.get(i).mouseDragged(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    public static boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (int i = SHOWN.size() - 1; i >= 0; i--) {
            if (SHOWN.get(i).mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    public static boolean mouseScrolled(double mouseX, double mouseY) {
        for (int i = SHOWN.size() - 1; i >= 0; i--) {
            if (SHOWN.get(i).mouseScrolled(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }
}
