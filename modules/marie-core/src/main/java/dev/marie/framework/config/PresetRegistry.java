package dev.marie.framework.config;

import dev.marie.framework.api.ApiStatus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.marie.framework.core.MarieContext;
import dev.marie.framework.core.MarieModRegistry;
import dev.marie.framework.core.MarieCore;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Loads and writes gameplay presets under {@code config/<modid>/presets/}.
 * Marie mods may seed locked presets via {@link MarieContext#ensureBuiltInPresetsOnDisk()}.
 */
@ApiStatus.Internal
public final class PresetRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record PresetValues(JsonObject values) {
        public static PresetValues fromJsonObject(JsonObject values) {
            return new PresetValues(values != null ? values : new JsonObject());
        }

        public static PresetValues empty() {
            return new PresetValues(new JsonObject());
        }

        public JsonObject toJsonObject() {
            return values;
        }
    }

    public record ParsedPreset(
            String fileStem,
            Path path,
            String name,
            String description,
            String author,
            boolean locked,
            PresetValues values
    ) {
        public boolean canDelete() {
            return !locked;
        }

        public boolean showLockIcon() {
            return locked;
        }
    }

    private PresetRegistry() {}

    public static Path presetsDirectory() {
        String modId = MarieModRegistry.isRegistered()
                ? MarieModRegistry.getPrimary().modId()
                : MarieCore.MOD_ID;
        return FMLPaths.CONFIGDIR.get().resolve(modId).resolve("presets");
    }

    /**
     * Ensures the presets directory exists and lets the active Marie mod seed locked presets.
     */
    public static void ensureBuiltInFilesOnDisk() {
        try {
            Files.createDirectories(presetsDirectory());
        } catch (IOException e) {
            MarieCore.LOGGER.error("[MarieLib] Failed to create presets directory", e);
        }
        if (MarieContext.isRegistered()) {
            MarieContext.get().ensureBuiltInPresetsOnDisk();
        }
    }

    public static void reload() {
        ensureBuiltInFilesOnDisk();
    }

    /**
     * Lists all {@code *.json} presets in the presets folder (newest files last after built-ins).
     */
    public static List<ParsedPreset> listPresets() {
        Path dir = presetsDirectory();
        List<ParsedPreset> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .forEach(p -> {
                        try {
                            ParsedPreset parsed = parseFile(p);
                            if (parsed != null) {
                                out.add(parsed);
                            }
                        } catch (Exception e) {
                            MarieCore.LOGGER.warn("[MarieLib] Skipping invalid preset {}", p, e);
                        }
                    });
        } catch (IOException e) {
            MarieCore.LOGGER.error("[MarieLib] Failed to list presets", e);
        }
        out.sort(Comparator
                .comparing((ParsedPreset p) -> p.locked())
                .reversed()
                .thenComparing(p -> p.name().toLowerCase(Locale.ROOT)));
        return out;
    }

    public static ParsedPreset parseFile(Path file) throws IOException {
        if (Files.size(file) > 65536) {
            throw new IOException("preset file too large: " + file);
        }
        String stem = fileNameStem(file);
        try (Reader r = Files.newBufferedReader(file)) {
            return parseReader(stem, file, r);
        }
    }

    private static ParsedPreset parseReader(String fileStem, Path path, Reader reader) {
        JsonObject root = GSON.fromJson(reader, JsonObject.class);
        if (root == null) {
            return null;
        }
        String name = root.has("name") ? root.get("name").getAsString() : fileStem;
        String description = root.has("description") ? root.get("description").getAsString() : "";
        String author = root.has("author") ? root.get("author").getAsString() : "";
        boolean locked = root.has("locked") && root.get("locked").getAsBoolean();
        JsonElement valuesEl = root.get("values");
        if (valuesEl == null || !valuesEl.isJsonObject()) {
            MarieCore.LOGGER.warn("[MarieLib] Preset {} has no values object", path);
            return null;
        }
        PresetValues values = PresetValues.fromJsonObject(valuesEl.getAsJsonObject());
        return new ParsedPreset(fileStem, path, name, description, author, locked, values);
    }

    private static String fileNameStem(Path file) {
        String fn = file.getFileName().toString();
        int dot = fn.lastIndexOf('.');
        return dot > 0 ? fn.substring(0, dot) : fn;
    }

    public static void applyPresetValues(PresetValues v) {
        if (MarieContext.isRegistered()) {
            MarieContext.get().applyPresetValues(v);
        }
    }

    public static void applyPreset(ParsedPreset preset) {
        applyPresetValues(preset.values());
    }

    public static void deletePreset(ParsedPreset preset) throws IOException {
        if (!preset.canDelete()) {
            throw new IOException("Cannot delete built-in or locked preset");
        }
        Files.deleteIfExists(preset.path());
    }

    /**
     * Writes a user preset. {@code displayName} becomes the JSON {@code name}; the file stem is derived and made unique.
     */
    public static Path saveUserPreset(String displayName, String description, String author, PresetValues values) throws IOException {
        Path dir = presetsDirectory();
        Files.createDirectories(dir);
        String baseStem = sanitizeFileStem(displayName);
        if (baseStem.isEmpty()) {
            baseStem = "preset";
        }
        String stem = uniquifyStem(dir, baseStem);
        Path file = dir.resolve(stem + ".json");
        JsonObject root = new JsonObject();
        root.addProperty("name", displayName.trim());
        root.addProperty("description", description == null ? "" : description.trim());
        root.addProperty("author", author == null ? "" : author.trim());
        root.addProperty("locked", false);
        root.add("values", values.toJsonObject());
        try (Writer w = Files.newBufferedWriter(file)) {
            GSON.toJson(root, w);
        }
        MarieCore.LOGGER.info("[MarieLib] Saved preset to {}", file);
        return file;
    }

    private static String uniquifyStem(Path dir, String base) throws IOException {
        String candidate = base;
        int n = 2;
        while (Files.exists(dir.resolve(candidate + ".json"))) {
            candidate = base + "_" + n;
            n++;
        }
        return candidate;
    }

    /**
     * Lowercase file stem: letters, digits, underscore only.
     */
    public static String sanitizeFileStem(String raw) {
        if (raw == null) {
            return "";
        }
        String lower = raw.toLowerCase(Locale.ROOT).trim();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lower.length() && sb.length() < 60; i++) {
            char ch = lower.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_') {
                sb.append(ch);
            } else if (ch == ' ' || ch == '-' || ch == '.') {
                sb.append('_');
            }
        }
        String s = sb.toString().replaceAll("_+", "_");
        if (s.startsWith("_")) {
            s = s.substring(1);
        }
        if (s.endsWith("_")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

}
