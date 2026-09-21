package dev.marie.framework.modscan;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.api.modscan.ModFileHandle;
import dev.marie.framework.api.modscan.ModFileInfo;
import dev.marie.framework.api.modscan.ModScanFinish;
import dev.marie.framework.api.modscan.ModScanListener;
import dev.marie.framework.api.modscan.ModScanStart;
import dev.marie.framework.api.modscan.ModSetDiff;
import dev.marie.framework.api.registry.ModScanRegistry;
import dev.marie.framework.config.MariesLibConfigHolder;
import dev.marie.framework.core.MarieCore;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * One mod-file scan: waits for deferrals, takes a metadata-only inventory, deep-scans only the files
 * that are new, changed or lack a cached result for a registered extractor, and publishes the results.
 * Runs on a single low-priority daemon thread and is cancellable. An instance runs at most once;
 * the process-wide instance is started at most once by {@link #startGlobal}.
 */
@ApiStatus.Internal
public final class ModScan {

    private static final long DEFERRAL_POLL_MS = 250;
    private static final int PROGRESS_LOG_EVERY = 100;

    private static final AtomicBoolean GLOBAL_STARTED = new AtomicBoolean();
    private static volatile ModScan current;

    /** Everything a finished scan knows; replaced as one unit so readers never see a half-built state. */
    private record Snapshot(ModInventory inventory, ModSetDiff diff, Map<String, Map<String, JsonElement>> results,
                            Map<String, ModFileInfo> fileByMod) {}

    private final Path cacheRoot;
    private final Supplier<ModInventory> inventorySource;
    private final List<ModScanRegistry.ExtractorEntry> extractors;
    private final List<ModScanListener> listeners;
    private final List<BooleanSupplier> deferrals;

    private final AtomicBoolean ran = new AtomicBoolean();
    private volatile boolean cancelled;
    private volatile Thread thread;
    private volatile Snapshot snapshot;
    private final AtomicInteger progressDone = new AtomicInteger();
    private volatile int progressTotal;

    public ModScan(Path cacheRoot, Supplier<ModInventory> inventorySource,
                   List<ModScanRegistry.ExtractorEntry> extractors, List<ModScanListener> listeners,
                   List<BooleanSupplier> deferrals) {
        this.cacheRoot = cacheRoot;
        this.inventorySource = inventorySource;
        this.extractors = List.copyOf(extractors);
        this.listeners = List.copyOf(listeners);
        this.deferrals = List.copyOf(deferrals);
    }

    // ── process-wide instance ───────────────────────────────────

    /** What to start once the game is idle; set at load-complete, consumed by the first ready signal. */
    private record Armed(Path cacheRoot, Supplier<ModInventory> inventorySource, boolean dedicatedServer) {}

    private static volatile Armed armed;
    private static volatile boolean clientReady;
    private static volatile boolean serverStarted;

    /**
     * Called at load-complete (registrations are frozen). Does not start anything: the scan begins only
     * after the game is idle, i.e. on a dedicated server once it has started, on a client once the
     * loading overlay is gone ({@link #onClientReady()}) or, if nothing reports that, once an
     * integrated server has started.
     */
    public static void arm(Path cacheRoot, Supplier<ModInventory> inventorySource, boolean dedicatedServer) {
        armed = new Armed(cacheRoot, inventorySource, dedicatedServer);
        tryStart();
    }

    /** Client only: the loading overlay has finished. Safe to call repeatedly. */
    public static void onClientReady() {
        clientReady = true;
        tryStart();
    }

    /** {@code ServerStartedEvent}: the (dedicated or integrated) server finished starting. */
    public static void onServerStarted() {
        serverStarted = true;
        tryStart();
    }

    private static void tryStart() {
        Armed a = armed;
        if (a == null) {
            return;
        }
        boolean idle = a.dedicatedServer() ? serverStarted : (clientReady || serverStarted);
        if (idle) {
            armed = null;
            startGlobal(a.cacheRoot(), a.inventorySource());
        }
    }

    /**
     * Starts the scan once, from the frozen registries, if the config switch is on and at least one
     * extractor is registered. Returns whether a scan was started. Never blocks.
     */
    public static boolean startGlobal(Path cacheRoot, Supplier<ModInventory> inventorySource) {
        if (!MariesLibConfigHolder.get().enableModScan) {
            MarieCore.LOGGER.debug("[ModScan] Disabled by config, not scanning");
            return false;
        }
        List<ModScanRegistry.ExtractorEntry> extractors = ModScanRegistry.getExtractors();
        if (extractors.isEmpty()) {
            MarieCore.LOGGER.debug("[ModScan] No extractor registered, not scanning");
            return false;
        }
        if (!GLOBAL_STARTED.compareAndSet(false, true)) {
            return false;
        }
        ModScan scan = new ModScan(cacheRoot, inventorySource, extractors, ModScanRegistry.getListeners(),
                ModScanRegistry.getDeferrals());
        current = scan;
        return scan.startAsync();
    }

    /** The process-wide scan, or null if none was started. */
    public static ModScan current() {
        return current;
    }

    /** Test hook: forget the process-wide scan (cancelling it) so another can start. */
    public static void resetForTests() {
        ModScan c = current;
        if (c != null) {
            c.cancel();
        }
        current = null;
        armed = null;
        clientReady = false;
        serverStarted = false;
        GLOBAL_STARTED.set(false);
    }

    // ── control ─────────────────────────────────────────────────

    /** Runs the scan on its own daemon thread. Single-flight: false if this instance already ran or is running. */
    public boolean startAsync() {
        if (!ran.compareAndSet(false, true)) {
            return false;
        }
        Thread t = new Thread(this::runGuarded, "MariesLib-ModScan");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        thread = t;
        t.start();
        return true;
    }

    /** Runs the scan on the calling thread (tests). Same single-flight rule as {@link #startAsync()}. */
    public boolean runNow() {
        if (!ran.compareAndSet(false, true)) {
            return false;
        }
        runGuarded();
        return true;
    }

    public void cancel() {
        cancelled = true;
        Thread t = thread;
        if (t != null) {
            t.interrupt();
        }
    }

    public boolean isReady() {
        return snapshot != null;
    }

    /** Completed and total (file, extractor) pairs so far; total is 0 until the inventory is known. */
    public int[] progress() {
        return new int[] {progressDone.get(), progressTotal};
    }

    // ── results ─────────────────────────────────────────────────

    public ModInventory inventory() {
        Snapshot s = snapshot;
        return s == null ? ModInventory.ofFiles(List.of()) : s.inventory();
    }

    public ModSetDiff lastDiff() {
        Snapshot s = snapshot;
        return s == null ? ModSetDiff.EMPTY : s.diff();
    }

    /** The result for the file that holds {@code modId}, if that extractor produced one for it. */
    public Optional<JsonElement> result(String namespace, String extractorId, String modId) {
        Snapshot s = snapshot;
        if (s == null) {
            return Optional.empty();
        }
        ModFileInfo file = s.fileByMod().get(modId);
        Map<String, JsonElement> byFile = s.results().get(resultKey(namespace, extractorId));
        if (file == null || byFile == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byFile.get(file.path()));
    }

    /** Every file's result for one extractor, keyed by file path. */
    public Map<String, JsonElement> resultsByFile(String namespace, String extractorId) {
        Snapshot s = snapshot;
        Map<String, JsonElement> m = s == null ? null : s.results().get(resultKey(namespace, extractorId));
        return m == null ? Map.of() : m;
    }

    private static String resultKey(String namespace, String extractorId) {
        return namespace + '\u0000' + extractorId;
    }

    // ── the scan ────────────────────────────────────────────────

    private void runGuarded() {
        try {
            run();
        } catch (Throwable t) {
            MarieCore.LOGGER.error("[ModScan] Scan aborted", t);
        }
    }

    private void run() {
        long startNanos = System.nanoTime();
        if (!awaitDeferrals()) {
            return;
        }
        ModScanCache cache = new ModScanCache(cacheRoot);
        ModInventory inventory = inventorySource.get();
        List<ModFileInfo> files = inventory.files();
        Optional<List<ModFileInfo>> previous = cache.loadInventory();
        ModSetDiff diff = ModInventory.diff(previous.orElse(List.of()), files);

        progressTotal = files.size() * extractors.size();
        MarieCore.LOGGER.info("[ModScan] Scanning {} mod files with {} extractors ({} added, {} removed, {} changed since last run)",
                files.size(), extractors.size(), diff.addedFiles().size(), diff.removedFiles().size(), diff.changedFiles().size());
        ModScanStart start = new ModScanStart(diff.addedFiles().size(), diff.removedFiles().size(),
                diff.changedFiles().size(), files.size());
        for (ModScanListener l : listeners) {
            try {
                l.onScanStarted(start);
            } catch (Throwable t) {
                MarieCore.LOGGER.warn("[ModScan] Listener failed in onScanStarted", t);
            }
        }

        int scanned = 0;
        int skipped = 0;
        int failed = 0;
        Map<String, Map<String, JsonElement>> results = new LinkedHashMap<>();
        Set<String> seenExtractors = new HashSet<>();
        Set<String> livePaths = ModInventory.paths(files);
        for (ModScanRegistry.ExtractorEntry entry : extractors) {
            String id = entry.extractor().id();
            int version = entry.extractor().version();
            String key = resultKey(entry.namespace(), id);
            if (!seenExtractors.add(key)) {
                MarieCore.LOGGER.warn("[ModScan] Ignoring duplicate extractor {} in namespace {}", id, entry.namespace());
                continue;
            }
            Map<String, JsonElement> byFile = new LinkedHashMap<>();
            for (ModFileInfo file : files) {
                if (cancelled) {
                    return;
                }
                Optional<JsonElement> cached = cache.lookup(entry.namespace(), id, version, file);
                if (cached.isPresent()) {
                    byFile.put(file.path(), cached.get());
                    skipped++;
                } else {
                    try (ModFileHandle handle = inventory.open(file)) {
                        JsonElement result = entry.extractor().extract(handle);
                        result = result == null ? JsonNull.INSTANCE : result;
                        cache.store(entry.namespace(), id, version, file, result);
                        byFile.put(file.path(), result);
                        scanned++;
                    } catch (Exception e) {
                        failed++;
                        MarieCore.LOGGER.warn("[ModScan] Extractor {} failed on {}: {}", id, file.path(), e.toString());
                    }
                }
                int done = progressDone.incrementAndGet();
                if (done % PROGRESS_LOG_EVERY == 0) {
                    MarieCore.LOGGER.info("[ModScan] Progress {}/{}", done, progressTotal);
                }
            }
            cache.retain(entry.namespace(), id, livePaths);
            results.put(key, byFile);
        }
        if (cancelled) {
            return;
        }
        cache.flush();
        cache.saveInventory(files);

        Map<String, ModFileInfo> fileByMod = new LinkedHashMap<>();
        for (ModFileInfo f : files) {
            for (String modId : f.mods().keySet()) {
                fileByMod.putIfAbsent(modId, f);
            }
        }
        snapshot = new Snapshot(inventory, diff, results, fileByMod);

        Duration took = Duration.ofNanos(System.nanoTime() - startNanos);
        MarieCore.LOGGER.info("[ModScan] Done in {} ms: {} scanned, {} from cache, {} failed",
                took.toMillis(), scanned, skipped, failed);
        ModScanFinish finish = new ModScanFinish(took, scanned, skipped, failed);
        for (ModScanListener l : listeners) {
            try {
                l.onScanFinished(finish);
            } catch (Throwable t) {
                MarieCore.LOGGER.warn("[ModScan] Listener failed in onScanFinished", t);
            }
        }
    }

    /** Blocks while any deferral says busy; false if cancelled meanwhile. A throwing deferral counts as not busy. */
    private boolean awaitDeferrals() {
        while (true) {
            if (cancelled) {
                return false;
            }
            boolean busy = false;
            for (BooleanSupplier d : deferrals) {
                try {
                    busy |= d.getAsBoolean();
                } catch (Throwable t) {
                    MarieCore.LOGGER.warn("[ModScan] A deferral threw and is treated as not busy", t);
                }
            }
            if (!busy) {
                return true;
            }
            try {
                Thread.sleep(DEFERRAL_POLL_MS);
            } catch (InterruptedException e) {
                if (cancelled) {
                    return false;
                }
            }
        }
    }
}
