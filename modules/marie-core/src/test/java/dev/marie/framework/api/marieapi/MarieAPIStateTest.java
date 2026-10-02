package dev.marie.framework.api.marieapi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarieAPIStateTest {

    @BeforeEach
    @AfterEach
    void reset() {
        MarieAPIState.resetForTests();
    }

    @Test
    void reloadScopeDuringModInitDoesNotCloseTheModInitWindow() {
        try (MarieAPIState.DatapackReloadScope scope = MarieAPIState.openForDatapackReload()) {
            assertEquals(MarieAPIState.Phase.DATAPACK_RELOAD, MarieAPIState.getPhase());
        }
        assertEquals(MarieAPIState.Phase.MOD_INIT, MarieAPIState.getPhase());
        assertDoesNotThrow(() -> MarieAPIState.assertRegistrationAllowed("x"));
    }

    @Test
    void closeDoesNotCutOffAnOpenReloadScope() {
        try (MarieAPIState.DatapackReloadScope scope = MarieAPIState.openForDatapackReload()) {
            MarieAPIState.close();
            assertDoesNotThrow(() -> MarieAPIState.assertRegistrationAllowed("x"));
        }
        assertEquals(MarieAPIState.Phase.CLOSED, MarieAPIState.getPhase());
        assertThrows(IllegalStateException.class, () -> MarieAPIState.assertRegistrationAllowed("x"));
    }

    @Test
    void nestedScopesStayOpenUntilTheOutermostCloses() {
        MarieAPIState.close();
        MarieAPIState.DatapackReloadScope outer = MarieAPIState.openForDatapackReload();
        MarieAPIState.DatapackReloadScope inner = MarieAPIState.openForDatapackReload();
        inner.close();
        inner.close(); // double close must not underflow the depth
        assertEquals(MarieAPIState.Phase.DATAPACK_RELOAD, MarieAPIState.getPhase());
        outer.close();
        assertEquals(MarieAPIState.Phase.CLOSED, MarieAPIState.getPhase());
    }
}
