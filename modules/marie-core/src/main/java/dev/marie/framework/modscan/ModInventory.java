package dev.marie.framework.modscan;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.api.modscan.ModFileHandle;
import dev.marie.framework.api.modscan.ModFileInfo;
import dev.marie.framework.api.modscan.ModSetDiff;
import dev.marie.framework.core.MarieCore;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * The installed mod files, keyed by file, with their mod ids and versions. Building one reads file
 * metadata only, never file contents. Also holds where each file's contents can be read from so the
 * scanner can open it later.
 */
@ApiStatus.Internal
public final class ModInventory {

    /** Bound on files visited when summarising a development folder. */
    private static final int FOLDER_WALK_LIMIT = 50_000;

    private final List<ModFileInfo> files;
    private final Map<String, List<Path>> roots;

    public ModInventory(List<ModFileInfo> files, Map<String, List<Path>> roots) {
        List<ModFileInfo> sorted = new ArrayList<>(files);
        sorted.sort(Comparator.comparing(ModFileInfo::path));
        this.files = List.copyOf(sorted);
        this.roots = Map.copyOf(roots);
    }

    /** Inventory whose files are read from {@code Path.of(info.path())}; for tests and plain files. */
    public static ModInventory ofFiles(List<ModFileInfo> files) {
        return new ModInventory(files, Map.of());
    }

    public List<ModFileInfo> files() {
        return files;
    }

    public String fingerprint() {
        return fingerprint(files);
    }

    public ModFileHandle open(ModFileInfo info) {
        List<Path> r = roots.get(info.path());
        return ModFileHandle.of(info, r != null ? r : List.of(Path.of(info.path())));
    }

    // ── metadata ────────────────────────────────────────────────

    /** Describes a jar or folder on disk; a folder's size/mtime are summed/maxed over its files. */
    public static ModFileInfo describe(Path path, Map<String, String> mods) throws IOException {
        return describe(path.toString(), path, mods, false);
    }

    /** Same, but reports the file as nested under an identity that is not its own path. */
    public static ModFileInfo describeNested(String identity, Path physical, Map<String, String> mods) throws IOException {
        return describe(identity, physical, mods, true);
    }

