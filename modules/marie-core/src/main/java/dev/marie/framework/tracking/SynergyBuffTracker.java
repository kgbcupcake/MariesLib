package dev.marie.framework.tracking;

import dev.marie.framework.api.ApiStatus;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks temporary value modifiers granted by source pair synergies, keyed by
 * player and value key.
 */
@ApiStatus.Internal
public final class SynergyBuffTracker {

    private record ExpiryEntry(float modifier, long expiryTick) {}

    private static final Map<UUID, Map<String, ExpiryEntry>> ACTIVE = new ConcurrentHashMap<>();

    private SynergyBuffTracker() {}

    /**
     * Activates a temporary value modifier for the given player and value key.
     *
     * @param playerId  the player's UUID
     * @param valueKey  the value key the modifier applies to
     * @param modifier  the modifier multiplier
     * @param expiryTick the game tick at which the modifier expires
     */
    public static void activate(UUID playerId, String valueKey, float modifier, long expiryTick) {
        ACTIVE.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .put(valueKey, new ExpiryEntry(modifier, expiryTick));
    }

    /**
     * Returns the currently active modifier for the given player and value key,
     * evicting the entry if it has expired.
     *
     * @param playerId   the player's UUID
     * @param valueKey   the value key to check
     * @param currentTick the current game tick
     * @return the active modifier, or {@code 1.0} if none is active or it has expired
     */
    public static float getActiveModifier(UUID playerId, String valueKey, long currentTick) {
        Map<String, ExpiryEntry> playerBuffs = ACTIVE.get(playerId);
        if (playerBuffs == null) {
            return 1.0f;
        }
        ExpiryEntry entry = playerBuffs.get(valueKey);
        if (entry == null) {
            return 1.0f;
        }
        if (currentTick >= entry.expiryTick()) {
            playerBuffs.remove(valueKey, entry);
            return 1.0f;
        }
        return entry.modifier();
    }

    /**
     * Removes all tracked buff state for the given player, e.g. on logout.
     *
     * @param playerId the player's UUID
     */
    public static void clearPlayer(UUID playerId) {
        ACTIVE.remove(playerId);
    }
}
