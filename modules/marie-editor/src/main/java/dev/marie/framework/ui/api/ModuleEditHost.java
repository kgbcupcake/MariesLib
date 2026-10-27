package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.drag.DraggableResizable;
import dev.marie.framework.ui.edithost.EditHostConfig;
import dev.marie.framework.ui.edithost.EditHostCore;
import dev.marie.framework.ui.edithost.HostTabContent;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * Makes a set of {@link EditableModule}s editable: whole-box move, edge/corner resize with sibling and
 * size snapping, move-mode content drags ({@link MoveDrag}), handles, dashed move outlines and size
 * labels, committing each finished gesture through a {@link PlacementStrategy} into one store.
 *
 * <p>Each frame: {@link #beginFrame()}, then {@link #track} every module as you lay it out (draw it at
 * the bounds it returns, which follow a drag live), then {@link #renderOverlay}. Forward mouse input
 * to the {@code mouse...} methods. A container that sizes modules before drawing them can read
 * {@link #previewBounds}/{@link #livePreviews()} first.
 *
 * <pre>{@code
 * ModuleEditHost host = ModuleEditHost.builder(store, PanelRelativePlacement.of(() -> panel, () -> scale))
 *         .activeWhen(editMode::isActive)
 *         .build();
 * // render
 * host.beginFrame();
 * host.addSnapTarget("", panel);
 * for (EditableModule m : modules) m.render(context, host.track(m));
 * host.renderOverlay(context, mouseX, mouseY);
 * }</pre>
 */
@ApiStatus.Experimental
public final class ModuleEditHost {

    private final EditHostCore core;

    private ModuleEditHost(EditHostConfig config) {
        this.core = new EditHostCore(config);
    }

    /** A host saving into {@code store}, placing every module with {@code placement} unless overridden per module. */
    public static Builder builder(PersistenceProvider store, PlacementStrategy placement) {
        return new Builder(store, placement);
    }

    /** As {@link #builder(PersistenceProvider, PlacementStrategy)}, over a profile's store. */
    public static Builder builder(ModuleProfile profile, PlacementStrategy placement) {
        return new Builder(profile.store(), placement);
    }

    public PersistenceProvider store() {
        return core.store();
    }

    /** Whether edit mode is on (see {@link Builder#activeWhen}); while off the host ignores input and draws nothing. */
    public boolean isActive() {
        return core.isActive();
    }

    /** Starts a frame: forgets last frame's modules and snap targets. */
    public void beginFrame() {
        core.beginFrame();
    }

    /** Registers {@code module} for this frame at its {@link EditableModule#resolvedBounds()}; returns where to draw it. */
    public Bounds track(EditableModule module) {
        return core.track(module, module.resolvedBounds());
    }

    /** Registers {@code module} for this frame, drawn at {@code drawnAt} unless it's being dragged; returns where to draw it. */
    public Bounds track(EditableModule module, Bounds drawnAt) {
        return core.track(module, drawnAt);
    }

    /** Extra edges modules in snap group {@code group} snap to this frame, e.g. the panel they sit in. */
    public void addSnapTarget(String group, Bounds bounds) {
        core.addSnapTarget(group, bounds);
    }

    /** The live bounds of module {@code id} while it's being moved or resized, else null. */
    public Bounds previewBounds(String id) {
        return core.preview(id);
    }

    /** Every live preview by module id (read-only, at most one entry) — for a container that resolves sibling layout before drawing. */
    public Map<String, Bounds> livePreviews() {
        return core.previews();
    }

    /** The gesture in progress, or null when idle. */
    public EditGesture activeGesture() {
        return core.gesture();
    }

    public void renderOverlay(RenderContext context, int mouseX, int mouseY) {
        core.renderOverlay(context, mouseX, mouseY);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return core.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        return core.mouseDragged(mouseX, mouseY);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return core.mouseReleased(mouseX, mouseY);
    }

    /** Unregisters anything the host put in {@link dev.marie.framework.ui.drag.SnapRegistry}; call when the host goes away for good. */
    public void dispose() {
        core.dispose();
    }

    /**
     * This host as a self-contained component, e.g. a {@code HubEntry}'s content: each render begins a
     * frame, calls {@code renderer} to draw the modules, then draws the overlay; input goes to the host.
     */
    public MarieComponent asComponent(String id, EditHostRenderer renderer) {
        return new HostTabContent(id, this, renderer);
    }

    /** Builder for {@link ModuleEditHost}. */
    @ApiStatus.Experimental
    public static final class Builder {

        private final PersistenceProvider store;
        private final PlacementStrategy placement;
        private final Map<String, PlacementStrategy> placements = new HashMap<>();
        private BooleanSupplier active = () -> true;
        private IntSupplier outlineColor = () -> 0xFFFFFFFF;
        private IntSupplier labelColor = () -> 0xFFFFFFFF;
        private IntSupplier labelShadowColor = () -> 0xFF000000;
        private boolean sizeLabels = true;
        private boolean outlineIdleBoxes;
        private boolean snapRegistry;
        private int snapThreshold = DraggableResizable.DEFAULT_SNAP_THRESHOLD_PX;
        private BiConsumer<String, Bounds> beforeCommit = (id, b) -> {};
        private BiConsumer<String, Bounds> afterCommit = (id, b) -> {};

        private Builder(PersistenceProvider store, PlacementStrategy placement) {
            this.store = Objects.requireNonNull(store, "store");
            this.placement = Objects.requireNonNull(placement, "placement");
        }

        /** Places module {@code moduleId} with its own strategy instead of the default one. */
        public Builder placement(String moduleId, PlacementStrategy strategy) {
            placements.put(moduleId, strategy);
            return this;
        }

        /** Edit mode on/off, re-read every call (e.g. {@code controller::isActive}). Always on by default. */
        public Builder activeWhen(BooleanSupplier active) {
            this.active = active;
            return this;
        }

        public Builder outlineColor(IntSupplier argb) {
            this.outlineColor = argb;
            return this;
        }

        public Builder sizeLabelColors(IntSupplier text, IntSupplier shadow) {
            this.labelColor = text;
            this.labelShadowColor = shadow;
            return this;
        }

        public Builder sizeLabels(boolean show) {
            this.sizeLabels = show;
            return this;
        }

        /** Also outline every box (dashed) while it has no move mode on, not just its handles. */
        public Builder outlineBoxes(boolean outline) {
            this.outlineIdleBoxes = outline;
            return this;
        }

        /** Snap against every box in the shared {@link dev.marie.framework.ui.drag.SnapRegistry} (across mods) instead of only this host's siblings. */
        public Builder useSnapRegistry(boolean use) {
            this.snapRegistry = use;
            return this;
        }

        public Builder snapThreshold(int pixels) {
            this.snapThreshold = pixels;
            return this;
        }

        /** Runs before a box gesture is saved, with the module id and committed bounds (e.g. to grow a parent panel first). */
        public Builder beforeCommit(BiConsumer<String, Bounds> listener) {
            this.beforeCommit = listener;
            return this;
        }

        /** Runs after a box gesture is saved. */
        public Builder afterCommit(BiConsumer<String, Bounds> listener) {
            this.afterCommit = listener;
            return this;
        }

        public ModuleEditHost build() {
            return new ModuleEditHost(new EditHostConfig(store, placement, Map.copyOf(placements), active, outlineColor,
                    labelColor, labelShadowColor, sizeLabels, outlineIdleBoxes, snapRegistry, snapThreshold, beforeCommit, afterCommit));
        }
    }
}
