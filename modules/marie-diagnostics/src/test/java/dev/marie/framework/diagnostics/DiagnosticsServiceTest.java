package dev.marie.framework.diagnostics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.marie.framework.diagnostics.api.DiagnosticProfile;
import dev.marie.framework.diagnostics.api.FailureCategory;
import dev.marie.framework.diagnostics.api.FailureSubmission;
import dev.marie.framework.diagnostics.capture.ContextCapture;
import dev.marie.framework.diagnostics.capture.ProviderRunner;
import dev.marie.framework.diagnostics.report.ReportStore;
import dev.marie.framework.diagnostics.service.CrashTicket;
import dev.marie.framework.diagnostics.service.DiagnosticsGuard;
import dev.marie.framework.diagnostics.service.DiagnosticsService;
import dev.marie.framework.diagnostics.service.SubmissionLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class DiagnosticsServiceTest {

    @TempDir
    Path root;

    private final List<DiagnosticProfile> profiles = new ArrayList<>(List.of(
            DiagnosticProfile.builder("marieslib").packages("dev.marie.framework")
                    .provider("state", (c, s) -> s.entry("lib", "ok")).build(),
            DiagnosticProfile.builder("nourished").packages("dev.marie.nourished")
                    .provider("registry", (c, s) -> s.entry("foods", 12))
                    .provider("broken", (c, s) -> { throw new IllegalStateException("provider bug"); })
                    .build(),
            DiagnosticProfile.builder("thinair").packages("dev.marie.thinair").retention(2).build()));

    private DiagnosticsService service(List<ContextCapture> captures, int perSession) {
        return new DiagnosticsService(new ReportStore(root), () -> profiles, captures, new ProviderRunner(200),
                id -> "1.0", () -> true, new SubmissionLimiter(perSession, DiagnosticsService.MAX_DUPLICATES));
    }

    private DiagnosticsService service() {
        ContextCapture env = new ContextCapture() {
            public String section() { return "environment"; }
            public com.google.gson.JsonElement capture() { return new JsonPrimitive("env"); }
        };
        ContextCapture broken = new ContextCapture() {
            public String section() { return "session"; }
            public com.google.gson.JsonElement capture() throws Exception { throw new IOException("session down"); }
        };
        return service(List.of(env, broken), 50);
    }

    private static FailureSubmission details(String component) {
        return FailureSubmission.builder().component(component).category(FailureCategory.DATA_PROCESSING)
                .context("item", "minecraft:apple").build();
    }

    private List<JsonObject> reports(String mod) throws IOException {
        Path dir = root.resolve(mod).resolve("crash-reports");
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.toString().endsWith(".json")).sorted().map(p -> {
                try {
                    return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).toList();
        }
    }

    @Test
    void submittedFailureProducesStructuredReportInOwnersFolder() throws IOException {
        DiagnosticsService s = service();
        RuntimeException e = TestProfiles.thrownIn("dev.marie.nourished.NutritionCalculator", "net.minecraft.Server");
        Optional<String> id = s.submit("nourished", e, null, details("NutritionCalculator"));
        s.shutdown(5000);

        assertTrue(id.isPresent());
        List<JsonObject> written = reports("nourished");
        assertEquals(1, written.size());
        JsonObject r = written.get(0);
        assertEquals(1, r.get("schemaVersion").getAsInt());
        JsonObject incident = r.getAsJsonObject("incident");
        assertEquals(id.get(), incident.get("id").getAsString());
        assertEquals("nourished", incident.get("reportingMod").getAsString());
        assertEquals("REPORTED_FAILURE", incident.get("kind").getAsString());
        assertEquals("NutritionCalculator", incident.get("component").getAsString());
        assertEquals("DATA_PROCESSING", incident.get("category").getAsString());

        JsonObject failure = r.getAsJsonObject("failure");
        assertEquals("java.lang.RuntimeException", failure.get("exceptionType").getAsString());
        assertEquals("nourished", failure.getAsJsonArray("implicatedMods").get(0).getAsString());
        assertEquals("minecraft:apple", failure.getAsJsonObject("context").get("item").getAsString());

        JsonObject providers = r.getAsJsonObject("runtime").getAsJsonObject("providers");
        assertEquals("ok", providers.getAsJsonObject("nourished:registry").get("status").getAsString());
        assertEquals("failed", providers.getAsJsonObject("nourished:broken").get("status").getAsString());
        assertEquals("ok", providers.getAsJsonObject("marieslib:state").get("status").getAsString());
        assertEquals(3, r.getAsJsonObject("runtime").getAsJsonArray("integratedMods").size());

        JsonObject status = r.getAsJsonObject("diagnosticStatus");
        assertTrue(status.getAsJsonArray("captured").toString().contains("environment"));
        assertTrue(status.getAsJsonArray("failed").toString().contains("session down"), "failed capture is recorded");
        assertTrue(status.getAsJsonArray("failed").toString().contains("provider bug"));
        assertTrue(r.get("session").isJsonNull(), "failed section is null, the rest of the report still exists");
        assertTrue(r.getAsJsonObject("analysis").get("rootCause").isJsonNull(), "no fabricated diagnosis");
    }

    @Test
    void reportingOwnershipIsSeparateFromImplicatedMods() throws IOException {
        DiagnosticsService s = service();
        RuntimeException e = TestProfiles.thrownIn("dev.marie.thinair.Breath", "dev.marie.nourished.Hook");
        s.submit("nourished", e, null, details("Hook"));
        s.shutdown(5000);
        JsonObject r = reports("nourished").get(0);
        assertEquals("nourished", r.getAsJsonObject("incident").get("reportingMod").getAsString());
        assertEquals("[\"thinair\",\"nourished\"]", r.getAsJsonObject("failure").getAsJsonArray("implicatedMods").toString());
        assertTrue(r.getAsJsonObject("runtime").getAsJsonObject("providers").has("nourished:registry"));
    }

    @Test
    void descriptionOnlyFailureRecordsReportSite() throws IOException {
        DiagnosticsService s = service();
        assertTrue(s.submit("thinair", null, "oxygen value out of range", details("Breath")).isPresent());
        s.shutdown(5000);
        JsonObject failure = reports("thinair").get(0).getAsJsonObject("failure");
        assertTrue(failure.get("exceptionType").isJsonNull());
        assertEquals("oxygen value out of range", failure.get("message").getAsString());
        assertTrue(failure.getAsJsonArray("reportSite").toString().contains("DiagnosticsServiceTest"));
    }

    @Test
    void invalidModIdFallsBackToMariesLibFolder() throws IOException {
        DiagnosticsService s = service();
        s.submit("Not A Mod!", new RuntimeException("x"), null, details("C"));
        s.shutdown(5000);
        JsonObject r = reports("marieslib").get(0);
        assertEquals("marieslib", r.getAsJsonObject("incident").get("reportingMod").getAsString());
        assertTrue(r.getAsJsonObject("diagnosticStatus").getAsJsonArray("limitations").toString().contains("invalid mod id"));
    }

    @Test
    void repeatsAndSessionVolumeAreLimited() throws IOException {
        DiagnosticsService s = service(List.of(), 4);
        RuntimeException same = TestProfiles.thrownIn("dev.marie.nourished.Loop");
        int accepted = 0;
        for (int i = 0; i < 10; i++) {
            if (s.submit("nourished", same, null, details("Loop")).isPresent()) {
                accepted++;
            }
        }
        assertEquals(DiagnosticsService.MAX_DUPLICATES, accepted);
        assertTrue(s.submit("nourished", TestProfiles.thrownIn("a.B"), null, details("Other")).isPresent());
        assertTrue(s.submit("nourished", TestProfiles.thrownIn("c.D"), null, details("Third")).isEmpty(),
                "session limit reached");
        s.shutdown(5000);
        assertEquals(4, reports("nourished").size());
    }

    @Test
    void reportsFromInsideDiagnosticsWorkAreDropped() {
        DiagnosticsService s = service();
        AtomicReference<Optional<String>> inner = new AtomicReference<>();
        DiagnosticsGuard.run(() -> inner.set(s.submit("nourished", new RuntimeException(), null, details("C"))));
        assertTrue(inner.get().isEmpty());
        assertNull(DiagnosticsGuard.isActive() ? "leaked" : null);
        s.shutdown(1000);
    }

    @Test
    void retentionIsPerModProfile() throws IOException {
        DiagnosticsService s = service(List.of(), 50);
        for (int i = 0; i < 4; i++) {
            s.submit("thinair", TestProfiles.thrownIn("dev.marie.thinair.C" + i), null, details("C" + i));
        }
        s.shutdown(5000);
        assertEquals(2, reports("thinair").size());
    }

    @Test
    void crashTicketNamesTheFileThatIsWrittenAndCorrelatesBothWays() throws IOException {
        DiagnosticsService s = service();
        RuntimeException crash = TestProfiles.thrownIn("dev.marie.nourished.Ticker", "net.minecraft.server.MinecraftServer");
        CrashTicket ticket = s.prepareCrash(crash);
        assertNotNull(ticket);
        assertEquals("nourished", ticket.storageMod());
        s.completeCrash(ticket, "crash-2026-11-24_15.30.12-server.txt", "CRASH");

        Path expected = root.resolve(ticket.displayPath());
        assertTrue(Files.isRegularFile(expected), "Minecraft's report points at a file that exists");
        JsonObject r = JsonParser.parseString(Files.readString(expected)).getAsJsonObject();
        assertEquals(ticket.id(), r.getAsJsonObject("incident").get("id").getAsString());
        assertEquals("MINECRAFT_CRASH", r.getAsJsonObject("incident").get("kind").getAsString());
        assertEquals("FATAL", r.getAsJsonObject("incident").get("severity").getAsString());
        assertEquals("crash-2026-11-24_15.30.12-server.txt",
                r.getAsJsonObject("correlation").get("minecraftCrashReport").getAsString());
        s.shutdown(1000);
    }

    @Test
    void vanillaCrashIsStoredUnderMariesLib() {
        DiagnosticsService s = service();
        CrashTicket ticket = s.prepareCrash(TestProfiles.thrownIn("net.minecraft.client.Minecraft"));
        assertEquals("marieslib", ticket.storageMod());
        s.shutdown(1000);
    }

    @Test
    void unwritableReportFolderNeverThrows() throws IOException {
        Files.writeString(root.resolve("nourished"), "not a folder");
        DiagnosticsService s = service();
        CrashTicket ticket = s.prepareCrash(TestProfiles.thrownIn("dev.marie.nourished.X"));
        assertDoesNotThrow(() -> s.completeCrash(ticket, "crash.txt", "CRASH"));
        assertDoesNotThrow(() -> s.submit("nourished", new RuntimeException(), null, details("C")));
        s.shutdown(5000);
    }
}
