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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The state behind one {@code ModuleEditHost}: the modules tracked this frame and where they were
 * drawn, input routing (one topmost-first pass over content drags and box gestures), commits
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

    /** How long a host can go without drawing before its boxes stop counting as snap targets (a hidden tab, a closed screen). */
    private static final long SNAP_STALE_NANOS = TimeUnit.MILLISECONDS.toNanos(250);
    private static final AtomicInteger HOST_SEQ = new AtomicInteger();

    // Per-host prefix for SnapRegistry ids, so two hosts tracking the same module id (e.g. two scoped
    // profiles of one screen) never replace, or on dispose remove, each other's entries.
    private final String snapIdPrefix = "marieslib.edithost." + HOST_SEQ.incrementAndGet() + "/";
    private long lastFrameNanos;

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
        lastFrameNanos = System.nanoTime();
        // A module tracked before but not last frame has stopped rendering: stop offering its old spot as a snap target.
        Set<String> tracked = new HashSet<>();
        for (Entry e : frame) {
            tracked.add(e.module().id());
        }
        lastBounds.keySet().removeIf(id -> {
            if (tracked.contains(id)) {
                return false;
            }
            if (config.snapRegistry()) {
                SnapRegistry.unregister(snapId(id));
            }
            return true;
        });
        frame.clear();
        snapTargets.clear();
    }

    public Bounds track(EditableModule module, Bounds drawnAt) {
        DraggableResizable drag = gestures.dragFor(module);
        drag.setConstraint(module.resizeConstraint());
        drag.setParentBounds(config.placementFor(module).clampRegion());
        drag.setSnapThresholdPx(config.snapThreshold());
        if (config.snapRegistry()) {
            String id = module.id();
            drag.setSnapRegistryId(snapId(id));
            if (!lastBounds.containsKey(id)) {
                SnapRegistry.register(snapId(id), new HostSnapSource(this, id));
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
        // One topmost-first pass, so a box always shields whatever sits under it, whichever kind of drag either one does.
        for (int i = frame.size() - 1; i >= 0; i--) {
            Entry e = frame.get(i);
            if (!e.module().isEditable()) {
                continue;
            }
            MoveDrag.Mode mode = moveMode(e.module());
            if (mode != null) {
                if (e.bounds().contains(mx, my)) {
                    moves.start(store(), e.module(), e.bounds(), mode, mouseX, mouseY);
                    return true;
                }
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
                SnapRegistry.unregister(snapId(id));
            }
        }
        lastBounds.clear();
    }

    private String snapId(String moduleId) {
        return snapIdPrefix + moduleId;
    }

    /** Whether this host drew a frame recently; a host that stopped (hidden hub tab) offers no snap targets. */
    private boolean isDrawing() {
        return System.nanoTime() - lastFrameNanos < SNAP_STALE_NANOS;
    }

    /**
     * A module's entry in the static {@link SnapRegistry}. Holds its host weakly, so a host whose screen
     * closed without {@link #dispose()} can still be collected, after which the registry drops the entry.
     */
    private static final class HostSnapSource implements SnapRegistry.ExpiringSource {
        private final WeakReference<EditHostCore> host;
        private final String moduleId;

        HostSnapSource(EditHostCore host, String moduleId) {
            this.host = new WeakReference<>(host);
            this.moduleId = moduleId;
        }

        @Override
        public Bounds get() {
            EditHostCore core = host.get();
            return core != null && core.isDrawing() ? core.lastBounds.get(moduleId) : null;
        }

        @Override
        public boolean isExpired() {
            return host.get() == null;
        }
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
        ComponentState state = placement.toState(committed, gesture.toCommit(gestures.keepsContentInPlace()), existing);
        if (state != null) {
            store().save(id, state);
        }
        config.afterCommit().accept(id, committed);
    }

    /**
     * Sibling edges (same snap group) and host snap targets for position; same-size-group siblings for
     * size. With the shared {@link SnapRegistry} on, it supplies the sibling edges itself, so only the
     * host targets (kept alongside it) and the size targets are set here.
     */
    private void applySnapTargets(EditableModule module) {
        boolean shared = config.snapRegistry();
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
            if (!shared && e.module().snapGroup().equals(group)) {
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
        if (shared) {
            drag.setExtraSnapTargets(xs, ys);
        } else {
            drag.setSnapTargets(xs, ys);
        }
        drag.setSizeSnapTargets(widths, heights);
    }

    private static void addEdges(List<Integer> xs, List<Integer> ys, Bounds b) {
        xs.add(b.x());
        xs.add(b.x() + b.width());
        ys.add(b.y());
        ys.add(b.y() + b.height());
    }
}
