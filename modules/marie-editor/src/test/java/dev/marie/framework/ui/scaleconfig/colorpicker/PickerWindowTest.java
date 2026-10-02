package dev.marie.framework.ui.scaleconfig.colorpicker;

import dev.marie.framework.ui.geometry.Bounds;
import dev.marie.framework.ui.toolbox.colorpicker.ColorSlot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PickerWindowTest {

    private static final Bounds SCREEN = new Bounds(0, 0, 800, 600);
    private static final Bounds OWNER = new Bounds(100, 100, 224, 89);

    private static ColorSlot slot(String label) {
        return new ColorSlot(label, () -> 0x336699, v -> {}, 0, () -> {});
    }

    @Test
    void cancelRunsOnCloseAndRetargetButNotOnSameSlotRetarget() {
        int[] cancels = {0, 0};
        ColorSlot a = new ColorSlot("a", () -> 0x336699, v -> {}, 0, () -> {}, () -> cancels[0]++);
        ColorSlot b = new ColorSlot("b", () -> 0x336699, v -> {}, 0, () -> {}, () -> cancels[1]++);
        PickerWindow w = new PickerWindow();
        w.show(a, "owner", "t", OWNER, SCREEN);
        w.show(a, "owner", "t", OWNER, SCREEN);
        assertEquals(0, cancels[0], "re-clicking the same slot is not a retarget");
        w.show(b, "owner", "t", OWNER, SCREEN);
        assertEquals(1, cancels[0], "retarget cancels the previous slot");
        assertEquals(0, cancels[1]);
        w.beginFrame(null);
        assertEquals(1, cancels[1], "closing cancels the current slot");
    }

    @Test
    void closesWhenItsOwnerIsNoLongerTheOpenModule() {
        PickerWindow w = new PickerWindow();
        w.show(slot("a"), "owner", "t", OWNER, SCREEN);
        assertTrue(w.isLive());
        w.beginFrame("owner");
        assertTrue(w.isLive());
        w.beginFrame("other");
        assertFalse(w.isLive());
        w.beginFrame("owner");
        assertFalse(w.isLive(), "reopening the owner does not restore it");
    }

    @Test
    void closesWhenOwnerIsCollapsed() {
        PickerWindow w = new PickerWindow();
        w.show(slot("a"), "owner", "t", OWNER, SCREEN);
        w.beginFrame(null);
        assertFalse(w.isLive());
    }

    @Test
    void closesAfterTheRenderGapButNotBeforeIt() throws Exception {
        PickerWindow w = new PickerWindow();
        w.show(slot("a"), "owner", "t", OWNER, SCREEN);
        Thread.sleep(PickerWindow.STALE_MS - 300);
        assertTrue(w.isLive(), "a short gap (lag spike) keeps it open");
        Thread.sleep(400);
        assertFalse(w.isLive());
        w.beginFrame("owner");
        assertFalse(w.isLive());
    }

    @Test
    void retargetKeepsPositionAndOnlyOneWindowExists() {
        PickerWindow w = new PickerWindow();
        w.show(slot("a"), "owner", "a - Owner", OWNER, SCREEN);
        assertTrue(w.mouseClicked(OWNER.x() + OWNER.width() + 20, OWNER.y() + 4, 0), "window sits beside the owner");
        w.show(slot("b"), "owner", "b - Owner", new Bounds(0, 0, 10, 10), SCREEN);
        assertTrue(w.mouseClicked(OWNER.x() + OWNER.width() + 20, OWNER.y() + 4, 0), "retarget keeps the old bounds");
    }
}
