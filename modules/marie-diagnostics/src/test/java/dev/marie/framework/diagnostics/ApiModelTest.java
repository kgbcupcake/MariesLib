package dev.marie.framework.diagnostics;

import dev.marie.framework.diagnostics.api.DiagnosticProfile;
import dev.marie.framework.diagnostics.api.DiagnosticSection;
import dev.marie.framework.diagnostics.api.FailureCategory;
import dev.marie.framework.diagnostics.api.FailureSubmission;
import dev.marie.framework.diagnostics.api.Severity;
import dev.marie.framework.diagnostics.registry.DiagnosticModRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApiModelTest {

    @AfterEach
    void reset() {
        DiagnosticModRegistry.resetInternal();
    }

    @Test
    void profileRejectsInvalidModIds() {
        assertThrows(IllegalArgumentException.class, () -> DiagnosticProfile.builder("Bad-Id"));
        assertThrows(IllegalArgumentException.class, () -> DiagnosticProfile.builder("../escape"));
        assertThrows(IllegalArgumentException.class, () -> DiagnosticProfile.builder(""));
        assertEquals("good_id1", DiagnosticProfile.builder("good_id1").build().modId());
    }

    @Test
    void profileNormalizesPackagesAndValidatesRetentionAndProviders() {
        DiagnosticProfile p = DiagnosticProfile.builder("mymod").packages("com.example.mymod", "com.example.mymod.").build();
        assertEquals(List.of("com.example.mymod."), p.packages());
        assertEquals(DiagnosticProfile.DEFAULT_RETENTION, p.retention());
        assertThrows(IllegalArgumentException.class, () -> DiagnosticProfile.builder("m").retention(0));
        assertThrows(IllegalArgumentException.class, () -> DiagnosticProfile.builder("m").packages(" "));
        assertThrows(IllegalArgumentException.class, () -> DiagnosticProfile.builder("m")
                .provider("a", (c, s) -> {}).provider("a", (c, s) -> {}));
    }

    @Test
    void registryRegistersRejectsDuplicatesAndFreezes() {
        DiagnosticModRegistry.register(TestProfiles.mod("one", "a.one"));
        assertNotNull(DiagnosticModRegistry.get("one"));
        assertThrows(IllegalStateException.class, () -> DiagnosticModRegistry.register(TestProfiles.mod("one")));
        DiagnosticModRegistry.freezeInternal();
        assertTrue(DiagnosticModRegistry.isFrozen());
        assertThrows(IllegalStateException.class, () -> DiagnosticModRegistry.register(TestProfiles.mod("two")));
    }

    @Test
    void sectionStaysWithinLimits() {
        DiagnosticSection s = new DiagnosticSection();
        for (int i = 0; i < DiagnosticSection.MAX_ENTRIES + 10; i++) {
            s.entry("k" + i, i);
        }
        assertEquals(DiagnosticSection.MAX_ENTRIES, s.entries().size());
        assertTrue(s.truncated());

        DiagnosticSection big = new DiagnosticSection();
        big.entry("long", "x".repeat(5000));
        assertEquals(DiagnosticSection.MAX_VALUE_CHARS, big.entries().get("long").length());
        for (int i = 0; i < 20; i++) {
            big.entry("fill" + i, "y".repeat(1000));
        }
        int total = big.entries().entrySet().stream().mapToInt(e -> e.getKey().length() + e.getValue().length()).sum();
        assertTrue(total <= DiagnosticSection.MAX_TOTAL_CHARS);
    }

    @Test
    void submissionDefaultsAndContextCap() {
        FailureSubmission.Builder b = FailureSubmission.builder();
        FailureSubmission plain = b.build();
        assertEquals(FailureCategory.OTHER, plain.category());
        assertEquals(Severity.ERROR, plain.severity());
        assertTrue(plain.runProviders());

        FailureSubmission.Builder many = FailureSubmission.builder();
        for (int i = 0; i < FailureSubmission.MAX_CONTEXT_ENTRIES + 5; i++) {
            many.context("k" + i, i);
        }
        FailureSubmission s = many.build();
        assertEquals(FailureSubmission.MAX_CONTEXT_ENTRIES, s.context().size());
        assertTrue(s.contextTruncated());
    }
}