    private static ModFileInfo describe(String identity, Path physical, Map<String, String> mods, boolean nested)
            throws IOException {
        if (Files.isDirectory(physical)) {
            long[] acc = {0L, 0L, 0L};
            try (Stream<Path> walk = Files.walk(physical)) {
                for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                    if (++acc[2] > FOLDER_WALK_LIMIT) {
                        break;
                    }
                    acc[0] += Files.size(p);
                    acc[1] = Math.max(acc[1], Files.getLastModifiedTime(p).toMillis());
                }
            }
            return new ModFileInfo(identity, acc[0], acc[1], ModFileInfo.Kind.FOLDER, mods);
        }
        return new ModFileInfo(identity, Files.size(physical), Files.getLastModifiedTime(physical).toMillis(),
                nested ? ModFileInfo.Kind.NESTED_JAR : ModFileInfo.Kind.JAR, mods);
    }

    /** Reads the loaded mods from FML. Game only; returns an empty inventory if FML has no mod list. */
    public static ModInventory collect() {
        List<ModFileInfo> infos = new ArrayList<>();
        Map<String, List<Path>> roots = new HashMap<>();
        try {
            Map<IModFile, Map<String, String>> byFile = new LinkedHashMap<>();
            for (IModInfo mod : ModList.get().getMods()) {
                byFile.computeIfAbsent(mod.getOwningFile().getFile(), f -> new TreeMap<>())
                        .put(mod.getModId(), String.valueOf(mod.getVersion()));
            }
            for (Map.Entry<IModFile, Map<String, String>> e : byFile.entrySet()) {
                try {
                    IModFile file = e.getKey();
                    IModFile outer = file;
                    while (outer.getDiscoveryAttributes().parent() != null) {
                        outer = outer.getDiscoveryAttributes().parent();
                    }
                    ModFileInfo info;
                    if (outer != file) {
                        String identity = outer.getFilePath() + "!" + String.join("+", new TreeSet<>(e.getValue().keySet()));
                        info = describeNested(identity, outer.getFilePath(), e.getValue());
                    } else {
                        info = describe(file.getFilePath(), e.getValue());
                    }
                    infos.add(info);
                    roots.put(info.path(), List.of(file.getSecureJar().getRootPath()));
                } catch (IOException | RuntimeException ex) {
                    MarieCore.LOGGER.warn("[ModScan] Could not read metadata of a mod file holding {}: {}",
                            e.getValue().keySet(), ex.toString());
                }
            }
        } catch (RuntimeException ex) {
            MarieCore.LOGGER.warn("[ModScan] Could not read the mod list: {}", ex.toString());
        }
        return new ModInventory(infos, roots);
    }

    // ── fingerprint & diff ──────────────────────────────────────

    /** Stable, order-independent SHA-256 over every file's fingerprint, kind and mods. */
    public static String fingerprint(List<ModFileInfo> files) {
        List<ModFileInfo> sorted = new ArrayList<>(files);
        sorted.sort(Comparator.comparing(ModFileInfo::path));
        StringBuilder sb = new StringBuilder();
        for (ModFileInfo f : sorted) {
            sb.append(f.fingerprint()).append('|').append(f.kind()).append('|').append(f.mods()).append('\n');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** What changed going from {@code old} to {@code current}; {@link ModSetDiff#EMPTY}-equal if nothing did. */
    public static ModSetDiff diff(List<ModFileInfo> old, List<ModFileInfo> current) {
        Map<String, ModFileInfo> before = byPath(old);
        Map<String, ModFileInfo> after = byPath(current);
        Set<String> addedFiles = new TreeSet<>();
        Set<String> removedFiles = new TreeSet<>();
        Set<String> changedFiles = new TreeSet<>();
        for (Map.Entry<String, ModFileInfo> e : after.entrySet()) {
            ModFileInfo prev = before.get(e.getKey());
            if (prev == null) {
                addedFiles.add(e.getKey());
            } else if (prev.size() != e.getValue().size() || prev.mtime() != e.getValue().mtime()) {
                changedFiles.add(e.getKey());
            }
        }
        for (String path : before.keySet()) {
            if (!after.containsKey(path)) {
                removedFiles.add(path);
            }
        }
        Map<String, String> oldMods = modVersions(old);
        Map<String, String> newMods = modVersions(current);
        Set<String> addedMods = new TreeSet<>();
        Set<String> removedMods = new TreeSet<>();
        List<ModSetDiff.VersionChange> versionChanged = new ArrayList<>();
        for (Map.Entry<String, String> e : newMods.entrySet()) {
            String prev = oldMods.get(e.getKey());
            if (prev == null) {
                addedMods.add(e.getKey());
            } else if (!prev.equals(e.getValue())) {
                versionChanged.add(new ModSetDiff.VersionChange(e.getKey(), prev, e.getValue()));
            }
        }
        for (String id : oldMods.keySet()) {
            if (!newMods.containsKey(id)) {
                removedMods.add(id);
            }
        }
        return new ModSetDiff(new ArrayList<>(addedFiles), new ArrayList<>(removedFiles), new ArrayList<>(changedFiles),
                new ArrayList<>(addedMods), new ArrayList<>(removedMods), versionChanged);
    }

    private static Map<String, ModFileInfo> byPath(List<ModFileInfo> files) {
        Map<String, ModFileInfo> m = new TreeMap<>();
        for (ModFileInfo f : files) {
            m.put(f.path(), f);
        }
        return m;
    }

    private static Map<String, String> modVersions(List<ModFileInfo> files) {
        Map<String, String> m = new TreeMap<>();
        for (ModFileInfo f : files) {
            m.putAll(f.mods());
        }
        return m;
    }

    /** Paths of {@code files}, for retaining cache entries. */
    static Set<String> paths(List<ModFileInfo> files) {
        Set<String> s = new HashSet<>();
        for (ModFileInfo f : files) {
            s.add(f.path());
        }
        return s;
    }
}
