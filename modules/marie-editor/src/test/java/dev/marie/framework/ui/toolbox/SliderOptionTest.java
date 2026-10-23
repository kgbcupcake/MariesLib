package dev.marie.framework.ui.toolbox;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SliderOptionTest {

    @Test
    void intSliderHandsTheSetterWholeNumbersAndCommitsOnReset() {
        List<Integer> written = new ArrayList<>();
        AtomicInteger commits = new AtomicInteger();
        SliderOption slider = SliderOption.ofInt("Width", () -> 60, written::add, 40, 120, 1, "px", commits::incrementAndGet);

        slider.defaultTo(52.0d);
        slider.resetToDefault();
        slider.defaultTo(500.0d);
        slider.resetToDefault();

        assertEquals(List.of(52, 120), written, "defaults are clamped to the range and arrive as ints");
        assertEquals(2, commits.get());
    }

    @Test
    void decimalSliderShowsPlacesAndUnit() {
        SliderOption kelvin = SliderOption.ofDecimal("Ref", () -> 293.15, v -> {}, 200, 400, 0.05, 2, "K", () -> {});
        SliderOption bare = SliderOption.ofDecimal("Coef", () -> 0.05, v -> {}, 0, 1, 0.005, 3, "", () -> {});
        SliderOption percent = new SliderOption("Opacity", () -> 0.5, v -> {}, 0, 1, 0.01, () -> {});

        assertEquals("293.15 K", kelvin.formatValue(293.15));
        assertEquals("0.050", bare.formatValue(0.05));
        assertEquals("50%", percent.formatValue(0.5));
    }

    @Test
    void decimalSliderResetWritesTheClampedDefault() {
        List<Double> written = new ArrayList<>();
        SliderOption slider = SliderOption.ofDecimal("Coef", () -> 0.05, written::add, 0, 1, 0.005, 3, null, () -> {});

        slider.defaultTo(0.15);
        slider.resetToDefault();
        slider.defaultTo(5.0);
        slider.resetToDefault();

        assertEquals(List.of(0.15, 1.0), written);
    }

    @Test
    void panelBuilderTakesADoubleDefaultOnAnIntSlider() {
        dev.marie.framework.ui.api.MarieToolbox.panel("p").tab("t")
                .intSlider("Width", () -> 60, v -> {}, 40, 120, 1, "px", () -> {})
                .defaultValue(60.0d)
                .build();
    }
}
