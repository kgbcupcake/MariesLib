package dev.marie.framework.api.value;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.api.impl.SimpleValueSourceTrigger;
import net.minecraft.resources.ResourceLocation;

/**
 * Represents a context in which a value source was applied to a player.
 * Decouple the pipeline from any single triggering action (e.g. item consumption) — any action can be a trigger.
 *
 * <p>Create instances via the static factory methods.</p>
 */
@ApiStatus.Stable
public interface ValueSourceTrigger {

    /** The logical trigger category. */
    enum TriggerType {
        ITEM_CONSUMED,   // item use finished
        ITEM_CRAFTED,    // Player crafted an item
        BLOCK_BROKEN,    // Player broke a block
        ENTITY_KILLED,   // Player killed an entity
        TRANSACTION,     // Abstract numeric transaction (e.g. EMC)
        TICK,            // Periodic tick-based trigger
        CUSTOM           // Anything else — discriminated by triggerId
    }

    /** The type of action that triggered this source application. */
    TriggerType type();

    /**
     * The source identifier — for ITEM_* this is the item's ResourceLocation
     * string; for TRANSACTION/CUSTOM it is a mod-defined key.
     */
    String sourceId();

    /**
     * Optional numeric payload.
     * For ITEM_CONSUMED: payload value from the triggering action.
     * For TRANSACTION: the transaction magnitude.
     * For others: 0.
     */
    double payload();

    /**
     * An optional free-form trigger ID for CUSTOM triggers,
     * used to discriminate between different custom trigger types.
     * Returns empty string for non-CUSTOM triggers.
     */
    String triggerId();

    // ── Static factories ────────────────────────────────────────────

    static ValueSourceTrigger itemConsumed(ResourceLocation itemId, double payload) {
        return new SimpleValueSourceTrigger(
                TriggerType.ITEM_CONSUMED, itemId.toString(), payload, "");
    }

    static ValueSourceTrigger itemCrafted(ResourceLocation itemId) {
        return new SimpleValueSourceTrigger(
                TriggerType.ITEM_CRAFTED, itemId.toString(), 0, "");
    }

    static ValueSourceTrigger blockBroken(ResourceLocation blockId) {
        return new SimpleValueSourceTrigger(
                TriggerType.BLOCK_BROKEN, blockId.toString(), 0, "");
    }

    static ValueSourceTrigger entityKilled(ResourceLocation entityId) {
        return new SimpleValueSourceTrigger(
                TriggerType.ENTITY_KILLED, entityId.toString(), 0, "");
    }

    static ValueSourceTrigger transaction(String transactionKey, double magnitude) {
        return new SimpleValueSourceTrigger(
                TriggerType.TRANSACTION, transactionKey, magnitude, "");
    }

    static ValueSourceTrigger tick(String tickKey) {
        return new SimpleValueSourceTrigger(
                TriggerType.TICK, tickKey, 0, "");
    }

    static ValueSourceTrigger custom(String sourceId, String triggerId, double payload) {
        return new SimpleValueSourceTrigger(
                TriggerType.CUSTOM, sourceId, payload, triggerId);
    }
}
