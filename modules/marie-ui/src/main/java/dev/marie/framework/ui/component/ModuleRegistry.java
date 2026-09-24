package dev.marie.framework.ui.component;

import dev.marie.framework.api.ApiStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * Flat, static, process-wide registry — matches the singleton-facade shape every other registry in
 * this codebase uses (e.g. {@code ScannerSpecRegistry}, {@code ColorRegistry}, {@code
 * TagRuleRegistry}): private constructor, static backing store, reachable from anywhere without a
 * caller holding a reference. This is a deliberate choice, not an oversight — see the {@code key}
 * param below for how a consumer gets independent, non-interfering module lists (e.g. one per
 * screen region) out of a single shared static instance.
 */
@ApiStatus.Experimental
public final class ModuleRegistry {

    private static final Map<String, List<ModuleFactory<?>>> MODULES = new LinkedHashMap<>();

    private ModuleRegistry() {}

    /**
     * Registers {@code factory} under {@code key}. Despite the parameter historically being called
     * "modId" and typically holding a mod's canonical ID, this registry places no meaning on the
     * string beyond "identifies one independent, ordered module list" — a consumer that wants
     * multiple independently-stacked module groups (e.g. a left column and a right column, each with
     * its own chained {@code startLocalY}/{@code startLocalX} cursor) registers each group under its
     * own distinct key, conventionally {@code <modId>.<region>} (e.g. {@code "mymod.hud.right"}),
     * rather than expecting a second registry object — this class has no instantiable/per-region
     * form, matching every other registry in this codebase. Nothing about ordering, lookup, or
     * storage differs between a plain modId key and a region-qualified one.
     */
    public static synchronized void register(String key, ModuleFactory<?> factory) {
        MODULES.computeIfAbsent(key, k -> new ArrayList<>()).add(factory);
    }

    /** Every module factory registered for {@code key} (a modId, or a region-qualified {@code <modId>.<region>} key — see {@link #register}), in registration order. Empty if none. */
    public static synchronized List<ModuleFactory<?>> get(String key) {
        return Collections.unmodifiableList(MODULES.getOrDefault(key, List.of()));
    }
}
