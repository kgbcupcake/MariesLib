package dev.marie.framework.diagnostics;

import net.minecraft.CrashReport;
import net.minecraft.ReportType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CrashReportHooksTest {

    @TempDir
    Path dir;

    @AfterEach
    void clear() {
        CrashReportHooks.setListener(null);
    }

    @Test
    void listenerSeesGenuineCrashAndItsSavedFile() throws IOException {
        List<Path> saved = new ArrayList<>();
        CrashReportHooks.setListener(report -> {
            report.getSystemReport().setDetail("MariesLib Incident", "test-id");
            return saved::add;
        });
        CrashReport report = new CrashReport("test crash", new RuntimeException("boom"));
        Path file = dir.resolve("crash-test-client.txt");

        assertTrue(report.saveToFile(file, ReportType.CRASH));
        assertEquals(List.of(file), saved);
        assertTrue(Files.readString(file).contains("MariesLib Incident: test-id"),
                "the companion line is inside Minecraft's own report");
    }

    @Test
    void throwingListenerNeverStopsTheSave() {
        CrashReportHooks.setListener(report -> {
            throw new IllegalStateException("listener bug");
        });
        CrashReport report = new CrashReport("test crash", new RuntimeException("boom"));
        Path file = dir.resolve("crash-a.txt");
        assertTrue(report.saveToFile(file, ReportType.CRASH));
        assertTrue(Files.isRegularFile(file));

        CrashReportHooks.setListener(r -> f -> {
            throw new IllegalStateException("writer bug");
        });
        CrashReport second = new CrashReport("test crash", new RuntimeException("boom"));
        Path file2 = dir.resolve("crash-b.txt");
        assertTrue(second.saveToFile(file2, ReportType.CRASH));
        assertTrue(Files.isRegularFile(file2));
    }

    @Test
    void otherReportTypesAndRepeatSavesAreIgnored() {
        List<CrashReport> seen = new ArrayList<>();
        CrashReportHooks.setListener(report -> {
            seen.add(report);
            return f -> {};
        });
        CrashReport chunk = new CrashReport("chunk", new RuntimeException());
        assertTrue(chunk.saveToFile(dir.resolve("chunk.txt"), ReportType.CHUNK_IO_ERROR));
        assertTrue(seen.isEmpty());

        CrashReport crash = new CrashReport("crash", new RuntimeException());
        assertTrue(crash.saveToFile(dir.resolve("one.txt"), ReportType.CRASH));
        assertFalse(crash.saveToFile(dir.resolve("two.txt"), ReportType.CRASH), "vanilla refuses a second save");
        assertEquals(1, seen.size());
    }
}
