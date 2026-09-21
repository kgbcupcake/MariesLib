package dev.marie.framework.api.modscan;

import com.google.gson.JsonElement;
import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.modscan.ModScanDelegate;

import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Facade for the opt-in mod-file scan and cache. Nothing is scanned unless a mod registers an
 * extractor and {@code enableModScan} is on. The scan starts once, after mod loading, when the game
 * is idle (dedicated server started, or the client's loading overlay gone), so everything registered
 * during mod init is known to it and game load is never blocked. Results are cached under
 * {@code <gamedir>/marieslib/cache/<namespace>/}.
 *
 * <p>Registration methods must be called during mod initialization. Query methods are safe at any
 * time, never throw, never start a scan, and return empty until a scan has finished (or when the
 * switch is off).</p>
 */
@ApiStatus.Experimental
public final class MarieModScan {

    private MarieModScan() {}

    // ── registration ────────────────────────────────────────────

    /**
     * Registers an extractor whose results are cached per mod file.
     *
     * @throws IllegalStateException    if registration is closed
     * @throws IllegalArgumentException if {@code namespace} is null or blank, or {@code extractor} is null
     */
    public static void registerExtractor(String namespace, ModScanExtractor extractor) {
        ModScanDelegate.registerModScanExtractor(namespace, extractor);
    }

    /** Alias for {@link #registerExtractor(String, ModScanExtractor)}. */
    public static void addExtractor(String namespace, ModScanExtractor extractor) {
        ModScanDelegate.registerModScanExtractor(namespace, extractor);
    }

    /**
     * Registers a listener told when a scan starts and finishes, on the scan thread.
     *
     * @throws IllegalStateException    if registration is closed
     * @throws IllegalArgumentException if {@code listener} is null
     */
    public static void registerListener(ModScanListener listener) {
        ModScanDelegate.registerModScanListener(listener);
    }

    /** Alias for {@link #registerListener(ModScanListener)}. */
    public static void addListener(ModScanListener listener) {
        ModScanDelegate.registerModScanListener(listener);
    }

    /**
     * Registers a deferral: the scan waits, polling, while any deferral returns true. Called on the
     * scan thread; must be cheap, thread-safe, and eventually return false.
     *
     * @throws IllegalStateException    if registration is closed
     * @throws IllegalArgumentException if {@code busy} is null
     */
    public static void registerDeferral(BooleanSupplier busy) {
        ModScanDelegate.registerModScanDeferral(busy);
    }

    /**
     * Asks for the built-in mixin footprint scan (extractor id {@code "marieslib:mixin_footprint"},
     * namespace {@code "marieslib"}) that {@link #getMixinsInto(String)} answers from. Safe to call
     * from several mods; it registers once.
     *
     * @throws IllegalStateException if registration is closed
     */
    public static void registerMixinFootprint() {
        ModScanDelegate.registerMixinFootprintScan();
    }

    // ── queries ─────────────────────────────────────────────────

    /** The installed mod files with their mod ids and versions; empty if off or not finished. */
    public static List<ModFileInfo> getInventory() {
        return ModScanDelegate.getModInventory();
    }

    /** Stable fingerprint (SHA-256 hex) of the whole mod set; empty string if not ready. */
    public static String getFingerprint() {
        return ModScanDelegate.getModSetFingerprint();
    }

    /** What changed since the previous run's scan; empty when nothing did or if not ready. */
    public static ModSetDiff getLastDiff() {
        return ModScanDelegate.getLastModSetDiff();
    }

    /**
     * The cached result of one extractor for the file holding {@code modId}; empty if off, not
     * finished, nothing was produced for that file, or any argument is null.
     */
    public static Optional<JsonElement> getResult(String namespace, String extractorId, String modId) {
        return ModScanDelegate.getModScanResult(namespace, extractorId, modId);
    }

    /**
     * Mixins that target {@code targetClassName} (Mojang name, dotted or slash-separated), from the
     * cached mixin footprint; empty unless {@link #registerMixinFootprint()} was called and the scan
     * has finished. Fabric-origin mods loaded through Sinytra Connector are matched via Connector's
     * Mojang-remapped copy when it has one. Entries whose target is still an unmapped intermediary
     * name are never dropped: they are returned for every query with
     * {@link MixinFootprintEntry#unresolved()} set and the raw name in {@code targetClass}.
     */
    public static List<MixinFootprintEntry> getMixinsInto(String targetClassName) {
        return ModScanDelegate.getMixinsInto(targetClassName);
    }

    /** True if the {@code enableModScan} config switch is on (scanning is still opt-in by registration). */
    public static boolean isEnabled() {
        return ModScanDelegate.isModScanEnabled();
    }

    /** True once a scan has finished and its results can be queried. */
    public static boolean isReady() {
        return ModScanDelegate.isModScanReady();
    }
}
