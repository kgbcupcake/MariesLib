package dev.marie.framework.ui.component;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.ui.geometry.Anchor;
import dev.marie.framework.ui.geometry.Insets;
import dev.marie.framework.ui.geometry.Size;

/**
 * Sizing/placement metadata every {@link MarieComponent} exposes. Layouts consume this — they do not
 * define their own sizing rules, so this record is the single source of truth for how big a
 * component wants to be and how it behaves when there is more or less space than that.
 */
@ApiStatus.Experimental
public record Constraint(
        Size preferredSize,
        Size minSize,
        Size maxSize,
        boolean expandHorizontal,
        boolean expandVertical,
        boolean shrinkHorizontal,
        boolean shrinkVertical,
        Anchor anchor,
        Insets padding,
        Insets margin
) {

    /** A component that never resizes away from {@code width}x{@code height}. */
    public static Constraint fixed(int width, int height) {
        Size size = new Size(width, height);
        return new Constraint(size, size, size, false, false, false, false, Anchor.TOP_LEFT, Insets.NONE, Insets.NONE);
    }

    /** A component that prefers {@code width}x{@code height} but may shrink or grow between 0 and unbounded. */
    public static Constraint preferred(int width, int height) {
        return new Constraint(
                new Size(width, height), Size.ZERO, Size.UNBOUNDED,
                false, false, true, true,
                Anchor.TOP_LEFT, Insets.NONE, Insets.NONE
        );
    }

    public Constraint withAnchor(Anchor newAnchor) {
        return new Constraint(preferredSize, minSize, maxSize, expandHorizontal, expandVertical,
                shrinkHorizontal, shrinkVertical, newAnchor, padding, margin);
    }

    public Constraint withMargin(Insets newMargin) {
        return new Constraint(preferredSize, minSize, maxSize, expandHorizontal, expandVertical,
                shrinkHorizontal, shrinkVertical, anchor, padding, newMargin);
    }
}
