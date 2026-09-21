package dev.marie.framework.ui.api;

import dev.marie.framework.ui.geometry.Bounds;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.Theme;

import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MarieWidgetsTest {

    /** A RenderContext where every draw is a no-op; text is 5 px per character. */
    private static RenderContext ctx() {
        return (RenderContext) Proxy.newProxyInstance(RenderContext.class.getClassLoader(), new Class<?>[]{RenderContext.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "theme" -> { return Theme.DARK; }
                        case "textWidth" -> { return ((String) args[0]).length() * 5; }
                        default -> { }
                    }
                    Class<?> r = method.getReturnType();
                    return r == int.class ? 0 : r == float.class ? 0f : r == boolean.class ? Boolean.FALSE : null;
                });
    }

    private static final Bounds BOUNDS = new Bounds(0, 0, 200, 40);

    @Test
    void tabBarReportsOnlyClicksThatMoveTheSelection() {
        AtomicInteger changes = new AtomicInteger();
        MarieWidgets.TabBar bar = MarieWidgets.tabBar("t", "A", "B").onChange(i -> changes.incrementAndGet());
        bar.render(ctx(), BOUNDS);
        assertTrue(bar.mouseClicked(150, 5, 0));
        assertEquals(1, bar.selected());
        assertEquals(1, changes.get());
        assertTrue(bar.mouseClicked(150, 5, 0), "clicking the selected tab is still a hit");
        assertEquals(1, changes.get(), "but it is not a change");
    }

    @Test
    void buttonRunsOnlyWhileEnabled() {
        AtomicInteger runs = new AtomicInteger();
        boolean[] on = {true};
        MarieWidgets.Button button = MarieWidgets.button("b", "Go", runs::incrementAndGet).enabledWhen(() -> on[0]);
        button.render(ctx(), BOUNDS);
        assertTrue(button.mouseClicked(10, 5, 0));
        assertEquals(1, runs.get());
        on[0] = false;
        assertTrue(button.mouseClicked(10, 5, 0), "a disabled button still swallows the click");
        assertEquals(1, runs.get());
    }

    @Test
    void sliderArrowStepsAndCommits() {
        double[] value = {1.0};
        AtomicInteger commits = new AtomicInteger();
        var slider = MarieWidgets.slider("S", () -> value[0], v -> value[0] = v, 0, 2, 0.5, commits::incrementAndGet);
        slider.render(ctx(), BOUNDS);
        // right arrow: last 9px of the row, in the track line under the 12px label line
        assertTrue(slider.mouseClicked(195, 16, 0));
        assertEquals(1.5, value[0]);
        assertEquals(1, commits.get());
    }

    @Test
    void sectionOpensOnHeaderClickAndRejectsForeignChildren() {
        var toggle = MarieWidgets.toggle("T", () -> false, v -> {}, () -> {});
        var section = MarieWidgets.section("Group", toggle);
        int closed = section.constraint().preferredSize().height();
        section.render(ctx(), BOUNDS);
        assertTrue(section.mouseClicked(20, 5, 0));
        assertTrue(section.constraint().preferredSize().height() > closed);
        assertThrows(IllegalArgumentException.class, () -> MarieWidgets.section("X", MarieWidgets.tabBar("t", "A")));
    }
}
