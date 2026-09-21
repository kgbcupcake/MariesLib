package dev.marie.framework.api.registry;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.api.modscan.ModScanExtractor;
import dev.marie.framework.api.modscan.ModScanListener;
import dev.marie.framework.registry.ListRegistry;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Internal storage for mod-scan extractors, listeners and deferrals registered via the public API.
 * Frozen right before the scan starts, so everything registered during mod init is known to it.
 */
@ApiStatus.Internal
public final class ModScanRegistry {

    public record ExtractorEntry(String namespace, ModScanExtractor extractor) {}

    private static final ListRegistry<ExtractorEntry> EXTRACTORS =
            new ListRegistry<>("ModScanExtractorRegistry", null);
    private static final ListRegistry<ModScanListener> LISTENERS =
            new ListRegistry<>("ModScanListenerRegistry", null);
    private static final ListRegistry<BooleanSupplier> DEFERRALS =
            new ListRegistry<>("ModScanDeferralRegistry", null);

    private ModScanRegistry() {}

    @ApiStatus.Internal
    public static void freezeInternal() {
        EXTRACTORS.freeze();
        LISTENERS.freeze();
        DEFERRALS.freeze();
    }

    @ApiStatus.Internal
    public static void resetInternal() {
        EXTRACTORS.reset();
        LISTENERS.reset();
        DEFERRALS.reset();
    }

    public static void registerExtractor(String namespace, ModScanExtractor extractor) {
        EXTRACTORS.register(new ExtractorEntry(namespace, extractor));
    }

    public static void registerListener(ModScanListener listener) {
        LISTENERS.register(listener);
    }

    public static void registerDeferral(BooleanSupplier busy) {
        DEFERRALS.register(busy);
    }

    public static List<ExtractorEntry> getExtractors() {
        return EXTRACTORS.values();
    }

    public static List<ModScanListener> getListeners() {
        return LISTENERS.values();
    }

    public static List<BooleanSupplier> getDeferrals() {
        return DEFERRALS.values();
    }
}
