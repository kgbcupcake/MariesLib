package dev.marie.framework.diagnostics;

import com.google.gson.JsonObject;
import dev.marie.framework.diagnostics.report.IncidentIds;
import dev.marie.framework.diagnostics.report.ReportRetention;
import dev.marie.framework.diagnostics.report.ReportStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class StorageTest {

    @TempDir
    Path root;

    @Test
    void writesPerModFolderAtomically() throws IOException {
        ReportStore store = new ReportStore(root);
        JsonObject report = new JsonObject();
        report.addProperty("schemaVersion", 1);
        String name = IncidentIds.fileName(Instant.now(), "abc123");
        assertNull(store.write("nourished", name, report, 20));

        Path file = root.resolve("nourished").resolve("crash-reports").resolve(name);
        assertTrue(Files.isRegularFile(file));
        assertTrue(Files.readString(file).contains("\"schemaVersion\": 1"));
        try (Stream<Path> files = Files.list(file.getParent())) {
            assertEquals(1, files.count(), "no temporary file left behind");
        }
        assertEquals("nourished/crash-reports/" + name, store.displayPath("nourished", name));
    }

    @Test
    void rejectsUnsafeModIdsAndFileNames() {
        ReportStore store = new ReportStore(root);
        assertThrows(IllegalArgumentException.class, () -> store.folder("../etc"));
        assertThrows(IllegalArgumentException.class, () -> store.write("ok", "../x.json", new JsonObject(), 5));
    }

    @Test
    void writeFailureIsReportedNotHidden() throws IOException {
        Path blocker = root.resolve("mymod");
        Files.writeString(blocker, "a file where the folder should be");
        ReportStore store = new ReportStore(root);
        assertThrows(IOException.class, () -> store.write("mymod",
                IncidentIds.fileName(Instant.now(), "id"), new JsonObject(), 5));
    }

    @Test
    void retentionKeepsNewestReportsOnly() throws IOException {
        Path dir = Files.createDirectories(root.resolve("m"));
        for (int i = 1; i <= 25; i++) {
            Files.writeString(dir.resolve(String.format("incident-20261124-1200%02d-x.json", i)), "{ broken");
        }
        Files.writeString(dir.resolve("notes.txt"), "user file");
        Path staleTmp = Files.writeString(dir.resolve("incident-old.json123.tmp"), "partial");
        Files.setLastModifiedTime(staleTmp, FileTime.fromMillis(System.currentTimeMillis() - 120_000));
        Path freshTmp = Files.writeString(dir.resolve("incident-new.json456.tmp"), "in progress");

        assertEquals(5, ReportRetention.prune(dir, 20));
        try (Stream<Path> files = Files.list(dir)) {
            List<String> names = files.map(p -> p.getFileName().toString()).sorted().toList();
            assertTrue(names.contains("notes.txt"));
            assertTrue(names.contains("incident-20261124-120025-x.json"));
            assertFalse(names.contains("incident-20261124-120005-x.json"));
            assertTrue(names.contains("incident-20261124-120006-x.json"));
            assertFalse(names.contains(staleTmp.getFileName().toString()));
            assertTrue(names.contains(freshTmp.getFileName().toString()));
        }
    }

    @Test
    void fileNamesSortByTime() {
        String earlier = IncidentIds.fileName(Instant.parse("2026-01-01T00:00:00Z"), "zzz");
        String later = IncidentIds.fileName(Instant.parse("2026-01-01T00:00:01Z"), "aaa");
        assertTrue(earlier.compareTo(later) < 0);
    }
}
