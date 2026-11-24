package dev.marie.framework.diagnostics;

import dev.marie.framework.diagnostics.api.DiagnosticProfile;

final class TestProfiles {

    private TestProfiles() {}

    static DiagnosticProfile mod(String id, String... packages) {
        return DiagnosticProfile.builder(id).packages(packages).build();
    }

    static RuntimeException thrownIn(String... classNames) {
        RuntimeException e = new RuntimeException("boom");
        StackTraceElement[] frames = new StackTraceElement[classNames.length];
        for (int i = 0; i < classNames.length; i++) {
            frames[i] = new StackTraceElement(classNames[i], "run", "X.java", i + 1);
        }
        e.setStackTrace(frames);
        return e;
    }
}
