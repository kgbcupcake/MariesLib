package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.component.ComponentState;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.Map;

/**
 * Generalized version of the panel-relative sub-box resolution pattern first written for an earlier
 * consumer mod's HUD screen (its own {@code resolveRelativeToPanel} helper): a component's live drag/resize
 * preview if one is active this frame, else a persisted local-unit offset/size if the user has
 * manually moved/resized it, otherwise a natural stacked default position — always clamped to stay
 * inside the owning panel and between two caller-supplied horizontal bounds.
 *
 * <p>The two horizontal clamp bounds ({@code leftClampBound}/{@code rightClampBound}) are what let
 * one implementation serve both a "left column" (clamped between the panel's own left edge and a
 * column divider) and a "right column" (clamped between that same divider and the panel's right
 * edge) — pass {@code panelX}/{@code dividerX} for the former, {@code dividerX}/{@code panelX +
 * panelWidth} for the latter. A host with no column split at all can simply pass {@code panelX} and
 * {@code panelX + panelWidth}.
 */
@ApiStatus.Experimental
public final class ComponentPersistence {

    private ComponentPersistence() {}

    /**
     * Resolves {@code componentId}'s screen {@link Bounds}: {@code liveOverrides.get(componentId)} if
     * present (a live drag/resize preview registered for this frame), else the persisted {@link
     * ComponentState} converted from local (pre-scale) units back to screen pixels, else the natural
     * default position ({@code contentX}, {@code panelY + startLocalY * scale}) at natural size.
     *
     * @param contentX      the screen X a local X of 0 maps to (typically {@code panelX + leftMargin})
     * @param panelY        the screen Y a local Y of 0 maps to
     * @param scale         screen pixels per local unit
     * @param startLocalY   default (unpersisted) local-unit start Y for this component
     * @param localWidth    natural local-unit width
     * @param localHeight   natural local-unit height
     * @param panelX        the owning panel's screen X (for the panel-bounds clamp)
     * @param panelWidth    the owning panel's screen width
     * @param panelHeight   the owning panel's screen height
     * @param leftClampBound  screen X the resolved bounds' left edge may not pass
     * @param rightClampBound screen X the resolved bounds' right edge may not pass
     */
    public static Bounds resolveRelative(
            PersistenceProvider store,
            Map<String, Bounds> liveOverrides,
            String componentId,
            int contentX,
            int panelY,
            double scale,
            int startLocalY,
            int localWidth,
            int localHeight,
            int panelX,
            int panelWidth,
            int panelHeight,
            int leftClampBound,
            int rightClampBound
    ) {
        Bounds liveOverride = liveOverrides.get(componentId);
        if (liveOverride != null) {
            return clamp(liveOverride, panelX, panelY, panelWidth, panelHeight, leftClampBound, rightClampBound);
        }
        int naturalWidth = Math.max(1, (int) Math.round(localWidth * scale));
        int naturalHeight = Math.max(1, (int) Math.round(localHeight * scale));
        Bounds resolved = store.load(componentId)
                .map(state -> new Bounds(
                        contentX + (int) Math.round(state.x() * scale),
                        panelY + (int) Math.round(state.y() * scale),
                        state.widthManual() ? (int) Math.round(state.width() * scale) : naturalWidth,
                        state.heightManual() ? (int) Math.round(state.height() * scale) : naturalHeight))
                .orElseGet(() -> new Bounds(
                        contentX,
                        panelY + (int) Math.round(startLocalY * scale),
                        naturalWidth,
                        naturalHeight));
        return clamp(resolved, panelX, panelY, panelWidth, panelHeight, leftClampBound, rightClampBound);
    }

    /** Confines {@code bounds} to the panel rectangle, and its horizontal span to {@code [leftBound, rightBound]}. */
    private static Bounds clamp(Bounds bounds, int panelX, int panelY, int panelW, int panelH, int leftBound, int rightBound) {
        int w = Math.min(bounds.width(), panelW);
        int h = Math.min(bounds.height(), panelH);
        int x = Math.max(panelX, Math.min(bounds.x(), panelX + panelW - w));
        int y = Math.max(panelY, Math.min(bounds.y(), panelY + panelH - h));

        w = Math.min(w, Math.max(1, rightBound - leftBound));
        x = Math.max(leftBound, Math.min(x, rightBound - w));

        return new Bounds(x, y, w, h);
    }
}
