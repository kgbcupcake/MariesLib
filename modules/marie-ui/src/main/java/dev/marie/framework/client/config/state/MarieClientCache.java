package dev.marie.framework.client.config.state;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.core.IMarieConfig;
import dev.marie.framework.tracking.TrackingData;
import dev.marie.framework.tracking.DiminishingReturnsConfig;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApiStatus.Internal
public class MarieClientCache {

    public static final int FLASH_MS = 2000;
    private static final float INCREASE_EPSILON = 0.005f;

    /**
     * Atomic client tracking snapshot: {@link #tracking} plus HUD lists that come from the server as a unit
     * (full sync derives lists from {@link TrackingData}; delta sync carries lists on the wire separately).
     * volatile: replaced whole on the network thread; read concurrently on the render thread.
     */
    private static volatile Snapshot current;

    private record Snapshot(
            TrackingData tracking,
            List<String> recentSourceIds,
            List<String> neglectedCategories,
            List<String> fatiguedFamilies
    ) {
        static Snapshot initial() {
            TrackingData d = TrackingData.createNew();
            d.setMemoryConfig(injectClientMemoryConfig());
            return new Snapshot(d, List.of(), List.of(), List.of());
        }

        static Snapshot fromFullTracking(TrackingData data) {
            TrackingData snapshot = TrackingData.copySnapshot(data);
            snapshot.setMemoryConfig(injectClientMemoryConfig());
            return new Snapshot(
                    snapshot,
                    snapshot.recentIds,
                    snapshot.getMostNeglectedCategories(2),
                    snapshot.getMostFatiguedFamilies(2, snapshot.lastTickTime)
            );
        }

        static Snapshot fromDelta(
                TrackingData next,
                List<String> recentSourceIds,
                List<String> neglectedCategories,
                List<String> fatiguedFamilies) {
            return new Snapshot(next, recentSourceIds, neglectedCategories, fatiguedFamilies);
        }
    }

    private static final Map<String, Long> lastValueIncreaseMs = new ConcurrentHashMap<>();
    /** Skip recording flashes on the first sync (login) so zeros-to-values does not flash every bar. */
    private static boolean firstClientSync = true;

    private static DiminishingReturnsConfig injectClientMemoryConfig() {
        return IMarieConfig.get().trackingMemoryConfig();
    }

    /** Resets client-side snapshot diagnostics, e.g. on disconnect. */
    public static void resetDiagnostics() {
        firstClientSync = true;
    }

    private static Snapshot currentSnapshot() {
        Snapshot snap = current;
        if (snap != null) {
            return snap;
        }
        synchronized (MarieClientCache.class) {
            snap = current;
            if (snap == null) {
                current = snap = Snapshot.initial();
            }
            return snap;
        }
    }

    /**
     * Applies incoming full tracking from the server: updates cache, records value increases for bar flash
     * (except on the very first client sync this session). Used on login, respawn, dimension change.
     */
    public static void onFullTrackingSync(TrackingData data) {
        Snapshot prev = currentSnapshot();
        Snapshot next = Snapshot.fromFullTracking(data);
        if (!firstClientSync) {
            recordValueIncreases(prev.tracking.values, next.tracking.values);
        }
        firstClientSync = false;
        current = next;
    }

    /**
     * Applies incoming delta from the server: values and source-memory state used for client-side previews.
     */
    public static void onTrackingDelta(
            TrackingData next,
            List<String> recentSourceIds,
            List<String> neglectedCategories,
            List<String> fatiguedFamilies) {
        Snapshot prev = currentSnapshot();
        if (!firstClientSync) {
            recordValueIncreases(prev.tracking.values, next.values);
        }
        firstClientSync = false;
        next.setMemoryConfig(injectClientMemoryConfig());
        current = Snapshot.fromDelta(next, recentSourceIds, neglectedCategories, fatiguedFamilies);
    }

    private static void recordValueIncreases(Map<String, Float> prev, Map<String, Float> next) {
        List<String> keys = TrackingData.barOrder();
        long now = System.currentTimeMillis();
        for (String key : keys) {
            float before = prev.getOrDefault(key, 0f);
            float after = next.getOrDefault(key, 0f);
            if (after > before + INCREASE_EPSILON) {
                lastValueIncreaseMs.put(key, now);
            }
        }
    }

    /**
     * Highlight strength in [0, 1]: full at flash start, zero after {@link #FLASH_MS}.
     */
    public static float flashAlpha(String key) {
        Long t = lastValueIncreaseMs.get(key);
        if (t == null) {
            return 0f;
        }
        long elapsed = System.currentTimeMillis() - t;
        if (elapsed >= FLASH_MS) {
            return 0f;
        }
        return Mth.clamp(1f - (float) elapsed / (float) FLASH_MS, 0f, 1f);
    }

    public static TrackingData get() {
        return currentSnapshot().tracking;
    }

    public static float getBalanceScore() {
        return currentSnapshot().tracking.getBalanceScore();
    }

    public static List<String> getRecentSourceIds() {
        return currentSnapshot().recentSourceIds;
    }

    public static List<String> getNeglectedCategories() {
        return currentSnapshot().neglectedCategories;
    }

    public static List<String> getFatiguedFamilies() {
        return currentSnapshot().fatiguedFamilies;
    }
}
