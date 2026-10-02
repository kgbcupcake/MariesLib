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
    void panelBuilderTakesADoubleDefaultOnAnIntSlider() {
        dev.marie.framework.ui.api.MarieToolbox.panel("p").tab("t")
                .intSlider("Width", () -> 60, v -> {}, 40, 120, 1, "px", () -> {})
                .defaultValue(60.0d)
                .build();
    }
}
