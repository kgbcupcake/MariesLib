package dev.marie.framework.modscan;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.api.modscan.MixinFootprintEntry;
import dev.marie.framework.api.modscan.ModFileHandle;
import dev.marie.framework.api.modscan.ModFileInfo;
import dev.marie.framework.api.modscan.ModScanExtractor;
import dev.marie.framework.core.MarieCore;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads which classes a mod file's mixins target and what they add. Looks only at the mixin config
 * JSONs the file declares (in {@code META-INF/neoforge.mods.toml} {@code [[mixins]]} tables, the
 * manifest {@code MixinConfigs} attribute and Fabric's {@code fabric.mod.json "mixins"}) and at the
 * mixin classes those list, parsing bytes with ASM; no class is ever loaded. The result is a JSON
 * array with one object per (mixin, target).
 *
 * <p>Fabric-origin jars (loaded through Sinytra Connector) carry intermediary names
 * ({@code net.minecraft.class_310}). Connector keeps a Mojang-remapped copy of each such jar in
 * {@code <gamedir>/.cache/connector/<jar name>_mapped_moj_<mc version>.jar}, with a {@code .input}
 * sidecar holding the SHA-256 of the original. When that copy exists and matches the original, the
 * mixin classes are read from it and targets come out with Mojang names. Otherwise the raw
 * intermediary name is kept and the entry is flagged unresolved; nothing is dropped.</p>
 */
@ApiStatus.Internal
public final class MixinFootprintExtractor implements ModScanExtractor {

    public static final String ID = "marieslib:mixin_footprint";
    public static final String NAMESPACE = "marieslib";

    private static final String MIXIN_DESC = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String SHADOW_DESC = "Lorg/spongepowered/asm/mixin/Shadow;";
    private static final Pattern TOML_CONFIG = Pattern.compile("^\\s*config\\s*=\\s*[\"']([^\"']+)[\"']");

    private static final int BASE_VERSION = 2;
    private static final Pattern INTERMEDIARY = Pattern.compile("(^|[.$])class_\\d+");

    private final Path connectorCacheDir;
    private final int version;

    /** No Connector cache: Fabric-origin intermediary targets stay unresolved. */
    public MixinFootprintExtractor() {
        this(null);
    }

    /**
     * @param connectorCacheDir Connector's remap cache ({@code <gamedir>/.cache/connector}), or null.
     *                          Its contents are folded into {@link #version()} so results are rebuilt
     *                          when Connector adds or replaces a remapped copy.
     */
    public MixinFootprintExtractor(Path connectorCacheDir) {
        this.connectorCacheDir = connectorCacheDir;
        this.version = connectorCacheDir == null ? BASE_VERSION : Objects.hash(BASE_VERSION, cacheListing(connectorCacheDir));
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int version() {
        return version;
    }

    private static List<String> cacheListing(Path dir) {
        List<String> out = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*_mapped_moj_*.jar")) {
                for (Path p : ds) {
                    out.add(p.getFileName() + ":" + Files.size(p) + ":" + Files.getLastModifiedTime(p).toMillis());
                }
            } catch (IOException e) {
                MarieCore.LOGGER.debug("[ModScan] Could not list {}: {}", dir, e.toString());
            }
        }
        java.util.Collections.sort(out);
        return out;
    }

    @Override
    public JsonElement extract(ModFileHandle file) throws IOException {
        JsonArray out = new JsonArray();
        Set<String> seenClasses = new LinkedHashSet<>();
        Set<String> configs = declaredConfigs(file);
        ModFileHandle mapped = openConnectorCopy(file, configs);
        ModFileHandle reader = mapped != null ? mapped : file;
        try {
            readAll(reader, configs, seenClasses, out);
        } finally {
            if (mapped != null) {
                mapped.close();
            }
        }
        return out;
    }

    private void readAll(ModFileHandle file, Set<String> configs, Set<String> seenClasses, JsonArray out) throws IOException {
        for (String config : configs) {
            JsonObject json = readConfig(file, config);
            if (json == null) {
                continue;
            }
            String pkg = json.has("package") ? json.get("package").getAsString() : "";
            for (String section : List.of("mixins", "client", "server")) {
                if (!json.has(section) || !json.get(section).isJsonArray()) {
                    continue;
                }
                for (JsonElement el : json.getAsJsonArray(section)) {
                    String simple = el.getAsString();
                    String cls = pkg.isEmpty() ? simple : pkg + "." + simple;
                    if (seenClasses.add(cls)) {
                        readMixinClass(file, cls, out);
                    }
                }
            }
        }
    }

    /** Connector's Mojang-remapped copy of a Fabric-origin jar, if present and made from this exact jar. */
    private ModFileHandle openConnectorCopy(ModFileHandle file, Set<String> configs) {
        if (connectorCacheDir == null || file.info().kind() != ModFileInfo.Kind.JAR || configs.isEmpty()) {
            return null;
        }
        try {
            if (file.open("fabric.mod.json").isEmpty()) {
                return null;
            }
            Path original = Path.of(file.info().path());
            String stem = original.getFileName().toString().replaceFirst("\\.jar$", "");
            String sha = null;
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(connectorCacheDir, stem + "_mapped_moj_*.jar")) {
                for (Path candidate : ds) {
                    Path input = candidate.resolveSibling(candidate.getFileName() + ".input");
                    if (!Files.isRegularFile(input)) {
                        continue;
                    }
                    String recorded = Files.readString(input).strip();
                    recorded = recorded.substring(recorded.lastIndexOf(',') + 1);
                    if (sha == null) {
                        sha = sha256(original);
                    }
                    if (recorded.equalsIgnoreCase(sha)) {
                        return ModFileHandle.of(file.info(), List.of(candidate));
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            MarieCore.LOGGER.debug("[ModScan] No usable Connector copy for {}: {}", file.info().path(), e.toString());
        }
        return null;
    }

    private static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) {
                md.update(buf, 0, n);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : md.digest()) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Turns a cached result plus the file's owning mod id into API entries. */
    public static List<MixinFootprintEntry> toEntries(String modId, JsonElement result, String targetClassName) {
        List<MixinFootprintEntry> out = new ArrayList<>();
        if (result == null || !result.isJsonArray()) {
            return out;
        }
        String wanted = normalizeClassName(targetClassName);
        for (JsonElement el : result.getAsJsonArray()) {
            try {
                JsonObject o = el.getAsJsonObject();
                String target = o.get("target").getAsString();
                boolean unresolved = o.has("unresolved") && o.get("unresolved").getAsBoolean();
                // an unresolved (intermediary) target cannot be compared to a Mojang name, so it is
                // returned for every query, flagged, rather than dropped
                if (!unresolved && !target.equals(wanted)) {
                    continue;
                }
                out.add(new MixinFootprintEntry(modId, o.get("mixin").getAsString(), target,
                        members(o.getAsJsonArray("fields")), members(o.getAsJsonArray("methods")), unresolved));
            } catch (RuntimeException e) {
                // an entry that does not have the expected shape is not worth failing the query for
            }
        }
        return out;
    }

    /** Dotted form of a class name given dotted or slash-separated ({@code net/minecraft/world/level/Level}). */
    public static String normalizeClassName(String name) {
        return name.strip().replace('/', '.');
    }

    private static List<MixinFootprintEntry.Member> members(JsonArray arr) {
        List<MixinFootprintEntry.Member> out = new ArrayList<>();
        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            out.add(new MixinFootprintEntry.Member(o.get("name").getAsString(), o.get("descriptor").getAsString()));
        }
        return out;
    }

    // ── config discovery ────────────────────────────────────────

    private static Set<String> declaredConfigs(ModFileHandle file) throws IOException {
        Set<String> configs = new LinkedHashSet<>();
        for (String toml : List.of("META-INF/neoforge.mods.toml", "META-INF/mods.toml")) {
            Optional<InputStream> in = file.open(toml);
            if (in.isPresent()) {
                try (InputStream is = in.get()) {
                    parseTomlMixins(new String(is.readAllBytes(), StandardCharsets.UTF_8), configs);
                }
            }
        }
        Optional<InputStream> fabric = file.open("fabric.mod.json");
        if (fabric.isPresent()) {
            try (InputStream is = fabric.get(); InputStreamReader r = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                parseFabricMixins(JsonParser.parseReader(r), configs);
            } catch (RuntimeException e) {
                MarieCore.LOGGER.debug("[ModScan] Unreadable fabric.mod.json in {}: {}", file.info().path(), e.toString());
            }
        }
        Optional<InputStream> manifest = file.open("META-INF/MANIFEST.MF");
        if (manifest.isPresent()) {
            try (InputStream is = manifest.get()) {
                String attr = new java.util.jar.Manifest(is).getMainAttributes().getValue("MixinConfigs");
                if (attr != null) {
                    for (String c : attr.split(",")) {
                        if (!c.isBlank()) {
                            configs.add(c.trim());
                        }
                    }
                }
            }
        }
        return configs;
    }

    /** Collects configs from {@code "mixins"}: a string, or an object with {@code config}, per element (or a lone one). */
    static void parseFabricMixins(JsonElement root, Set<String> into) {
        if (!root.isJsonObject() || !root.getAsJsonObject().has("mixins")) {
            return;
        }
        JsonElement mixins = root.getAsJsonObject().get("mixins");
        Iterable<JsonElement> items = mixins.isJsonArray() ? mixins.getAsJsonArray() : List.of(mixins);
        for (JsonElement el : items) {
            if (el.isJsonPrimitive()) {
                into.add(el.getAsString());
            } else if (el.isJsonObject() && el.getAsJsonObject().has("config")) {
                into.add(el.getAsJsonObject().get("config").getAsString());
            }
        }
    }

    /** Collects {@code config = "..."} lines that belong to a {@code [[mixins]]} table. */
    static void parseTomlMixins(String toml, Set<String> into) {
        boolean inMixins = false;
        for (String line : toml.split("\\R")) {
            String t = line.strip();
            if (t.startsWith("[")) {
                inMixins = t.replace(" ", "").equals("[[mixins]]");
                continue;
            }
            if (inMixins) {
                Matcher m = TOML_CONFIG.matcher(line);
                if (m.find()) {
                    into.add(m.group(1));
                }
            }
        }
    }

    private static JsonObject readConfig(ModFileHandle file, String config) throws IOException {
        Optional<InputStream> in = file.open(config);
        if (in.isEmpty()) {
            return null;
        }
        try (InputStream is = in.get(); InputStreamReader r = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(r);
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            MarieCore.LOGGER.debug("[ModScan] Unreadable mixin config {} in {}: {}", config, file.info().path(), e.toString());
            return null;
        }
    }

    // ── class reading ───────────────────────────────────────────

    private static void readMixinClass(ModFileHandle file, String className, JsonArray out) throws IOException {
        Optional<InputStream> in = file.open(className.replace('.', '/') + ".class");
        if (in.isEmpty()) {
            return;
        }
        MixinVisitor v = new MixinVisitor();
        try (InputStream is = in.get()) {
            new ClassReader(is).accept(v, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (RuntimeException e) {
            MarieCore.LOGGER.debug("[ModScan] Unreadable mixin class {} in {}: {}", className, file.info().path(), e.toString());
            return;
        }
        for (String target : v.targets) {
            JsonObject o = new JsonObject();
            o.addProperty("mixin", className);
            String dotted = normalizeClassName(target);
            o.addProperty("target", dotted);
            if (INTERMEDIARY.matcher(dotted).find()) {
                o.addProperty("unresolved", true);
            }
            o.add("fields", memberArray(v.fields));
            o.add("methods", memberArray(v.methods));
            out.add(o);
        }
    }

    private static JsonArray memberArray(List<String[]> members) {
        JsonArray arr = new JsonArray();
        for (String[] m : members) {
            JsonObject o = new JsonObject();
            o.addProperty("name", m[0]);
            o.addProperty("descriptor", m[1]);
            arr.add(o);
        }
        return arr;
    }

    private static final class MixinVisitor extends ClassVisitor {
        final Set<String> targets = new LinkedHashSet<>();
        final List<String[]> fields = new ArrayList<>();
        final List<String[]> methods = new ArrayList<>();

        MixinVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (!MIXIN_DESC.equals(descriptor)) {
                return null;
            }
            return new AnnotationVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitArray(String name) {
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override
                        public void visit(String n, Object value) {
                            if ("value".equals(name) && value instanceof Type t) {
                                targets.add(t.getInternalName());
                            } else if ("targets".equals(name) && value instanceof String s) {
                                targets.add(s);
                            }
                        }
                    };
                }
            };
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if ((access & Opcodes.ACC_SYNTHETIC) != 0) {
                return null;
            }
            String[] member = {name, descriptor};
            return new FieldVisitor(Opcodes.ASM9) {
                boolean shadow;

                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    shadow |= SHADOW_DESC.equals(desc);
                    return null;
                }

                @Override
                public void visitEnd() {
                    if (!shadow) {
                        fields.add(member);
                    }
                }
            };
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if ((access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0 || name.equals("<init>") || name.equals("<clinit>")) {
                return null;
            }
            String[] member = {name, descriptor};
            return new MethodVisitor(Opcodes.ASM9) {
                boolean shadow;

                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    shadow |= SHADOW_DESC.equals(desc);
                    return null;
                }

                @Override
                public void visitEnd() {
                    if (!shadow) {
                        methods.add(member);
                    }
                }
            };
        }
    }
}
