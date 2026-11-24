package dev.marie.framework.diagnostics.registry;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.diagnostics.api.DiagnosticProfile;
import dev.marie.framework.registry.AbstractRegistry;

import javax.annotation.Nullable;
import java.util.List;

@ApiStatus.Internal
public final class DiagnosticModRegistry {

    private static final Registry REGISTRY = new Registry();

    private DiagnosticModRegistry() {}

    public static void register(DiagnosticProfile profile) {
        REGISTRY.register(profile.modId(), profile);
    }

    @Nullable
    public static DiagnosticProfile get(String modId) {
        return REGISTRY.get(modId);
    }

    public static boolean contains(String modId) {
        return REGISTRY.contains(modId);
    }

    public static List<DiagnosticProfile> all() {
        return REGISTRY.values();
    }

    public static boolean isFrozen() {
        return REGISTRY.isFrozen();
    }

    public static void freezeInternal() {
        REGISTRY.freeze();
    }

    public static void resetInternal() {
        REGISTRY.reset();
    }

    private static final class Registry extends AbstractRegistry<String, DiagnosticProfile> {
        Registry() {
            super("DiagnosticModRegistry");
        }
    }
}
