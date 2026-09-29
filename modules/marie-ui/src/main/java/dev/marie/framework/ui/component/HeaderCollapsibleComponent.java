package dev.marie.framework.ui.component;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.ui.geometry.Bounds;

/**
 * Contract for {@link MarieComponent} implementations with a fixed header and collapsible body
 * content.
 *
 * <p>The header always remains visible while body content is hidden when the resolved {@link Bounds}
 * cannot fit it. Fit calculations use the component's current content scale.
 */
@ApiStatus.Experimental
public interface HeaderCollapsibleComponent {

    /** Fixed header height in local (pre-scale) units. The header is never collapsed. */
    int headerLocalHeight();

    /**
     * Calculates how many repeating body units fit below the header.
     *
     * @param bounds current component bounds
     * @param contentScale screen pixels per local unit
     * @param availableUnits maximum available body units
     * @param unitLocalHeight height of one body unit in local units
     * @return number of body units that fit
     */
    default int bodyUnitsFit(Bounds bounds, double contentScale, int availableUnits, int unitLocalHeight) {
        if (contentScale <= 0 || unitLocalHeight <= 0) {
            return 0;
        }

        double headerScreenH = headerLocalHeight() * contentScale;
        double unitScreenH = unitLocalHeight * contentScale;

        int fit = (int) Math.floor((bounds.height() - headerScreenH) / unitScreenH);
        return Math.max(0, Math.min(availableUnits, fit));
    }

    /**
     * Checks whether a fixed body block fits below the header.
     *
     * @param bounds current component bounds
     * @param contentScale screen pixels per local unit
     * @param bodyLocalHeight body height in local units
     * @return {@code true} if the body fits below the header
     */
    default boolean bodyBlockFits(Bounds bounds, double contentScale, int bodyLocalHeight) {
        if (contentScale <= 0) {
            return false;
        }

        double headerScreenH = headerLocalHeight() * contentScale;
        double bodyScreenH = bodyLocalHeight * contentScale;

        return bounds.height() >= headerScreenH + bodyScreenH;
    }
}