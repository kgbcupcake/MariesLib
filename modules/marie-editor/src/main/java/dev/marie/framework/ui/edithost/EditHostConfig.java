package dev.marie.framework.ui.edithost;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.api.EditableModule;
import dev.marie.framework.ui.api.PlacementStrategy;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/** Everything {@code ModuleEditHost.Builder} collects, handed to {@link EditHostCore}. */
@ApiStatus.Internal
public record EditHostConfig(
        PersistenceProvider store,
        PlacementStrategy defaultPlacement,
        Map<String, PlacementStrategy> placements,
        BooleanSupplier active,
        IntSupplier outlineColor,
        IntSupplier labelColor,
        IntSupplier labelShadowColor,
        boolean sizeLabels,
        boolean outlineIdleBoxes,
        boolean snapRegistry,
        int snapThreshold,
        BiConsumer<String, Bounds> beforeCommit,
        BiConsumer<String, Bounds> afterCommit
) {

    PlacementStrategy placementFor(EditableModule module) {
        PlacementStrategy own = module.placementStrategy();
        if (own != null) {
            return own;
        }
        PlacementStrategy byId = placements.get(module.id());
        return byId != null ? byId : defaultPlacement;
    }
}
