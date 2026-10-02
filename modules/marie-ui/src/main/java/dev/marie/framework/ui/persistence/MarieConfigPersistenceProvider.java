package dev.marie.framework.ui.persistence;

import dev.marie.framework.api.ApiStatus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import dev.marie.framework.core.MarieCore;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.component.ComponentState;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The only {@link PersistenceProvider} MarieUI ships: one JSON file under config/, one entry per
 * component id, following the same Gson-backed convention marie-core uses for its own config
 * (see MariesLibConfigIO). File is named {@code <modId>-ui-state.json} per consumer. A future
 * server-sync or per-world provider is a drop-in replacement — callers depend only on the
 * {@link PersistenceProvider} interface.
 */
@ApiStatus.Internal
public final class MarieConfigPersistenceProvider implements PersistenceProvider {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // old shared filename from before per-consumer namespacing; migrated on first load
    private static final String LEGACY_FILE_NAME = "marieslib-ui-state.json";

    private final String fileName;
    private final Map<String, ComponentState> cache = new HashMap<>();
    private boolean loaded;

    public MarieConfigPersistenceProvider(String modId) {
        this.fileName = modId + "-ui-state.json";
    }

    @Override
    public synchronized Optional<ComponentState> load(String componentId) {
        ensureLoaded();
        return Optional.ofNullable(cache.get(componentId));
    }

    @Override
    public synchronized void save(String componentId, ComponentState state) {
        ensureLoaded();
        cache.put(componentId, state);
        writeAll();
    }

    @Override
    public synchronized void remove(String componentId) {
        ensureLoaded();
        if (cache.remove(componentId) != null) {
            writeAll();
        }
    }

    private Path filePath() {
        return FMLPaths.CONFIGDIR.get().resolve(fileName);
    }

    private void migrateLegacyFileIfNeeded() {
        Path file = filePath();
        if (Files.exists(file)) {
            return;
        }
        Path legacy = FMLPaths.CONFIGDIR.get().resolve(LEGACY_FILE_NAME);
        if (!Files.exists(legacy)) {
            return;
        }
        try {
            Files.copy(legacy, file);
        } catch (IOException e) {
            MarieCore.LOGGER.error("[MarieUI] Failed to migrate legacy {} to {}", legacy, file, e);
        }
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        migrateLegacyFileIfNeeded();
        Path file = filePath();
        if (!Files.exists(file)) {
            return;
        }
        try (Reader r = Files.newBufferedReader(file)) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
            if (root == null) {
                return;
            }
            for (String key : root.keySet()) {
                JsonObject o = root.getAsJsonObject(key);
                cache.put(key, new ComponentState(
                        o.get("x").getAsInt(),
                        o.get("y").getAsInt(),
                        o.get("width").getAsInt(),
                        o.get("height").getAsInt(),
                        o.has("collapsed") && o.get("collapsed").getAsBoolean(),
                        o.has("widthManual") && o.get("widthManual").getAsBoolean(),
                        o.has("heightManual") && o.get("heightManual").getAsBoolean(),
                        o.has("leftMargin") ? o.get("leftMargin").getAsInt() : 0,
                        o.has("contentScale") ? o.get("contentScale").getAsDouble() : ComponentState.DEFAULT_CONTENT_SCALE,
                        o.has("paddingScale") ? o.get("paddingScale").getAsDouble() : ComponentState.DEFAULT_PADDING_SCALE
                ));
            }
        } catch (IOException e) {
            MarieCore.LOGGER.error("[MarieUI] Failed to load {}, discarding cached component state", file, e);
        }
    }

    private void writeAll() {
        Path file = filePath();
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            for (Map.Entry<String, ComponentState> entry : cache.entrySet()) {
                ComponentState s = entry.getValue();
                JsonObject o = new JsonObject();
                o.addProperty("x", s.x());
                o.addProperty("y", s.y());
                o.addProperty("width", s.width());
                o.addProperty("height", s.height());
                o.addProperty("collapsed", s.collapsed());
                o.addProperty("widthManual", s.widthManual());
                o.addProperty("heightManual", s.heightManual());
                o.addProperty("leftMargin", s.leftMargin());
                o.addProperty("contentScale", s.contentScale());
                o.addProperty("paddingScale", s.paddingScale());
                root.add(entry.getKey(), o);
            }
            try (Writer w = Files.newBufferedWriter(file)) {
                GSON.toJson(root, w);
            }
        } catch (IOException e) {
            MarieCore.LOGGER.error("[MarieUI] Failed to save {}", file, e);
        }
    }
}
