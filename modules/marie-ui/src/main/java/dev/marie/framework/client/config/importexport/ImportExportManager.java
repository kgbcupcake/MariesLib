package dev.marie.framework.client.config.importexport;

import dev.marie.framework.api.ApiStatus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import dev.marie.framework.config.MariesLibConfigBridge;
import dev.marie.framework.core.MarieCore;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Serializes and restores MarieCore scanner/diagnostics settings for file export and share codes.
 */
@ApiStatus.Internal
public final class ImportExportManager {

    public static final int SCHEMA_VERSION = 1;
    private static final String SHARE_PREFIX = "MARIESLIBCF1:";

    private static final Gson GSON_COMPACT = new GsonBuilder().create();
    private static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public enum Section {
        SCANNER("scanner"),
        DEBUG("debug");

        private final String jsonKey;

        Section(String jsonKey) {
            this.jsonKey = jsonKey;
        }

        public String jsonKey() {
            return jsonKey;
        }

        public Component label() {
            return Component.translatable("config." + MarieCore.MOD_ID + ".importExport.section." + jsonKey);
        }

        public static Section fromJsonKey(String key) {
            for (Section s : values()) {
                if (s.jsonKey.equals(key)) {
                    return s;
                }
            }
            return null;
        }
    }

    private ImportExportManager() {}

    public static String sharePrefix() {
        return SHARE_PREFIX;
    }

    public static Path exportsDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve(MarieCore.MOD_ID).resolve("exports");
    }

    public static Path writeExportFile(JsonObject root) throws IOException {
        Files.createDirectories(exportsDirectory());
        String stem = MarieCore.MOD_ID + "-config-" + LocalDateTime.now().format(FILE_TS);
        Path file = exportsDirectory().resolve(stem + ".json");
        try (Writer w = Files.newBufferedWriter(file)) {
            GSON_PRETTY.toJson(root, w);
        }
        return file;
    }

    public static JsonObject buildExportRoot(Set<Section> sections) {
        JsonObject full = MariesLibConfigBridge.buildExportRoot();
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        for (Section s : sections) {
            if (full.has(s.jsonKey()) && !full.get(s.jsonKey()).isJsonNull()) {
                root.add(s.jsonKey(), full.get(s.jsonKey()));
            }
        }
        return root;
    }

    public static String buildShareCode(JsonObject root) throws IOException {
        String json = GSON_COMPACT.toJson(root);
        byte[] gzipped = gzipUtf8(json);
        return sharePrefix() + Base64.getEncoder().encodeToString(gzipped);
    }

    public static JsonObject parseShareCode(String raw) throws IOException {
        if (raw == null) {
            throw new IOException("empty");
        }
        String prefix = sharePrefix();
        String flat = raw.replaceAll("\\s+", "");
        if (!flat.regionMatches(true, 0, prefix, 0, prefix.length())) {
            throw new IOException("missing prefix");
        }
        String b64 = flat.substring(prefix.length());
        byte[] decoded = Base64.getDecoder().decode(b64);
        if (decoded.length > 65536) {
            throw new IOException("share code payload too large: " + decoded.length + " bytes");
        }
        String json = gunzipUtf8(decoded);
        JsonObject obj = GSON_COMPACT.fromJson(json, JsonObject.class);
        if (obj == null) {
            throw new IOException("not json object");
        }
        return obj;
    }

    public static JsonObject parseJsonFile(Path file) throws IOException {
        try (Reader r = Files.newBufferedReader(file)) {
            JsonObject obj = GSON_COMPACT.fromJson(r, JsonObject.class);
            if (obj == null) {
                throw new IOException("empty file");
            }
            return obj;
        }
    }

    public static EnumSet<Section> sectionsPresent(JsonObject root) {
        EnumSet<Section> out = EnumSet.noneOf(Section.class);
        for (Section s : Section.values()) {
            if (root.has(s.jsonKey()) && !root.get(s.jsonKey()).isJsonNull()) {
                out.add(s);
            }
        }
        return out;
    }

    public static void applyImport(JsonObject root, Set<Section> selected) throws IOException {
        JsonObject filtered = new JsonObject();
        filtered.addProperty("schemaVersion", SCHEMA_VERSION);
        for (Section s : selected) {
            if (root.has(s.jsonKey()) && !root.get(s.jsonKey()).isJsonNull()) {
                filtered.add(s.jsonKey(), root.get(s.jsonKey()));
            }
        }
        MariesLibConfigBridge.applyImport(filtered);
    }

    public static List<Path> listExportJsonFiles() throws IOException {
        Path dir = exportsDirectory();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Path> out = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .sorted()
                    .forEach(out::add);
        }
        return out;
    }

    private static byte[] gzipUtf8(String s) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(s.getBytes(StandardCharsets.UTF_8));
        }
        return baos.toByteArray();
    }

    @com.google.common.annotations.VisibleForTesting
    static String gunzipUtf8(byte[] data) throws IOException {
        final int maxBytes = 1_048_576;
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(data));
             Reader r = new InputStreamReader(in, StandardCharsets.UTF_8);
             StringWriter sw = new StringWriter()) {
            char[] buf = new char[8192];
            int total = 0;
            int n;
            while ((n = r.read(buf)) != -1) {
                total += n;
                if (total > maxBytes) {
                    throw new IOException("decompressed payload exceeds 1 MiB");
                }
                sw.write(buf, 0, n);
            }
            return sw.toString();
        }
    }
}
