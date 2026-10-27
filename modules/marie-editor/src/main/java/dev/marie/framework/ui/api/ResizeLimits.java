package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.component.Constraint;
import dev.marie.framework.ui.geometry.Anchor;
import dev.marie.framework.ui.geometry.Insets;
import dev.marie.framework.ui.geometry.Size;

/**
 * How far an {@link EditableModule} may be resized, as factors of its natural size: a resize stops at
 * {@code natural * minFactor} (but never below {@code minPixels}) and at {@code natural * max...Factor}.
 */
@ApiStatus.Experimental
public record ResizeLimits(double minFactor, double maxWidthFactor, double maxHeightFactor, int minPixels) {

    /** 60% of natural size up to 50x as wide and 4x as tall. */
    public static final ResizeLimits DEFAULT = new ResizeLimits(0.6d, 50.0d, 4.0d, 1);

    /** The drag/resize clamp for a module whose natural size is {@code natural}. */
    public Constraint constraintFor(Size natural) {
        int minW = Math.max(minPixels, (int) Math.round(natural.width() * minFactor));
        int minH = Math.max(minPixels, (int) Math.round(natural.height() * minFactor));
        int maxW = Math.max(minW, (int) Math.round(natural.width() * maxWidthFactor));
        int maxH = Math.max(minH, (int) Math.round(natural.height() * maxHeightFactor));
        return new Constraint(natural, new Size(minW, minH), new Size(maxW, maxH),
                false, false, true, true, Anchor.TOP_LEFT, Insets.NONE, Insets.NONE);
    }
}
