package dev.marie.framework.network;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarieRequestChannelTest {

    @Test
    void underBudgetPassesThrough() {
        assertEquals(List.of("a", "b"), MarieRequestChannel.clampLines(List.of("a", "b"), 1000));
    }

    @Test
    void overBudgetEndsWithMarkerCountingDroppedLines() {
        List<String> out = MarieRequestChannel.clampLines(List.of("aaaa", "bbbb", "cccc", "dddd"), 20);
        assertEquals(List.of("aaaa", "bbbb", "... truncated, 2 more line(s)"), out);
    }

    @Test
    void longLineIsCut() {
        String huge = "x".repeat(5000);
        List<String> out = MarieRequestChannel.clampLines(List.of(huge), 1_000_000);
        assertEquals(MarieRequestChannel.MAX_LINE_CHARS, out.get(0).length());
    }
}
