package dev.marie.framework.api.modscan;

import dev.marie.framework.api.ApiStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Metadata for one mod file, gathered without reading its contents. A file can hold several mods.
 *
 * <ul>
 *   <li>{@link Kind#JAR}: {@code path} is the jar, {@code size}/{@code mtime} are the jar's.</li>
 *   <li>{@link Kind#NESTED_JAR}: a jar-in-jar mod. {@code path} is {@code <outer file>!<mod ids>}
 *       and {@code size}/{@code mtime} are those of the outermost physical file, since a nested jar
 *       cannot change without it.</li>
 *   <li>{@link Kind#FOLDER}: a development class/resource directory. {@code size} is the summed size
 *       of its files and {@code mtime} the newest file mtime (a directory's own mtime does not
 *       change when a file inside is edited).</li>
 * </ul>
 *
 * @param mods mod id to version, sorted by id
 */
@ApiStatus.Experimental
public record ModFileInfo(String path, long size, long mtime, Kind kind, Map<String, String> mods) {

    public enum Kind { JAR, NESTED_JAR, FOLDER }

    public ModFileInfo {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(kind, "kind");
        mods = mods == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(mods));
    }

    /** Identity of this exact state of the file: changes when path, size or mtime change. */
    public String fingerprint() {
        return path + '|' + size + '|' + mtime;
    }

    public List<String> modIds() {
        return new ArrayList<>(mods.keySet());
    }
}
