package dev.marie.framework.ui.widget;

import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.Theme;
import dev.marie.framework.ui.geometry.Bounds;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WidgetsTest {

    /** Records fillRect and drawText calls; drawLine and the rest use their defaults or no-op. */
    static RenderContext recording(List<int[]> fills, List<String> texts) {
        return (RenderContext) Proxy.newProxyInstance(RenderContext.class.getClassLoader(), new Class<?>[]{RenderContext.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "fillRect" -> fills.add(new int[]{(Integer) args[0], (Integer) args[1], (Integer) args[2], (Integer) args[3]});
                        case "drawText" -> texts.add((String) args[0]);
                        case "theme" -> { return Theme.DARK; }
                        case "textWidth" -> { return ((String) args[0]).length() * 5; }
                        case "drawLine" -> {
                            // default method: replay it through the proxy so fillRect is recorded
                            return java.lang.invoke.MethodHandles.privateLookupIn(RenderContext.class, java.lang.invoke.MethodHandles.lookup())
                                    .unreflectSpecial(method, RenderContext.class).bindTo(proxy).invokeWithArguments(args);
                        }
                        default -> { }
                    }
                    Class<?> r = method.getReturnType();
                    return r == int.class ? 0 : r == float.class ? 0f : r == boolean.class ? Boolean.FALSE : null;
                });
    }

    @Test
    void wrapSplitsOnWordsAndLongWords() {
        assertEquals(List.of("aaa bbb", "ccc"), MarieTextList.wrap("aaa bbb ccc", 35, s -> s.length() * 5));
        assertEquals(List.of("abcd", "efgh", "i"), MarieTextList.wrap("abcdefghi", 20, s -> s.length() * 5));
        assertEquals(List.of(""), MarieTextList.wrap("", 20, s -> s.length() * 5));
    }

    @Test
    void textListFollowsTailUntilScrolledUp() {
        MarieTextList list = new MarieTextList("t", 100);
        for (int i = 0; i < 30; i++) {
            list.add("line " + i);
        }
        List<String> texts = new ArrayList<>();
        Bounds b = new Bounds(0, 0, 100, 50); // 5 rows
        list.render(recording(new ArrayList<>(), texts), b);
        assertEquals("line 29", texts.get(texts.size() - 1));

        assertTrue(list.mouseScrolled(10, 10, 0, 1)); // wheel up
        texts.clear();
        list.render(recording(new ArrayList<>(), texts), b);
        assertNotEquals("line 29", texts.get(texts.size() - 1));
        assertFalse(list.mouseScrolled(500, 500, 0, 1));
    }

    @Test
    void textListDropsOldestBeyondMax() {
        MarieTextList list = new MarieTextList("t", 3);
        for (int i = 0; i < 5; i++) {
            list.add("l" + i);
        }
        assertEquals(3, list.lineCount());
    }

    @Test
    void pickerSelectsClickedRow() {
        List<Integer> picked = new ArrayList<>();
        MarieListPicker picker = new MarieListPicker("p", picked::add);
        picker.setItems(List.of("a", "b", "c"));
        picker.render(recording(new ArrayList<>(), new ArrayList<>()), new Bounds(0, 0, 100, 60));
        assertTrue(picker.mouseClicked(5, MarieListPicker.ROW_HEIGHT + 2, 0));
        assertEquals(List.of(1), picked);
        assertFalse(picker.mouseClicked(5, 50, 0)); // below the last row
    }

    @Test
    void graphKeepsNewestSamplesOldestFirst() {
        MarieGraph g = new MarieGraph("g", 3);
        for (int i = 1; i <= 5; i++) {
            g.push(i);
        }
        assertArrayEquals(new double[]{3, 4, 5}, g.samples());
    }

    @Test
    void graphRangeIsUsableAndPlotStaysInside() {
        double[] range = MarieGraph.yRange(new double[]{5, 5, 5});
        assertTrue(range[1] > range[0]);
        for (double v : new double[]{-1e9, 5, 1e9}) {
            int y = MarieGraph.plotY(v, range, 40);
            assertTrue(y >= 0 && y <= 39);
        }
    }

    @Test
    void drawLineCoversBothEndpointsAndCollapsesStraightRuns() {
        List<int[]> fills = new ArrayList<>();
        RenderContext ctx = recording(fills, new ArrayList<>());
        ctx.drawLine(2, 3, 9, 3, 0xFFFFFFFF);
        assertEquals(1, fills.size());
        assertArrayEquals(new int[]{2, 3, 8, 1}, fills.get(0));

        fills.clear();
        ctx.drawLine(0, 0, 4, 2, 0xFFFFFFFF);
        assertTrue(fills.stream().anyMatch(f -> f[0] == 0 && f[1] == 0));
        assertTrue(fills.stream().anyMatch(f -> f[0] == 4 && f[1] == 2));
    }
}
