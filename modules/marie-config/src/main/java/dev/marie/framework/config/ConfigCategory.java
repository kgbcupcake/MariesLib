package dev.marie.framework.config;

import dev.marie.framework.api.ApiStatus;

/**
 * A named grouping {@link ConfigValueDefinition}s are filed under (e.g. "Gameplay", "HUD"),
 * mirroring how a Cloth Config screen or the item editor's own File-menu pages group related
 * settings. {@code sortOrder} is display order only — lower sorts first — and has no effect on
 * registration or lookup.
 *
 * @param id        stable identifier, e.g. {@code "nourished.gameplay"}
 * @param label     display label shown to the player
 * @param sortOrder display order relative to other categories; lower sorts first
 */
@ApiStatus.Experimental
public record ConfigCategory(String id, String label, int sortOrder) {

    public ConfigCategory {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id cannot be null or blank");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label cannot be null or blank");
        }
    }
}
