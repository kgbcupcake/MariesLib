package dev.marie.framework.ui.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveDragTest {

    @Test
    void singleModeOffsetFollowsThePointerFromTheGrabPoint() {
        MoveDrag drag = new MoveDrag();
        assertFalse(drag.isActive());

        drag.start(MoveDrag.Mode.ICONS, 100, 50, 10, -4);   // offset was (10, -4) at the press
        assertTrue(drag.isActive());
        assertEquals(MoveDrag.Mode.ICONS, drag.mode());
        assertEquals(10, drag.offsetX(100), "no movement yet: offset unchanged");
        assertEquals(25, drag.offsetX(115));
        assertEquals(-14, drag.offsetY(40));

        drag.stop();
        assertFalse(drag.isActive());
    }

    @Test
    void allModeReportsMovementSinceThePressAndKeepsEachBaseOffset() {
        MoveDrag drag = new MoveDrag();
        drag.startAll(200, 100, 1, 2, 3, 4, 5, 6);

        assertEquals(MoveDrag.Mode.ALL, drag.mode());
        assertEquals(0, drag.offsetX(200));
        assertEquals(12, drag.offsetX(212));
        assertEquals(-8, drag.offsetY(92));
        assertEquals(1, drag.baseX(MoveDrag.Mode.TEXT));
        assertEquals(4, drag.baseY(MoveDrag.Mode.ICONS));
        assertEquals(5, drag.baseX(MoveDrag.Mode.BARS));
    }
}
