package dev.marie.framework.ui.edithost;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.api.EditGesture;
import dev.marie.framework.ui.api.EditableModule;
import dev.marie.framework.ui.api.MarieModuleSettings;
import dev.marie.framework.ui.api.MoveDrag;
import dev.marie.framework.ui.api.PlacementStrategy;
import dev.marie.framework.ui.component.ComponentState;
import dev.marie.framework.ui.drag.DraggableResizable;
import dev.marie.framework.ui.drag.SnapRegistry;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The state behind one {@code ModuleEditHost}: the modules tracked this frame and where they were
 * drawn, input routing (move-mode drags first, then box gestures, topmost module first), commits
 * through each module's placement strategy, and the overlay.
 */
@ApiStatus.Internal
public final class EditHostCore {

    private record Entry(EditableModule module, Bounds bounds) {}

    private record SnapTarget(String group, Bounds bounds) {}

    private final EditHostConfig config;
    private final EditOverlayPainter painter;
    private final BoxGestures gestures = new BoxGestures(this::commit);
    private final ContentMoveSession moves = new ContentMoveSession();
    private final List<Entry> frame = new ArrayList<>();
    private final List<SnapTarget> snapTargets = new ArrayList<>();
    private final Map<String, Bounds> lastBounds = new HashMap<>();

    public EditHostCore(EditHostConfig config) {
        this.config = config;
        this.painter = new EditOverlayPainter(config);
    }

    public PersistenceProvider store() {
        return config.store();
    }

    public boolean isActive() {
        return config.active().getAsBoolean();
    }

    public void beginFrame() {
        frame.clear();
        snapTargets.clear();
    }

    public Bounds track(EditableModule module, Bounds drawnAt) {
        DraggableResizable drag = gestures.dragFor(module);
        drag.setConstraint(module.resizeConstraint());
        drag.setParentBounds(config.placementFor(module).clampRegion());
        drag.setSnapThresholdPx(config.snapThreshold());
        if (config.snapRegistry()) {
            drag.setSnapRegistryId(module.id());
            if (!lastBounds.containsKey(module.id())) {
                SnapRegistry.register(module.id(), () -> lastBounds.getOrDefault(module.id(), new Bounds(0, 0, 0, 0)));
            }
        }
        Bounds preview = gestures.preview(module.id());
        Bounds bounds = preview != null ? preview : drawnAt;
        frame.add(new Entry(module, bounds));
        lastBounds.put(module.id(), bounds);
        return bounds;
    }

    public void addSnapTarget(String group, Bounds bounds) {
        snapTargets.add(new SnapTarget(group == null ? "" : group, bounds));
    }

    public Bounds preview(String id) {
        return gestures.preview(id);
    }

    public Map<String, Bounds> previews() {
        return gestures.previews();
    }

    public EditGesture gesture() {
        if (moves.isActive()) {
            return new EditGesture(moves.moduleId(), EditGesture.Kind.CONTENT_MOVE, moves.box(), moves.mode(), false, false, false, false);
        }
        return gestures.gesture();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isActive() || button != 0) {
            return false;
        }
        int mx = (int) mouseX;
        int my = (int) mouseY;
        for (int i = frame.size() - 1; i >= 0; i--) {
            Entry e = frame.get(i);
            MoveDrag.Mode mode = e.module().isEditable() ? moveMode(e.module()) : null;
            if (mode != null && e.bounds().contains(mx, my)) {
                moves.start(store(), e.module(), e.bounds(), mode, mouseX, mouseY);
                return true;
            }
        }
        for (int i = frame.size() - 1; i >= 0; i--) {
            Entry e = frame.get(i);
            if (!e.module().isEditable() || moveMode(e.module()) != null) {
                continue;
            }
            applySnapTargets(e.module());
            if (gestures.tryStart(e.module(), e.bounds(), mx, my, store())) {
                return true;
            }
            // A box that can't be grabbed here still sits on top of whatever is under it.
            if (e.bounds().contains(mx, my)) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (moves.isActive()) {
            moves.apply(store(), mouseX, mouseY);
            return true;
        }
        if (gestures.isActive()) {
            applySnapTargets(gestures.activeModule());
            gestures.drag((int) mouseX, (int) mouseY, store());
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY) {
        if (moves.isActive()) {
            moves.finish(store());
            return true;
        }
        if (gestures.isActive()) {
            gestures.release((int) mouseX, (int) mouseY, store());
            return true;
        }
        return false;
    }

    public void renderOverlay(RenderContext context, int mouseX, int mouseY) {
        if (!isActive()) {
            return;
        }
        EditGesture gesture = gestures.gesture();
        for (Entry e : frame) {
            if (e.module().isEditable()) {
                painter.paint(context, e.module(), e.bounds(), moveMode(e.module()),
                        gestures.existingDrag(e.module().id()), gesture, mouseX, mouseY);
            }
        }
    }

    public void dispose() {
        for (String id : lastBounds.keySet()) {
            if (config.snapRegistry()) {
                SnapRegistry.unregister(id);
            }
        }
        lastBounds.clear();
    }

    /** The move mode switched on for {@code module}, if it's one the module supports. */
    private MoveDrag.Mode moveMode(EditableModule module) {
        MoveDrag.Mode mode = MarieModuleSettings.activeMoveMode(store(), module.id());
        return module.editCapabilities().supports(mode) ? mode : null;
    }

    private void commit(EditableModule module, Bounds committed) {
        String id = module.id();
        EditGesture gesture = gestures.gesture();
        config.beforeCommit().accept(id, committed);
        PlacementStrategy placement = config.placementFor(module);
        ComponentState existing = store().load(id).orElse(null);
        ComponentState state = placement.toState(committed, gesture.toCommit(), existing);
        if (state != null) {
            store().save(id, state);
        }
        config.afterCommit().accept(id, committed);
    }

    /** Sibling edges (same snap group) and host snap targets for position; same-size-group siblings for size. */
    private void applySnapTargets(EditableModule module) {
        if (config.snapRegistry()) {
            return;
        }
        String group = module.snapGroup();
        String sizeGroup = module.sizeSnapGroup();
        List<Integer> xs = new ArrayList<>();
        List<Integer> ys = new ArrayList<>();
        List<Integer> widths = new ArrayList<>();
        List<Integer> heights = new ArrayList<>();
        for (Entry e : frame) {
            if (e.module().id().equals(module.id())) {
                continue;
            }
            if (e.module().snapGroup().equals(group)) {
                addEdges(xs, ys, e.bounds());
            }
            if (e.module().sizeSnapGroup().equals(sizeGroup)) {
                widths.add(e.bounds().width());
                heights.add(e.bounds().height());
            }
        }
        for (SnapTarget target : snapTargets) {
            if (target.group().equals(group)) {
                addEdges(xs, ys, target.bounds());
            }
        }
        DraggableResizable drag = gestures.dragFor(module);
        drag.setSnapTargets(xs, ys);
        drag.setSizeSnapTargets(widths, heights);
    }

    private static void addEdges(List<Integer> xs, List<Integer> ys, Bounds b) {
        xs.add(b.x());
        xs.add(b.x() + b.width());
        ys.add(b.y());
        ys.add(b.y() + b.height());
    }
}
