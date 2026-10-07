package dev.marie.framework.config;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigRegistryTest {

    private static final ConfigCategory CATEGORY = new ConfigCategory("test.category", "Test", 0);

    @AfterEach
    void resetRegistries() {
        ConfigRegistry.resetInternal();
        ConfigCategoryRegistry.resetInternal();
    }

    @Test
    void registerThenGetRoundTrips() {
        AtomicBoolean backing = new AtomicBoolean(true);
        ConfigValueDefinition<Boolean> def = new ConfigValueDefinition<>(
                "test.flag", "testmod", CATEGORY, ConfigValueType.BOOLEAN,
                true, backing::get, backing::set, "Flag", null);

        ConfigRegistry.register(def);

        assertEquals(def, ConfigRegistry.get("test.flag"));
        assertTrue(ConfigRegistry.getAll().contains(def));
        assertTrue(ConfigRegistry.getForCategory(CATEGORY).contains(def));
        assertTrue(ConfigRegistry.getForMod("testmod").contains(def));
    }

    @Test
    void getUnknownIdReturnsNull() {
        assertNull(ConfigRegistry.get("does.not.exist"));
    }

    @Test
    void duplicateIdThrows() {
        AtomicInteger backing = new AtomicInteger(0);
        ConfigValueDefinition<Integer> def = new ConfigValueDefinition<>(
                "test.count", "testmod", CATEGORY, ConfigValueType.INT,
                0, backing::get, backing::set, "Count", null);

        ConfigRegistry.register(def);

        assertThrows(IllegalStateException.class, () -> ConfigRegistry.register(def));
    }

    @Test
    void getSetRouteThroughSuppliedAccessors() {
        AtomicReference<String> backing = new AtomicReference<>("initial");
        ConfigValueDefinition<String> def = new ConfigValueDefinition<>(
                "test.name", "testmod", CATEGORY, ConfigValueType.STRING,
                "initial", backing::get, backing::set, "Name", null);

        assertEquals("initial", def.get());
        def.set("changed");
        assertEquals("changed", backing.get());
    }

    @Test
    void exportThenImportRoundTripsBooleanIntAndString() {
        AtomicBoolean boolBacking = new AtomicBoolean(true);
        AtomicInteger intBacking = new AtomicInteger(5);
        AtomicReference<String> stringBacking = new AtomicReference<>("hello");

        ConfigRegistry.register(new ConfigValueDefinition<>(
                "test.bool", "testmod", CATEGORY, ConfigValueType.BOOLEAN,
                true, boolBacking::get, boolBacking::set, "Bool", null));
        ConfigRegistry.register(new ConfigValueDefinition<>(
                "test.int", "testmod", CATEGORY, ConfigValueType.INT,
                5, intBacking::get, intBacking::set, "Int", null));
        ConfigRegistry.register(new ConfigValueDefinition<>(
                "test.string", "testmod", CATEGORY, ConfigValueType.STRING,
                "hello", stringBacking::get, stringBacking::set, "String", null));

        JsonObject exported = ConfigSerialization.exportAll();
        assertTrue(exported.get("test.bool").getAsBoolean());
        assertEquals(5, exported.get("test.int").getAsInt());
        assertEquals("hello", exported.get("test.string").getAsString());

        boolBacking.set(false);
        intBacking.set(99);
        stringBacking.set("changed");

        ConfigSerialization.importAll(exported);

        // Re-importing the earlier export restores what was captured at export time (true/5/"hello"),
        // overwriting the changes made in between.
        assertTrue(boolBacking.get());
        assertEquals(5, intBacking.get());
        assertEquals("hello", stringBacking.get());
    }

    @Test
    void importIgnoresUnknownIds() {
        JsonObject json = new JsonObject();
        json.addProperty("not.registered", true);

        ConfigSerialization.importAll(json);
        // No exception, no registered value touched — nothing to assert beyond "didn't throw".
    }
}
