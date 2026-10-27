package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.component.Constraint;
import dev.marie.framework.ui.component.SelfPositioningModule;
import dev.marie.framework.ui.edithost.AdaptedEditableModule;
import dev.marie.framework.ui.geometry.Bounds;
import dev.marie.framework.ui.geometry.Size;

import java.util.List;
import java.util.function.Supplier;

/**
 * A module a {@link ModuleEditHost} can edit. Its {@link #id()} is both its persistence key and its
 * {@link MarieModuleSettings} panel id, so its move toggles, offsets and outlines all line up.
 *
 * <p>Only {@link #naturalSize()} is required on top of {@link SelfPositioningModule}; everything else
 * has a default. Wrap an existing module instead of implementing this with {@link #of}.
 */
@ApiStatus.Experimental
public interface EditableModule extends SelfPositioningModule {

    /** The module's untouched size in screen pixels at its current scale — what resize limits are measured from. */
    Size naturalSize();

    /** Which gestures the host allows on this module. */
    default EditCapabilities editCapabilities() {
        return EditCapabilities.full();
    }

    default ResizeLimits resizeLimits() {
        return ResizeLimits.DEFAULT;
    }

    /** The clamp a resize uses; re-read every frame so it follows a changing natural size. */
    default Constraint resizeConstraint() {
        return resizeLimits().constraintFor(naturalSize());
    }

    /** This module's own placement, or null to use the host's (per-id, then default). Handy for modules built from a registry at runtime. */
    default PlacementStrategy placementStrategy() {
        return null;
    }

    /** False to skip this module this frame (no handles, no gestures), e.g. while it's hidden or has nothing to show. */
    default boolean isEditable() {
        return true;
    }

    /** Modules snap their edges to siblings in the same group (and to host snap targets for it). */
    default String snapGroup() {
        return "";
    }

    /** Modules match sizes only with siblings in the same size group; defaults to {@link #snapGroup()}. */
    default String sizeSnapGroup() {
        return snapGroup();
    }

    /** More dashed outlines to draw in edit mode, e.g. sub-parts the module tracks itself; {@code mode} is the active move mode or null. */
    default List<Bounds> extraOutlineParts(MoveDrag.Mode mode) {
        return List.of();
    }

    /** Clamps a content offset dragged by {@code mode}; by default it may move at most the box's own width either way. */
    default int clampMoveOffsetX(MoveDrag.Mode mode, int offsetX, Bounds box) {
        return Math.max(-box.width(), Math.min(box.width(), offsetX));
    }

    default int clampMoveOffsetY(MoveDrag.Mode mode, int offsetY, Bounds box) {
        return Math.max(-box.height(), Math.min(box.height(), offsetY));
    }

    /** Makes an existing module editable without changing its class. */
    static EditableModule of(SelfPositioningModule module, Supplier<Size> naturalSize, EditCapabilities capabilities) {
        return new AdaptedEditableModule(module, naturalSize, capabilities);
    }
}
