package dev.marie.framework.ui.modulesettings;

import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.geometry.Bounds;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class ModuleExtentsTest {

    private static PersistenceProvider store() {
        return (PersistenceProvider) Proxy.newProxyInstance(PersistenceProvider.class.getClassLoader(),
                new Class<?>[]{PersistenceProvider.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> method.getReturnType() == java.util.Optional.class ? java.util.Optional.empty() : null;
                });
    }

    @Test
    void recordsUnionPerKindAndForgetsOnBegin() {
        PersistenceProvider p = store();
        ModuleExtents.begin(p, "m");
        ModuleExtents.add(p, "m", ModuleExtents.Kind.ICON, 10, 20, 16, 16);
        ModuleExtents.add(p, "m", ModuleExtents.Kind.ICON, 10, 40, 16, 16);
        ModuleExtents.add(p, "m", ModuleExtents.Kind.TEXT, 30, 20, 40, 9);
        assertEquals(new Bounds(10, 20, 16, 36), ModuleExtents.of(p, "m", ModuleExtents.Kind.ICON));
        assertEquals(new Bounds(10, 20, 60, 36), ModuleExtents.all(p, "m"));
        assertNull(ModuleExtents.of(p, "m", ModuleExtents.Kind.BAR));
        assertNull(ModuleExtents.of(p, "other", ModuleExtents.Kind.ICON));
        ModuleExtents.begin(p, "m");
        assertNull(ModuleExtents.all(p, "m"));
    }

    @Test
    void ignoresEmptyRectangles() {
        PersistenceProvider p = store();
        ModuleExtents.begin(p, "m");
        ModuleExtents.add(p, "m", ModuleExtents.Kind.TEXT, 5, 5, 0, 9);
        assertNull(ModuleExtents.of(p, "m", ModuleExtents.Kind.TEXT));
    }
}
