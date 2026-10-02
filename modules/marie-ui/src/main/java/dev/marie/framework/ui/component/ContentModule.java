package dev.marie.framework.ui.component;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.drag.DraggableResizable;
import dev.marie.framework.ui.geometry.Bounds;

/**
 * The one nested, independently editable content region a {@link MarieComponent} may expose
 * inside its own main box — e.g. a HUD panel's icon/bar/number row-group, moved and resized as a
 * single unit within the panel instead of each piece needing its own coordinate. A component
 * holds at most one {@code ContentModule}; this is a fixed one-level relationship (component
 * &rarr; one module), not a general child-component tree — nest further containers inside the
 * module's own {@link #bounds()} if a component needs more internal structure, but that's the
 * component's content to lay out, not this class's concern.
 *
 * <p>Purely a layout/editing primitive, the same division of responsibility as {@link
 * DraggableResizable} itself: it owns the module's {@link Bounds} and its own drag/resize gesture
 * (composing a {@link DraggableResizable} clamped to the owner's current bounds via {@link
 * #setParentBounds}), plus persistence under the owner's {@code #module}-suffixed {@link
 * ComponentState} (mirroring {@code ScaleConfigPanel}'s {@code #window}-suffixed sub-state). It
 * never renders anything itself — the owning component's {@link MarieComponent#render} decides
 * what content to draw inside {@link #bounds()}, and whether/how to render an edit-mode
 * affordance for it (e.g. {@code RenderContext.drawGlow} with {@code ThemeKey.SUBBOX_GLOW}, and/or
 * {@code RenderContext.drawResizeHandle} using {@link #isHandleHovered}/{@link #isHandleActive}).
 *
 * <p>Position is persisted as an offset relative to the owner's own bounds, not an absolute
 * screen position — so this module rides along automatically when the owner itself is dragged or
 * resized, the same behavior an earlier consumer mod's HUD screen hand-rolled for its row
 * sub-box before this class existed (persisting {@code bounds.x() - panelBounds.x()} etc. itself,
 * outside its {@code DraggableResizable}). Size is stored as-is, unaffected by the owner's size.
 *
 * <p>The owner must call {@link #setParentBounds} every frame with its own current bounds before
 * forwarding any mouse event or computing {@link #bounds}/{@link #liveBounds} — same reasoning as
 * {@link DraggableResizable#setParentBounds}: the owner's own box can move/resize across a
 * session, and both the clamp region and this module's own on-screen position derive from it. A
 * typical owner wires this module's {@link #mouseClicked}/{@link #mouseDragged}/{@link
 * #mouseReleased} into its own {@code MarieComponent} overrides after its main box's own gesture
 * tracker (if any) has had first refusal, exactly as {@code CommandCenterScreen} chains multiple
 * gesture trackers today.
 */
@ApiStatus.Experimental
public final class ContentModule {

    private final PersistenceProvider persistence;
    private final String moduleKey;
    private final DraggableResizable drag;

    private Bounds ownerBounds;
    private int offsetX;
    private int offsetY;
    private int width;
    private int height;

    /**
     * @param owner the component this module belongs to; also the {@code target} forwarded to
     *              {@code onCommit}-style callers via the internal {@link DraggableResizable}
     * @param constraint min/max size clamp for this module's own resize gestures
     * @param persistence where this module's position/size are loaded/saved, keyed off {@code owner.id()}
     * @param ownerBounds the owner's current bounds at construction time — used to resolve the
     *                     initial on-screen position (from either persisted or {@code
     *                     defaultBounds} offset) and as the initial drag/resize clamp region;
     *                     superseded every frame by {@link #setParentBounds}
     * @param defaultBounds absolute bounds to use the first time this module is ever created,
     *                       when nothing has been persisted yet — converted to an offset relative
     *                       to {@code ownerBounds} for storage
     */
    public ContentModule(MarieComponent owner, Constraint constraint, PersistenceProvider persistence,
                          Bounds ownerBounds, Bounds defaultBounds) {
        this.persistence = persistence;
        this.moduleKey = owner.id() + "#module";
        this.ownerBounds = ownerBounds;

        ComponentState state = persistence.load(moduleKey).orElse(null);
        if (state != null) {
            this.offsetX = state.x();
            this.offsetY = state.y();
            this.width = state.width();
            this.height = state.height();
        } else {
            this.offsetX = defaultBounds.x() - ownerBounds.x();
            this.offsetY = defaultBounds.y() - ownerBounds.y();
            this.width = defaultBounds.width();
            this.height = defaultBounds.height();
        }

        this.drag = new DraggableResizable(owner, constraint, (target, committed) -> {
            this.offsetX = committed.x() - this.ownerBounds.x();
            this.offsetY = committed.y() - this.ownerBounds.y();
            this.width = committed.width();
            this.height = committed.height();
            persistOffset();
        }, ownerBounds);
    }

