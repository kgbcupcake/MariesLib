package dev.marie.framework.api.modscan;

import dev.marie.framework.api.ApiStatus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Read-only view of one mod file's entries, the same for a jar, a nested jar and a development
 * folder. A root that is a regular file is opened as a zip; a directory is used as is. Entry
 * names use {@code /}. The scanner closes the handle; extractors must not.
 */
@ApiStatus.Experimental
public final class ModFileHandle implements AutoCloseable {

    private final ModFileInfo info;
    private final List<Path> roots;
    private final List<FileSystem> opened = new ArrayList<>();
    private List<Path> resolvedRoots;

    private ModFileHandle(ModFileInfo info, List<Path> roots) {
        this.info = info;
        this.roots = List.copyOf(roots);
    }

    /** Scanner/test entry point; consumers receive handles from the scanner. */
    @ApiStatus.Internal
    public static ModFileHandle of(ModFileInfo info, List<Path> roots) {
        return new ModFileHandle(info, roots);
    }

    public ModFileInfo info() {
        return info;
    }

    /** Opens an entry, or empty if it does not exist (or the name tries to leave the file). */
    public Optional<InputStream> open(String entryPath) throws IOException {
        String name = normalize(entryPath);
        if (name == null) {
            return Optional.empty();
        }
        for (Path root : resolvedRoots()) {
            Path p = root.resolve(name);
            if (Files.isRegularFile(p)) {
                return Optional.of(new ByteArrayInputStream(Files.readAllBytes(p)));
            }
        }
        return Optional.empty();
    }

    /** Names of all file entries, sorted and de-duplicated across roots. */
    public List<String> entryNames() throws IOException {
        TreeSet<String> names = new TreeSet<>();
        for (Path root : resolvedRoots()) {
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                    StringBuilder sb = new StringBuilder();
                    for (Path part : root.relativize(p)) {
                        if (sb.length() > 0) {
                            sb.append('/');
                        }
                        sb.append(part.toString());
                    }
                    names.add(sb.toString());
                }
            }
        }
        return new ArrayList<>(names);
    }

    private static String normalize(String entryPath) {
        if (entryPath == null) {
            return null;
        }
        String name = entryPath.replace('\\', '/');
        while (name.startsWith("/")) {
            name = name.substring(1);
        }
        if (name.isEmpty()) {
            return null;
        }
        for (String segment : name.split("/")) {
            if (segment.equals("..")) {
                return null;
            }
        }
        return name;
    }

    private synchronized List<Path> resolvedRoots() throws IOException {
        if (resolvedRoots == null) {
            List<Path> out = new ArrayList<>();
            for (Path root : roots) {
                if (Files.isDirectory(root)) {
                    out.add(root);
                } else {
                    FileSystem fs = FileSystems.newFileSystem(root);
                    opened.add(fs);
                    out.add(fs.getPath("/"));
                }
            }
            resolvedRoots = out;
        }
        return resolvedRoots;
    }

    @Override
    public synchronized void close() throws IOException {
        IOException failure = null;
        for (FileSystem fs : opened) {
            try {
                fs.close();
            } catch (IOException e) {
                failure = e;
            }
        }
        opened.clear();
        resolvedRoots = null;
        if (failure != null) {
            throw failure;
        }
    }
}
