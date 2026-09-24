package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;

@ApiStatus.Experimental
/**
 * Drag state for a module's move modes (text, icons, bars, or all three at once), so a host's mouse handlers don't each carry the same
 * grab-offset bookkeeping. One instance per module; the host still owns the offsets themselves and
 * their clamping (the bar offset through {@link MarieModuleSettings#setBarOffset}, its text-and-icons offset however
 * it likes):
 *
 * <pre>{@code
 * // press:   moveDrag.start(mode, mouseX, mouseY, currentOffsetX, currentOffsetY);
 * // drag:    if (moveDrag.isActive()) { apply(moveDrag.mode(), clamp(moveDrag.offsetX(mouseX)), clamp(moveDrag.offsetY(mouseY))); }
 * // release: if (moveDrag.isActive()) { Mode mode = moveDrag.mode(); moveDrag.stop(); commit(mode); }
 * }</pre>
 */
public final class MoveDrag {

    /** What a move drag is repositioning. */
    public enum Mode {
        TEXT, ICONS, BARS,
        /** A separate title's offset (see {@link MarieModuleSettings#headerOffsetX}); only for modules that ask for a header toggle. */
        HEADER,
        /**
         * The icon itself, independent of {@link #ICONS}' icon-box offset (see {@link
         * MarieModuleSettings#iconInnerOffsetX}) — for a module whose icon sits in its own small box
         * (e.g. {@code BarRowComponent}) and wants the icon draggable inside that box without moving
         * the box. Not part of {@link #ALL}, since that would defeat the point of keeping it separate.
         */
        ICON_INNER,
        /** Text, icons and bars together: one drag shifts all three offsets by the same amount (see {@link #startAll}). */
        ALL
    }

    private boolean active;
    private Mode mode = Mode.TEXT;
    private int grabX;
    private int grabY;
    /** Each single mode's offset at the press, indexed TEXT, ICONS, BARS — what {@link Mode#ALL} adds its delta to. */
    private final int[] baseX = new int[4];
    private final int[] baseY = new int[4];

    /** Begins a {@link Mode#ALL} drag from the three offsets' current values; {@link #offsetX}/{@link #offsetY} then return the pointer's movement since the press, to add to {@link #baseX}/{@link #baseY} of each mode. */
    public void startAll(double mouseX, double mouseY, int textX, int textY, int iconX, int iconY, int barX, int barY) {
        this.active = true;
        this.mode = Mode.ALL;
        this.grabX = (int) mouseX;
        this.grabY = (int) mouseY;
        baseX[0] = textX;
        baseY[0] = textY;
        baseX[1] = iconX;
        baseY[1] = iconY;
        baseX[2] = barX;
        baseY[2] = barY;
    }

    /** As {@link #startAll(double, double, int, int, int, int, int, int)}, also carrying the header offset. */
    public void startAll(double mouseX, double mouseY, int textX, int textY, int iconX, int iconY, int barX, int barY, int headerX, int headerY) {
        startAll(mouseX, mouseY, textX, textY, iconX, iconY, barX, barY);
        baseX[3] = headerX;
        baseY[3] = headerY;
    }

    /** {@code single}'s offset when the {@link Mode#ALL} drag started ({@code single} must be TEXT, ICONS or BARS). */
    public int baseX(Mode single) {
        return baseX[single.ordinal()];
    }

    public int baseY(Mode single) {
        return baseY[single.ordinal()];
    }

    /** Begins dragging the offset of {@code mode} from its current value. */
    public void start(Mode mode, double mouseX, double mouseY, int currentOffsetX, int currentOffsetY) {
        this.active = true;
        this.mode = mode;
        this.grabX = (int) mouseX - currentOffsetX;
        this.grabY = (int) mouseY - currentOffsetY;
    }

    public boolean isActive() {
        return active;
    }

    /** What the active drag is repositioning. */
    public Mode mode() {
        return mode;
    }

    /** The unclamped offset the pointer implies for the active drag (for {@link Mode#ALL}, the movement since the press). */
    public int offsetX(double mouseX) {
        return (int) mouseX - grabX;
    }

    public int offsetY(double mouseY) {
        return (int) mouseY - grabY;
    }

    public void stop() {
        active = false;
    }
}