    /** This module's current committed bounds, resolved from its persisted owner-relative offset against the owner's last-set bounds. */
    public Bounds bounds() {
        return new Bounds(ownerBounds.x() + offsetX, ownerBounds.y() + offsetY, width, height);
    }

    /** Replaces the owner bounds this module positions/clamps against — call every frame with the owner's current bounds. */
    public void setParentBounds(Bounds ownerBounds) {
        this.ownerBounds = ownerBounds;
        drag.setParentBounds(ownerBounds);
    }

    /** Replaces this module's min/max size clamp — call every frame if it depends on something that can change mid-session. */
    public void setConstraint(Constraint constraint) {
        drag.setConstraint(constraint);
    }

    /**
     * Overrides this module's size directly, bypassing whatever was persisted or last committed —
     * for an owner whose module has no user-facing resize handles (a move-only module, {@link
     * Constraint#fixed} passed to the constructor/{@link #setConstraint}) and instead computes the
     * module's size itself every frame from live content/layout state. Without this, a module's
     * size only ever changes via a committed resize gesture, so a size computed from something that
     * can shrink out from under it — e.g. the owner's own bounds being manually resized smaller
     * than this module's last-known size — would otherwise leave a stale, oversized module sticking
     * out past the owner's current bounds. Not needed for a module the user is meant to resize
     * themselves; that case should rely on the gesture/persistence path instead.
     */
    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public boolean isDragging() {
        return drag.isDragging();
    }

    public boolean isResizing() {
        return drag.isResizing();
    }

    /** Whether this module's resize handle should render in its "active" (currently being dragged) state. */
    public boolean isHandleActive() {
        return drag.isHandleActive();
    }

    /** Whether (mx, my) is within this module's own resize-handle hit region, for hover rendering. */
    public boolean isHandleHovered(int mx, int my) {
        return drag.isHandleHovered(mx, my, bounds());
    }

    /** Call from the owner's mouseClicked. Starts a drag/resize gesture if (mx, my) hits this module; returns true if one started. */
    public boolean mouseClicked(int mx, int my) {
        return drag.mouseClicked(mx, my, bounds());
    }

    /** Call from the owner's mouseDragged with absolute mouse coordinates. Returns the live preview {@link Bounds}, or {@code null} if no gesture is active. */
    public Bounds mouseDragged(int mx, int my) {
        return drag.mouseDragged(mx, my);
    }

    /** Call from the owner's mouseReleased. Ends any active gesture and persists the result. */
    public void mouseReleased(int mx, int my) {
        drag.mouseReleased(mx, my);
    }

    /** The live preview bounds while a gesture is active, else the committed {@link #bounds()} — call every render pass with the current mouse position. */
    public Bounds liveBounds(int mx, int my) {
        if (drag.isDragging() || drag.isResizing()) {
            Bounds preview = drag.mouseDragged(mx, my);
            if (preview != null) {
                return preview;
            }
        }
        return bounds();
    }

    /**
     * Persists this module's current owner-relative offset/size under its {@code #module}-suffixed
     * key, preserving whatever {@code contentScale}/{@code paddingScale} was already stored (this
     * class doesn't use either field itself) rather than resetting them to their defaults on every
     * commit.
     */
    private void persistOffset() {
        ComponentState base = persistence.load(moduleKey).orElse(null);
        double contentScale = base != null ? base.contentScale() : ComponentState.DEFAULT_CONTENT_SCALE;
        double paddingScale = base != null ? base.paddingScale() : ComponentState.DEFAULT_PADDING_SCALE;
        persistence.save(moduleKey, new ComponentState(offsetX, offsetY, width, height,
                false, false, false, 0, contentScale, paddingScale));
    }
}
