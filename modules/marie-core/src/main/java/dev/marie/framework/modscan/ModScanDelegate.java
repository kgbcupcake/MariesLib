package dev.marie.framework.modscan;

import com.google.gson.JsonElement;
import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.api.marieapi.MarieAPIState;
import dev.marie.framework.api.modscan.MixinFootprintEntry;
import dev.marie.framework.api.modscan.ModFileInfo;
import dev.marie.framework.api.modscan.ModScanExtractor;
import dev.marie.framework.api.modscan.ModScanListener;
import dev.marie.framework.api.modscan.ModSetDiff;
import dev.marie.framework.api.registry.ModScanRegistry;
import dev.marie.framework.config.MariesLibConfigHolder;

import net.neoforged.fml.loading.FMLPaths;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

@ApiStatus.Internal
public final class ModScanDelegate {

    private ModScanDelegate() {}

    // ── registration ────────────────────────────────────────────

    public static void registerModScanExtractor(String namespace, ModScanExtractor extractor) {
        MarieAPIState.assertRegistrationAllowed("registerModScanExtractor");
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("namespace cannot be null or blank");
        }
        if (extractor == null) {
            throw new IllegalArgumentException("extractor cannot be null");
        }
        String id = extractor.id();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("extractor id cannot be null or blank");
        }
        ModScanRegistry.registerExtractor(namespace, extractor);
    }

    public static void registerModScanListener(ModScanListener listener) {
        MarieAPIState.assertRegistrationAllowed("registerModScanListener");
        if (listener == null) {
            throw new IllegalArgumentException("listener cannot be null");
        }
        ModScanRegistry.registerListener(listener);
    }

    public static void registerModScanDeferral(BooleanSupplier busy) {
        MarieAPIState.assertRegistrationAllowed("registerModScanDeferral");
        if (busy == null) {
            throw new IllegalArgumentException("busy cannot be null");
        }
        ModScanRegistry.registerDeferral(busy);
    }

    /** Registers the built-in mixin footprint extractor once, however many callers ask. */
    public static synchronized void registerMixinFootprintScan() {
        MarieAPIState.assertRegistrationAllowed("registerMixinFootprintScan");
        for (ModScanRegistry.ExtractorEntry e : ModScanRegistry.getExtractors()) {
            if (e.namespace().equals(MixinFootprintExtractor.NAMESPACE) && e.extractor().id().equals(MixinFootprintExtractor.ID)) {
                return;
            }
        }
        ModScanRegistry.registerExtractor(MixinFootprintExtractor.NAMESPACE,
                new MixinFootprintExtractor(FMLPaths.GAMEDIR.get().resolve(".cache").resolve("connector")));
    }

    // ── queries: never throw, never start a scan ────────────────

    public static boolean isModScanEnabled() {
        return MariesLibConfigHolder.get().enableModScan;
    }

    public static boolean isModScanReady() {
        return readyScan() != null;
    }

    public static List<ModFileInfo> getModInventory() {
        ModScan scan = readyScan();
        return scan == null ? List.of() : scan.inventory().files();
    }

    public static String getModSetFingerprint() {
        ModScan scan = readyScan();
        return scan == null ? "" : scan.inventory().fingerprint();
    }

    public static ModSetDiff getLastModSetDiff() {
        ModScan scan = readyScan();
        return scan == null ? ModSetDiff.EMPTY : scan.lastDiff();
    }

    public static Optional<JsonElement> getModScanResult(String namespace, String extractorId, String modId) {
        ModScan scan = readyScan();
        if (scan == null || namespace == null || extractorId == null || modId == null) {
            return Optional.empty();
        }
        try {
            return scan.result(namespace, extractorId, modId);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public static List<MixinFootprintEntry> getMixinsInto(String targetClassName) {
        ModScan scan = readyScan();
        if (scan == null || targetClassName == null || targetClassName.isBlank()) {
            return List.of();
        }
        try {
            List<MixinFootprintEntry> out = new ArrayList<>();
            Map<String, JsonElement> byFile = scan.resultsByFile(MixinFootprintExtractor.NAMESPACE, MixinFootprintExtractor.ID);
            for (ModFileInfo file : scan.inventory().files()) {
                JsonElement result = byFile.get(file.path());
                if (result != null && !file.mods().isEmpty()) {
                    out.addAll(MixinFootprintExtractor.toEntries(file.mods().keySet().iterator().next(), result, targetClassName));
                }
            }
            return List.copyOf(out);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** The finished scan, or null when the switch is off, nothing was started or it has not finished. */
    private static ModScan readyScan() {
        try {
            if (!isModScanEnabled()) {
                return null;
            }
            ModScan scan = ModScan.current();
            return scan != null && scan.isReady() ? scan : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
