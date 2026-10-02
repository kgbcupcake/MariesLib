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
        List<String> lines = java.util.stream.Stream.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j")
                .map(c -> c.repeat(4)).toList();
        List<String> out = MarieRequestChannel.clampLines(lines, 49);
        assertEquals(List.of("aaaa", "bbbb", "... truncated, 8 more line(s)"), out);
    }

    @Test
    void markerCountsAgainstTheBudget() {
        int budget = 45;
        List<String> out = MarieRequestChannel.clampLines(
                List.of("aaaa", "bbbb", "cccc", "dddd", "eeee", "ffff"), budget);
        assertEquals(List.of("aaaa", "... truncated, 5 more line(s)"), out);
        int used = out.stream().mapToInt(l -> l.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 4).sum();
        assertTrue(used <= budget, "clamped response must fit the budget, was " + used);
    }

    @Test
    void longLineIsCut() {
        String huge = "x".repeat(5000);
        List<String> out = MarieRequestChannel.clampLines(List.of(huge), 1_000_000);
        assertEquals(MarieRequestChannel.MAX_LINE_CHARS, out.get(0).length());
    }

    private static MarieRequestChannel channel() {
        return new MarieRequestChannel("testmod", "actions", "1", player -> true,
                (player, action, argument) -> new MarieRequestChannel.Response("test", List.of()));
    }

    /** A connection whose negotiated channels are exactly {@code ids}, answering hasChannel like a real listener. */
    private static net.neoforged.neoforge.common.extensions.ICommonPacketListener listenerWith(String... ids) {
        java.util.Set<String> negotiated = java.util.Set.of(ids);
        return (net.neoforged.neoforge.common.extensions.ICommonPacketListener) java.lang.reflect.Proxy.newProxyInstance(
                MarieRequestChannelTest.class.getClassLoader(),
                new Class<?>[]{net.neoforged.neoforge.common.extensions.ICommonPacketListener.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("hasChannel") && args != null && args[0] instanceof
                            net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<?> type) {
                        return negotiated.contains(type.id().toString());
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void channelsAreRequiredUnlessMadeOptional() {
        MarieRequestChannel c = channel();
        assertFalse(c.isOptional(), "the default keeps the old behaviour: a missing channel refuses the connection");
        assertSame(c, c.optional(), "optional() is a fluent setter");
        assertTrue(c.isOptional());
    }

    @Test
    void availabilityFollowsTheNegotiatedChannelsOfTheConnection() {
        MarieRequestChannel c = channel();
        assertTrue(c.isAvailable(listenerWith("testmod:actions_request", "testmod:actions_response")));
        assertFalse(c.isAvailable(listenerWith()), "server without the mod: nothing negotiated");
        assertFalse(c.isAvailable(listenerWith("othermod:actions_request")));
        assertFalse(c.isAvailable(null), "no connection at all");
    }
}
