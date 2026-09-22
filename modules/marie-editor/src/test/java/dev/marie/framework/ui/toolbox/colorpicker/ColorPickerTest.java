package dev.marie.framework.ui.toolbox.colorpicker;

import dev.marie.framework.ui.color.HexColors;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.Theme;
import dev.marie.framework.ui.geometry.Bounds;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ColorPickerTest {

    /** A RenderContext that counts fillRect calls and records the rectangles; every other primitive is a no-op. */
    static RenderContext counting(List<int[]> fills) {
        return (RenderContext) Proxy.newProxyInstance(RenderContext.class.getClassLoader(), new Class<?>[]{RenderContext.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "fillRect" -> fills.add(new int[]{(Integer) args[0], (Integer) args[1], (Integer) args[2], (Integer) args[3]});
                        case "theme" -> { return Theme.DARK; }
                        case "textWidth" -> { return ((String) args[0]).length() * 5; }
                        case "screenWidth", "screenHeight" -> { return 400; }
                        default -> { }
                    }
                    Class<?> r = method.getReturnType();
                    return r == int.class ? 0 : r == float.class ? 0f : r == boolean.class ? Boolean.FALSE : null;
                });
    }

    private static ColorSlot slot(int[] value, AtomicInteger sets, AtomicInteger commits, int def) {
        return new ColorSlot("Slot", () -> 0xFF000000 | value[0], v -> { value[0] = v; sets.incrementAndGet(); }, def, commits::incrementAndGet);
    }

    @Test
    void hsvRoundTrips() {
        for (int rgb : new int[]{0xFF0000, 0x00FF00, 0x0000FF, 0x123456, 0xABCDEF, 0x808080, 0xFFFFFF}) {
            float[] hsv = ColorPicker.rgbToHsv(rgb);
            assertEquals(rgb, ColorPicker.hsvToRgb(hsv[0], hsv[1], hsv[2]), Integer.toHexString(rgb));
        }
    }

    @Test
    void hexHelpers() {
        assertEquals("#0A0B0C", HexColors.formatRgbHex(0xFF0A0B0C));
        assertEquals(Optional.of(0xFF123456), HexColors.parseStrictRgbHex("#123456"));
        assertTrue(HexColors.parseStrictRgbHex("123456").isEmpty());
        assertTrue(HexColors.parseStrictRgbHex("#12345").isEmpty());
        assertTrue(HexColors.hexInputFilter("#aF09"));
        assertFalse(HexColors.hexInputFilter("#xyz"));
    }

    @Test
    void fillCountStaysBoundedAcrossSizes() {
        int[] value = {0x33AA66};
        ColorPicker picker = new ColorPicker(() -> "Reset");
        picker.setSlot(slot(value, new AtomicInteger(), new AtomicInteger(), 0));
        for (int[] size : new int[][]{{100, 130}, {150, 176}, {300, 352}, {600, 700}}) {
            List<int[]> fills = new ArrayList<>();
            Bounds b = new Bounds(10, 20, size[0], size[1]);
            picker.render(counting(fills), b);
            System.out.println("picker " + size[0] + "x" + size[1] + " fills=" + fills.size());
            assertTrue(fills.size() < 2500, "fills " + fills.size());
            for (int[] f : fills) {
                assertTrue(f[0] >= b.x() && f[1] >= b.y() && f[0] + f[2] <= b.x() + b.width() && f[1] + f[3] <= b.y() + b.height(), "fill outside bounds");
            }
        }
    }

    @Test
    void draggingCallsSetterLiveOnChangeAndCommitsOnceOnRelease() {
        int[] value = {0xFF0000};
        AtomicInteger sets = new AtomicInteger();
        AtomicInteger commits = new AtomicInteger();
        ColorPicker picker = new ColorPicker(() -> "Reset");
        picker.setSlot(slot(value, sets, commits, 0x102030));
        Bounds b = new Bounds(0, 0, 150, 176);
        picker.render(counting(new ArrayList<>()), b);
        // Ring: 12 o'clock-ish is unknown without layout, so scan for a ring hit.
        double hitX = -1;
        double hitY = -1;
        outer:
        for (int y = 0; y < 176; y++) {
            for (int x = 0; x < 150; x++) {
                ColorPicker probe = new ColorPicker(() -> "Reset");
                probe.setSlot(slot(new int[]{0xFF0000}, new AtomicInteger(), new AtomicInteger(), 0));
                probe.render(counting(new ArrayList<>()), b);
                if (probe.mouseClicked(x, y, 0)) {
                    hitX = x; hitY = y;
                    break outer;
                }
            }
        }
        assertTrue(hitX >= 0, "some point on the wheel must accept a click");
        assertTrue(picker.mouseClicked(hitX, hitY, 0));
        int afterPress = sets.get();
        picker.mouseDragged(hitX, hitY, 0, 0, 0);
        assertEquals(afterPress, sets.get(), "no change, no setter call");
        assertEquals(0, commits.get());
        assertTrue(picker.mouseReleased(hitX, hitY, 0));
        assertEquals(1, commits.get());
        assertFalse(picker.mouseReleased(hitX, hitY, 0), "a second release has nothing to end");
        assertEquals(0, value[0] & 0xFF000000, "setter only ever sees RGB");
    }

    @Test
    void resetWritesTheDefaultThenCommits() {
        int[] value = {0xFF0000};
        AtomicInteger sets = new AtomicInteger();
        AtomicInteger commits = new AtomicInteger();
        ColorPicker picker = new ColorPicker(() -> "Reset");
        picker.setSlot(slot(value, sets, commits, 0x102030));
        Bounds b = new Bounds(0, 0, 150, 176);
        picker.render(counting(new ArrayList<>()), b);
        int resetX = 75;
        int resetY = 176 - 4 - 3;
        assertTrue(picker.mouseClicked(resetX, resetY, 0));
        assertEquals(0x102030, value[0]);
        assertEquals(1, commits.get());
    }
}
