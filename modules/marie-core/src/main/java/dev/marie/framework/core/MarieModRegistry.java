package dev.marie.framework.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import dev.marie.framework.api.ApiStatus;

/**
 * Insertion-ordered registry of all mods that have registered a
 * {@link MarieContext}. Supports the upcoming multi-mod config UI
 * and any feature that needs to enumerate consuming mods.
 */
@ApiStatus.Experimental
public final class MarieModRegistry {

    private static final Map<String, MarieContext> MODS = new LinkedHashMap<>();

    private MarieModRegistry() {}

    public static synchronized void register(MarieContext ctx) {
        MODS.put(ctx.modId(), ctx);
    }

    @Nullable
    public static synchronized MarieContext get(String modId) {
        return MODS.get(modId);
    }

    public static synchronized MarieContext getOrThrow(String modId) {
        MarieContext ctx = MODS.get(modId);
        if (ctx == null) {
            throw new IllegalStateException("No MarieContext registered for mod: " + modId);
        }
        return ctx;
    }

    public static synchronized List<MarieContext> getAll() {
        return Collections.unmodifiableList(List.copyOf(MODS.values()));
    }

    public static synchronized MarieContext getPrimary() {
        MarieContext ctx = getPrimaryOrNull();
        if (ctx == null) {
            throw new IllegalStateException("No MarieContext has been registered");
        }
        return ctx;
    }

    @Nullable
    public static synchronized MarieContext getPrimaryOrNull() {
        if (MODS.isEmpty()) {
            return null;
        }
        return MODS.values().iterator().next();
    }

    public static synchronized boolean isRegistered() {
        return !MODS.isEmpty();
    }

    public static synchronized boolean isRegistered(String modId) {
        return MODS.containsKey(modId);
    }

    /**
     * Invokes {@code action} once per registered mod context, in registration order. Use this for
     * hooks that apply to a whole player/tracking blob rather than a single value key (effect
     * application, tracking sync, respawn handling, tracker-period callbacks) so every attached mod
     * gets a chance to act on shared player state instead of only the last-attached one.
     */
    @ApiStatus.Experimental
    public static void forEach(Consumer<MarieContext> action) {
        for (MarieContext ctx : getAll()) {
            action.accept(ctx);
        }
    }
}
