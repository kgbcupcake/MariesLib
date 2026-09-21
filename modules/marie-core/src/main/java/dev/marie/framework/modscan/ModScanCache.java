package dev.marie.framework.modscan;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.api.modscan.ModFileInfo;
import dev.marie.framework.core.MarieCore;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * On-disk cache of extractor results under {@code <root>/<namespace>/}, one file per extractor.
 * An entry is valid only while its key (file fingerprint + extractor id + extractor version) matches,
 * so a changed file or a bumped extractor version invalidates exactly the affected entries.
 * A file that is corrupt, from another schema version or for another extractor is discarded and
 * rebuilt; nothing here throws for bad data. Writes go to a temp file and are moved into place.
 */
@ApiStatus.Internal
public final class ModScanCache {

    public static final int SCHEMA_VERSION = 1;

    static final String INVENTORY_NAMESPACE = "marieslib";
    private static final String INVENTORY_FILE = "inventory.json";
    private static final Gson GSON = new Gson();

    private static final class Entry {
        final String key;
        final JsonElement result;

        Entry(String key, JsonElement result) {
            this.key = key;
            this.result = result;
        }
    }

    private static final class Store {
        final Map<String, Entry> entries = new LinkedHashMap<>();
        boolean dirty;
    }

    private final Path root;
    private final Map<String, Store> stores = new HashMap<>();

    public ModScanCache(Path root) {
        this.root = root;
    }

    // ── extractor results ───────────────────────────────────────

    public synchronized Optional<JsonElement> lookup(String namespace, String extractorId, int version, ModFileInfo file) {
        Entry e = store(namespace, extractorId).entries.get(file.path());
        if (e != null && e.key.equals(key(file, extractorId, version))) {
            return Optional.of(e.result);
        }
        return Optional.empty();
    }

    public synchronized void store(String namespace, String extractorId, int version, ModFileInfo file, JsonElement result) {
        Store s = store(namespace, extractorId);
        s.entries.put(file.path(), new Entry(key(file, extractorId, version), result));
        s.dirty = true;
    }

    /** Drops entries whose file is no longer installed. */
    public synchronized void retain(String namespace, String extractorId, Set<String> livePaths) {
        Store s = store(namespace, extractorId);
        if (s.entries.keySet().removeIf(p -> !livePaths.contains(p))) {
            s.dirty = true;
        }
    }

    /** Writes every changed extractor file. A failed write is logged; the scan result is unaffected. */
    public synchronized void flush() {
        for (Map.Entry<String, Store> e : stores.entrySet()) {
            Store s = e.getValue();
            if (!s.dirty) {
                continue;
            }
            String[] parts = e.getKey().split("\u0000", 2);
            JsonObject root = new JsonObject();
            root.addProperty("schemaVersion", SCHEMA_VERSION);
            root.addProperty("extractorId", parts[1]);
            JsonObject entries = new JsonObject();
            for (Map.Entry<String, Entry> en : new TreeMap<>(s.entries).entrySet()) {
                JsonObject o = new JsonObject();
                o.addProperty("key", en.getValue().key);
                o.add("result", en.getValue().result);
                entries.add(en.getKey(), o);
            }
            root.add("entries", entries);
            if (write(extractorFile(parts[0], parts[1]), root)) {
                s.dirty = false;
            }
        }
    }

    private Store store(String namespace, String extractorId) {
        return stores.computeIfAbsent(namespace + '\u0000' + extractorId, k -> load(namespace, extractorId));
    }

    private Store load(String namespace, String extractorId) {
        Store s = new Store();
        Path file = extractorFile(namespace, extractorId);
        if (!Files.exists(file)) {
            return s;
        }
        try {
            JsonObject root = read(file);
            if (root == null || !root.has("schemaVersion") || root.get("schemaVersion").getAsInt() != SCHEMA_VERSION) {
                throw new IllegalStateException("schema version " + (root == null ? "missing" : root.get("schemaVersion")));
            }
            if (!extractorId.equals(root.get("extractorId").getAsString())) {
                throw new IllegalStateException("belongs to extractor " + root.get("extractorId"));
            }
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("entries").entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                s.entries.put(e.getKey(), new Entry(o.get("key").getAsString(), o.has("result") ? o.get("result") : JsonNull.INSTANCE));
            }
        } catch (IOException | RuntimeException ex) {
            MarieCore.LOGGER.warn("[ModScan] Discarding cache {} ({}), it will be rebuilt", file, ex.toString());
            s.entries.clear();
            s.dirty = true;
        }
        return s;
    }

    // ── previous inventory (for the diff) ───────────────────────

    /** The inventory saved by the last completed scan, or empty if none, unreadable or another schema. */
    public synchronized Optional<List<ModFileInfo>> loadInventory() {
        Path file = root.resolve(INVENTORY_NAMESPACE).resolve(INVENTORY_FILE);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            JsonObject root = read(file);
            if (root == null || root.get("schemaVersion").getAsInt() != SCHEMA_VERSION) {
                throw new IllegalStateException("schema version mismatch");
            }
            List<ModFileInfo> out = new ArrayList<>();
            for (JsonElement el : root.getAsJsonArray("files")) {
                JsonObject o = el.getAsJsonObject();
                Map<String, String> mods = new TreeMap<>();
                for (Map.Entry<String, JsonElement> m : o.getAsJsonObject("mods").entrySet()) {
                    mods.put(m.getKey(), m.getValue().getAsString());
                }
                out.add(new ModFileInfo(o.get("path").getAsString(), o.get("size").getAsLong(),
                        o.get("mtime").getAsLong(), ModFileInfo.Kind.valueOf(o.get("kind").getAsString()), mods));
            }
            return Optional.of(out);
        } catch (IOException | RuntimeException ex) {
            MarieCore.LOGGER.warn("[ModScan] Discarding saved inventory {} ({}), treating every file as new", file, ex.toString());
            return Optional.empty();
        }
    }

    public synchronized void saveInventory(List<ModFileInfo> files) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        JsonArray arr = new JsonArray();
        for (ModFileInfo f : files) {
            JsonObject o = new JsonObject();
            o.addProperty("path", f.path());
            o.addProperty("size", f.size());
            o.addProperty("mtime", f.mtime());
            o.addProperty("kind", f.kind().name());
            JsonObject mods = new JsonObject();
            f.mods().forEach(mods::addProperty);
            o.add("mods", mods);
            arr.add(o);
        }
        root.add("files", arr);
        write(this.root.resolve(INVENTORY_NAMESPACE).resolve(INVENTORY_FILE), root);
    }

    // ── io ──────────────────────────────────────────────────────

    private static String key(ModFileInfo file, String extractorId, int version) {
        return file.fingerprint() + '|' + extractorId + '|' + version;
    }

    private Path extractorFile(String namespace, String extractorId) {
        return root.resolve(safe(namespace)).resolve("extractor-" + safe(extractorId) + ".json");
    }

    private static String safe(String s) {
        return s.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static JsonObject read(Path file) throws IOException {
        try (Reader r = Files.newBufferedReader(file)) {
            return GSON.fromJson(r, JsonObject.class);
        }
    }

    private static boolean write(Path file, JsonObject json) {
        Path tmp = null;
        try {
            Files.createDirectories(file.getParent());
            tmp = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp)) {
                GSON.toJson(json, w);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            MarieCore.LOGGER.warn("[ModScan] Could not write {}: {}", file, e.toString());
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // best effort
                }
            }
            return false;
        }
    }
}
