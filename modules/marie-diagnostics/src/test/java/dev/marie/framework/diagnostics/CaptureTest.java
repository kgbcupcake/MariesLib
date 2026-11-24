package dev.marie.framework.diagnostics;

import com.google.gson.JsonObject;
import dev.marie.framework.diagnostics.api.DiagnosticProfile;
import dev.marie.framework.diagnostics.api.FailureCategory;
import dev.marie.framework.diagnostics.api.IncidentKind;
import dev.marie.framework.diagnostics.api.ProviderContext;
import dev.marie.framework.diagnostics.capture.ProviderRunner;
import dev.marie.framework.diagnostics.capture.StackAttribution;
import dev.marie.framework.diagnostics.capture.ThrowableCapture;
import dev.marie.framework.diagnostics.service.DiagnosticsGuard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class CaptureTest {

    private static final ProviderContext CTX =
            new ProviderContext("id", IncidentKind.REPORTED_FAILURE, "mymod", "Comp", FailureCategory.OTHER);

    @Test
    void attributionUsesLongestPrefixAndRootCauseFirst() {
        List<DiagnosticProfile> profiles = List.of(
                TestProfiles.mod("outer", "com.outer"),
                TestProfiles.mod("inner", "com.outer.inner"),
                TestProfiles.mod("rootmod", "org.root"));
        RuntimeException cause = TestProfiles.thrownIn("org.root.Failing");
        RuntimeException top = TestProfiles.thrownIn("com.outer.inner.Thing", "com.outer.Other", "java.lang.Thread");
        top.initCause(cause);

        StackAttribution.Result r = StackAttribution.attribute(top, profiles);
        assertEquals(List.of("rootmod", "inner", "outer"), r.implicatedMods());
        assertEquals("rootmod", r.primary());
        assertEquals(3, r.relevantFrames().size());
    }

    @Test
    void consumerFramesInsideMariesLibPackagesAreNotFlaggedAsMariesLib() {
        List<DiagnosticProfile> profiles = List.of(
                TestProfiles.mod("marieslib", "dev.marie.framework"),
                TestProfiles.mod("marieslib_devtest", "dev.marie.framework.diagnostics.devtest"));
        RuntimeException e = TestProfiles.thrownIn("dev.marie.framework.diagnostics.devtest.Cmd", "dev.marie.framework.core.Core");
        StackAttribution.Result r = StackAttribution.attribute(e, profiles);
        assertEquals("marieslib_devtest", r.relevantFrames().get(0).getAsJsonObject().get("mod").getAsString());
        assertFalse(r.relevantFrames().get(0).getAsJsonObject().get("isMariesLib").getAsBoolean());
        assertTrue(r.relevantFrames().get(1).getAsJsonObject().get("isMariesLib").getAsBoolean());
    }

    @Test
    void framesSharedByAnExceptionAndItsCauseAreListedOnce() {
        List<DiagnosticProfile> profiles = List.of(TestProfiles.mod("mymod", "com.mymod"));
        RuntimeException cause = TestProfiles.thrownIn("com.mymod.Cmd", "com.mymod.Dispatch");
        RuntimeException outer = TestProfiles.thrownIn("com.mymod.Cmd", "com.mymod.Dispatch");
        outer.initCause(new IllegalArgumentException("inner"));
        outer.getCause().setStackTrace(cause.getStackTrace());
        StackAttribution.Result r = StackAttribution.attribute(outer, profiles);
        assertEquals(2, r.relevantFrames().size());
        assertEquals("java.lang.IllegalArgumentException",
                r.relevantFrames().get(0).getAsJsonObject().get("exception").getAsString());
    }

    @Test
    void attributionMarksMariesLibFramesAndFindsNothingForVanilla() {
        RuntimeException e = TestProfiles.thrownIn("net.minecraft.Foo", "dev.marie.framework.core.Bar");
        StackAttribution.Result r = StackAttribution.attribute(e, List.of());
        assertNull(r.primary());
        assertEquals(1, r.relevantFrames().size());
        assertTrue(r.relevantFrames().get(0).getAsJsonObject().get("isMariesLib").getAsBoolean());
    }

    @Test
    void throwableCaptureBoundsFramesAndSurvivesCauseCycles() {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);
        StackTraceElement[] many = new StackTraceElement[ThrowableCapture.MAX_FRAMES + 5];
        java.util.Arrays.fill(many, new StackTraceElement("x.Y", "z", "Y.java", 1));
        a.setStackTrace(many);

        JsonObject json = ThrowableCapture.describe(a);
        assertEquals(ThrowableCapture.MAX_FRAMES + 1, json.getAsJsonArray("stackTrace").size());
        assertEquals(1, json.getAsJsonArray("causes").size());
    }

    @Test
    void providersAreIsolatedFromEachOther() {
        ProviderRunner runner = new ProviderRunner(200);
        AtomicBoolean guardSeen = new AtomicBoolean();
        List<ProviderRunner.Target> targets = List.of(
                new ProviderRunner.Target("m", "good", (c, s) -> {
                    guardSeen.set(DiagnosticsGuard.isActive());
                    s.entry("value", 42);
                }),
                new ProviderRunner.Target("m", "throws", (c, s) -> {
                    throw new IllegalStateException("nope");
                }),
                new ProviderRunner.Target("m", "hangs", (c, s) -> Thread.sleep(5_000)),
                new ProviderRunner.Target("m", "after", (c, s) -> s.entry("ran", true)));

        ProviderRunner.Outcome out = runner.run(targets, CTX);
        JsonObject sections = out.sections();
        assertEquals("ok", sections.getAsJsonObject("m:good").get("status").getAsString());
        assertEquals("42", sections.getAsJsonObject("m:good").getAsJsonObject("entries").get("value").getAsString());
        assertEquals("failed", sections.getAsJsonObject("m:throws").get("status").getAsString());
        assertEquals("timed_out", sections.getAsJsonObject("m:hangs").get("status").getAsString());
        assertEquals("ok", sections.getAsJsonObject("m:after").get("status").getAsString());
        assertEquals(2, out.failures().size());
        assertTrue(guardSeen.get(), "providers run with the recursion guard active");
        runner.shutdown();
    }

    @Test
    void providerCountIsCapped() {
        ProviderRunner runner = new ProviderRunner(200);
        List<ProviderRunner.Target> targets = new ArrayList<>();
        for (int i = 0; i < ProviderRunner.MAX_PROVIDERS_PER_INCIDENT + 2; i++) {
            targets.add(new ProviderRunner.Target("m", "p" + i, (c, s) -> {}));
        }
        JsonObject sections = runner.run(targets, CTX).sections();
        assertEquals("skipped", sections.getAsJsonObject("m:p" + ProviderRunner.MAX_PROVIDERS_PER_INCIDENT)
                .get("status").getAsString());
        runner.shutdown();
    }
}
