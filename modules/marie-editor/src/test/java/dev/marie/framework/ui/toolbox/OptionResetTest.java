package dev.marie.framework.ui.toolbox;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** "Reset This Tab" goes through each row's resetToDefault: it writes the default through the setter, then commits. */
class OptionResetTest {

    @Test
    void sliderResetWritesItsDefaultThenCommits() {
        double[] value = {0.9};
        AtomicInteger commits = new AtomicInteger();
        SliderOption slider = new SliderOption("s", () -> value[0], v -> value[0] = v, 0.0, 2.0, 0.01, commits::incrementAndGet);
        slider.defaultTo(1.0);
        slider.resetToDefault();
        assertEquals(1.0, value[0]);
        assertEquals(1, commits.get());
    }

    @Test
    void sliderDefaultIsClampedIntoItsRange() {
        double[] value = {0.5};
        SliderOption slider = new SliderOption("s", () -> value[0], v -> value[0] = v, 0.0, 1.0, 0.01, () -> {});
        slider.defaultTo(7.0);
        slider.resetToDefault();
        assertEquals(1.0, value[0]);
    }

    @Test
    void toggleAndCycleResetToTheirDefaults() {
        boolean[] on = {false};
        ToggleOption toggle = new ToggleOption("t", () -> on[0], v -> on[0] = v, () -> {});
        toggle.defaultTo(true);
        toggle.resetToDefault();
        assertEquals(true, on[0]);

        int[] index = {2};
        CycleOption cycle = new CycleOption("c", new String[]{"a", "b", "c"}, () -> index[0], v -> index[0] = v, () -> {});
        cycle.defaultTo(0);
        cycle.resetToDefault();
        assertEquals(0, index[0]);
    }

    @Test
    void aRowWithNoDefaultIsLeftAlone() {
        double[] value = {0.9};
        AtomicInteger commits = new AtomicInteger();
        SliderOption slider = new SliderOption("s", () -> value[0], v -> value[0] = v, 0.0, 2.0, 0.01, commits::incrementAndGet);
        slider.resetToDefault();
        assertEquals(0.9, value[0]);
        assertEquals(0, commits.get());
    }

    @Test
    void customResetReplacesThePlainDefault() {
        double[] value = {0.9};
        boolean[] ran = {false};
        SliderOption slider = new SliderOption("s", () -> value[0], v -> value[0] = v, 0.0, 2.0, 0.01, () -> {});
        slider.resetWith(() -> ran[0] = true);
        slider.resetToDefault();
        assertEquals(true, ran[0]);
        assertEquals(0.9, value[0], "the custom action ran instead of writing a number");
    }

    @Test
    void aWrongTypedDefaultIsRejected() {
        SliderOption slider = new SliderOption("s", () -> 0, v -> {}, 0.0, 1.0, 0.1, () -> {});
        assertThrows(IllegalStateException.class, () -> slider.defaultTo(true));
    }
}
