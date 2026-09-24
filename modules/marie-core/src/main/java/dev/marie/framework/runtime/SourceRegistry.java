package dev.marie.framework.runtime;

import com.mojang.logging.LogUtils;
import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.api.marieapi.MarieAPIState;
import dev.marie.framework.scanner.ItemScanner;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Framework-level registry for external and scanner-applied source classifications.
 */
@ApiStatus.Internal
public class SourceRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** @GuardedBy("itself — ConcurrentHashSet") */
    private static final Set<String> WARNED_ITEMS = ConcurrentHashMap.newKeySet();

    /** @GuardedBy("itself — ConcurrentHashSet") Per-reload dedupe for empty blend warnings. */
    private static final Set<String> EMPTY_BLEND_WARNED = ConcurrentHashMap.newKeySet();

    /** @GuardedBy("itself — ConcurrentHashSet") Per-session dedupe for external classification cap warnings. */
    private static final Set<String> WARNED_CAP_ITEMS = ConcurrentHashMap.newKeySet();

    private static final int EXTERNAL_CLASSIFICATION_CAP = 16384;

    /** @GuardedBy("itself — ConcurrentHashMap") */
    private static final Map<ResourceLocation, Map<String, Float>> EXTERNAL_CLASSIFICATIONS = new ConcurrentHashMap<>();

    /**
     * @GuardedBy("itself — ConcurrentHashMap") Registrations made outside a datapack reload
     * (mod init, KubeJS startup scripts, runtime API calls) that must survive {@link #clearExternalClassifications()}.
     * Datapack-reload-scoped registrations are deliberately kept out of this map — see {@link #registerClassification}.
     */
    private static final Map<ResourceLocation, Map<String, Float>> API_REGISTERED_CLASSIFICATIONS = new ConcurrentHashMap<>();

    /** @GuardedBy("itself — ConcurrentHashMap") */
    private static final Map<ResourceLocation, Map<String, Float>> SCANNER_CLASSIFICATIONS = new ConcurrentHashMap<>();

    /**
     * @GuardedBy("itself — ConcurrentHashMap") Genuine {@link #registerClassification} entries that
     * were already present for a sourceId at the moment an authoritative override was first applied
     * on top of them. Held aside so {@link #unregisterClassification} (the override going away) can
     * restore the real registration instead of destroying it. Only ever populated from an entry that
     * came from a genuine API call — override-mirrored values are never copied in here, so a restored
     * entry is always a real API registration and never a stale datapack/tag-derived one.
     */
    private static final Map<ResourceLocation, Map<String, Float>> PRE_OVERRIDE_API_CLASSIFICATIONS = new ConcurrentHashMap<>();

    // sourceIds exclusively owned by an enabled SourceClassificationRegistry override; non-override registerClassification calls for these are ignored.
    private static final Set<ResourceLocation> OVERRIDE_LOCKED_SOURCES = ConcurrentHashMap.newKeySet();

    private SourceRegistry() {}

    public static void registerClassification(ResourceLocation sourceId, String valueKey, float amount) {
        if (OVERRIDE_LOCKED_SOURCES.contains(sourceId)) {
            LOGGER.debug("[SourceRegistry] Ignoring {} -> {}: source has an authoritative classification override",
                    sourceId, valueKey);
            return;
        }
        if (EXTERNAL_CLASSIFICATIONS.size() >= EXTERNAL_CLASSIFICATION_CAP && !EXTERNAL_CLASSIFICATIONS.containsKey(sourceId)) {
            if (WARNED_CAP_ITEMS.add(sourceId.toString())) {
                LOGGER.warn("[SourceRegistry] External classification cap ({}) reached — ignoring: {} -> {}",
                        EXTERNAL_CLASSIFICATION_CAP, sourceId, valueKey);
            }
            return;
        }
        EXTERNAL_CLASSIFICATIONS.computeIfAbsent(sourceId, k -> new ConcurrentHashMap<>()).put(valueKey, amount);
        // Only registrations made outside a datapack reload (mod init / KubeJS startup / runtime API) are
        // mirrored so they can survive clearExternalClassifications(). Datapack-reload-scoped callers —
        // value-tag bridging and the datapack source_classifications/*.json directory — rebuild their
        // entries in full on every reload, so mirroring them here would resurrect entries that were later
        // removed from the source files (stale EXTERNAL_CLASSIFICATION), which is exactly what this guards against.
        if (MarieAPIState.getPhase() != MarieAPIState.Phase.DATAPACK_RELOAD) {
            API_REGISTERED_CLASSIFICATIONS.computeIfAbsent(sourceId, k -> new ConcurrentHashMap<>()).put(valueKey, amount);
        }
        LOGGER.debug("[SourceRegistry] Registered external classification: {} -> {} ({})", sourceId, valueKey, amount);
    }

    // Replaces sourceId's classification exclusively with an authoritative override's values and locks out future non-override registerClassification calls for it.
    static void applyAuthoritativeOverride(ResourceLocation sourceId, Map<String, Float> values) {
        Map<String, Float> sanitized = new ConcurrentHashMap<>();
        for (Map.Entry<String, Float> e : values.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                sanitized.put(e.getKey(), e.getValue());
            }
        }
        // The first time an override lands on this source, stash whatever genuine
        // registerClassification() entry is sitting underneath it so unregisterClassification() can
        // put it back later. Guarded on the override lock so the repeated override pushes that
        // happen once per datapack reload don't overwrite that snapshot with the override's own
        // mirrored values. While the lock is held, registerClassification() is refused for this
        // source, so the snapshot taken here stays the correct genuine state to restore.
        if (!OVERRIDE_LOCKED_SOURCES.contains(sourceId)) {
            Map<String, Float> genuine = API_REGISTERED_CLASSIFICATIONS.get(sourceId);
            if (genuine != null) {
                PRE_OVERRIDE_API_CLASSIFICATIONS.put(sourceId, new ConcurrentHashMap<>(genuine));
            }
        }
        EXTERNAL_CLASSIFICATIONS.put(sourceId, sanitized);
        API_REGISTERED_CLASSIFICATIONS.put(sourceId, new ConcurrentHashMap<>(sanitized));
        OVERRIDE_LOCKED_SOURCES.add(sourceId);
    }

    // Removes one sourceId's entries from both maps and its override lock; used by SourceClassificationRegistry to drop stale entries before re-pushing a reload.
    static void unregisterClassification(ResourceLocation sourceId) {
        OVERRIDE_LOCKED_SOURCES.remove(sourceId);
        Map<String, Float> genuine = PRE_OVERRIDE_API_CLASSIFICATIONS.remove(sourceId);
        if (genuine != null) {
            // A real registerClassification() entry existed before the override was applied — restore
            // it rather than dropping the source outright, so removing an override can't destroy a
            // genuine mod-init/KubeJS registration that was underneath it.
            EXTERNAL_CLASSIFICATIONS.put(sourceId, new ConcurrentHashMap<>(genuine));
            API_REGISTERED_CLASSIFICATIONS.put(sourceId, new ConcurrentHashMap<>(genuine));
        } else {
            EXTERNAL_CLASSIFICATIONS.remove(sourceId);
            API_REGISTERED_CLASSIFICATIONS.remove(sourceId);
        }
    }

    public static void clearExternalClassifications() {
        EXTERNAL_CLASSIFICATIONS.clear();
        for (Map.Entry<ResourceLocation, Map<String, Float>> entry : API_REGISTERED_CLASSIFICATIONS.entrySet()) {
            EXTERNAL_CLASSIFICATIONS.put(entry.getKey(), new ConcurrentHashMap<>(entry.getValue()));
        }
    }

    public static void applyFromScanner(Map<ResourceLocation, Map<String, Float>> perItemValues) {
        SCANNER_CLASSIFICATIONS.clear();
        for (Map.Entry<ResourceLocation, Map<String, Float>> entry : perItemValues.entrySet()) {
            ResourceLocation itemId = entry.getKey();
            if (EXTERNAL_CLASSIFICATIONS.containsKey(itemId)) {
                continue;
            }
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(itemId));
            if (ItemScanner.hasValueTag(stack)) {
                continue;
            }
            Map<String, Float> inner = entry.getValue();
            if (inner == null || inner.isEmpty()) {
                continue;
            }
            SCANNER_CLASSIFICATIONS.put(itemId, new ConcurrentHashMap<>(inner));
        }
        RuntimeResolver.getInstance().invalidateCache();
        LOGGER.info("[SourceRegistry] Scanner applied {} classifications", SCANNER_CLASSIFICATIONS.size());
    }

    public static void clearScannerClassifications() {
        SCANNER_CLASSIFICATIONS.clear();
    }

    public static void clearPerReloadWarnings() {
        WARNED_ITEMS.clear();
        EMPTY_BLEND_WARNED.clear();
    }

    public static void clearSessionWarnings() {
        WARNED_CAP_ITEMS.clear();
    }

    public static Map<String, Float> getExternalClassification(ResourceLocation sourceId) {
        Map<String, Float> api = EXTERNAL_CLASSIFICATIONS.get(sourceId);
        if (api != null) {
            return api;
        }
        return SCANNER_CLASSIFICATIONS.get(sourceId);
    }

    public static boolean hasAuthoritativeClassification(ResourceLocation sourceId) {
        return EXTERNAL_CLASSIFICATIONS.containsKey(sourceId);
    }

    static Map<ResourceLocation, Map<String, Float>> getAllExternalView() {
        return Collections.unmodifiableMap(EXTERNAL_CLASSIFICATIONS);
    }

    static boolean hasApiClassification(ResourceLocation sourceId) {
        return EXTERNAL_CLASSIFICATIONS.containsKey(sourceId);
    }

    static boolean hasScannerClassification(ResourceLocation sourceId) {
        return SCANNER_CLASSIFICATIONS.containsKey(sourceId);
    }
}
