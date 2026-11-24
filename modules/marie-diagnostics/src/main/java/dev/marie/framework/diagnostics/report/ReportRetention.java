package dev.marie.framework.diagnostics.report;

import dev.marie.framework.api.ApiStatus;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ApiStatus.Internal
public final class ReportRetention {

    static final long STALE_TEMP_MILLIS = 60_000L;

    private ReportRetention() {}

    public static int prune(Path dir, int keep) throws IOException {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        List<Path> reports = new ArrayList<>();
        long now = System.currentTimeMillis();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                String name = p.getFileName().toString();
                if (!name.startsWith(IncidentIds.FILE_PREFIX)) {
                    continue;
                }
                if (name.endsWith(IncidentIds.FILE_SUFFIX)) {
                    reports.add(p);
                } else if (name.endsWith(".tmp") && isOlderThan(p, now - STALE_TEMP_MILLIS)) {
                    Files.deleteIfExists(p);
                }
            }
        }
        if (reports.size() <= keep) {
            return 0;
        }
        reports.sort(Comparator.comparing(p -> p.getFileName().toString()));
        int deleted = 0;
        for (int i = 0; i < reports.size() - keep; i++) {
            try {
                if (Files.deleteIfExists(reports.get(i))) {
                    deleted++;
                }
            } catch (IOException ignored) {
            }
        }
        return deleted;
    }

    private static boolean isOlderThan(Path p, long cutoffMillis) {
        try {
            FileTime t = Files.getLastModifiedTime(p);
            return t.toMillis() < cutoffMillis;
        } catch (IOException e) {
            return false;
        }
    }
}
