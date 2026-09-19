package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.geometry.Anchor;
import dev.marie.framework.ui.modulesettings.MoveFlags;
import dev.marie.framework.ui.scaleconfig.ScaleConfigEntry;
import dev.marie.framework.ui.scaleconfig.ScaleConfigPanel;

import java.util.List;

/**
 * Public facade for the generic scale-config dashboard overlay: a corner-anchored panel of
 * sliders letting the player fine-tune the persisted {@code contentScale}/{@code paddingScale} of
 * a set of {@code ContentScaleController}-managed components, as an alternative to in-world
 * double-click+scroll.
 *
 * <p>{@link #create} builds the panel; MarieLib does not own a screen for it. The returned {@link
 * ScaleConfigPanel} is a plain object, not a {@code Screen} — the caller's own host screen owns
 * the instance, decides when it's visible, and must call {@link ScaleConfigPanel#render}/{@link
 * ScaleConfigPanel#mouseClicked}/{@link ScaleConfigPanel#mouseDragged}/{@link
 * ScaleConfigPanel#mouseReleased}/{@link ScaleConfigPanel#mouseScrolled} itself every frame while
 * it should be interactive (typically forwarding from the host screen's own {@code render}/{@code
 * mouseClicked}/{@code mouseDragged}/{@code mouseReleased}/{@code mouseScrolled}) — entries render
 * collapsed as tabs by default, and {@code mouseDragged}/{@code mouseReleased} drive repositioning
 * and resizing the single open window.
 *
 * <pre>{@code
 * // in the host screen's field initializers:
 * private final ScaleConfigPanel scaleConfigPanel = MarieScaleConfig.create(
 *         List.of(new ScaleConfigEntry(MyHudPanel.ID, Component.translatable("mymod.hud.label"))),
 *         MyPersistenceProvider.get(),
 *         Anchor.TOP_RIGHT);
 *
 * // in the host screen's render(), only while the panel should be visible:
 * scaleConfigPanel.render(context, screenBounds);
 *
 * // forwarded from the host screen's own input handlers:
 * if (scaleConfigPanel.mouseClicked(mouseX, mouseY, button)) {
 *     return true;
 * }
 * if (scaleConfigPanel.mouseDragged(mouseX, mouseY, button)) {
 *     return true;
 * }
 * scaleConfigPanel.mouseReleased(mouseX, mouseY, button);
 * }</pre>
 */
@ApiStatus.Experimental
public final class MarieScaleConfig {

    private MarieScaleConfig() {}

    /**
     * Creates a new scale-config panel for {@code entries}, anchored to one corner/edge of the
     * host screen's bounds.
     *
     * @param entries the components to expose sliders for, one card each, in display order
     * @param persistence where each entry's {@code contentScale}/{@code paddingScale} is read/written
     * @param anchor which corner/edge of the host screen's bounds the panel is anchored to
     * @return a new panel instance; construct one per host screen, not shared across screens
     */
    public static ScaleConfigPanel create(List<ScaleConfigEntry> entries, PersistenceProvider persistence, Anchor anchor) {
        return new ScaleConfigPanel(entries, persistence, anchor);
    }

    /**
     * Whether {@code componentId}'s "Move Text and Icons" flag is on in {@code persistence} — the same
     * value {@link ScaleConfigPanel#isMoveContentEnabled} reads back and the panel's own toggle (and
     * the toolbox's move toggles) write, for code that has the store but no panel instance.
     */
    public static boolean isMoveContentEnabled(PersistenceProvider persistence, String componentId) {
        return MoveFlags.isOn(persistence, componentId);
    }

    /** Sets the flag {@link #isMoveContentEnabled} reads; same stored key and format the panel's toggle uses. */
    public static void setMoveContentEnabled(PersistenceProvider persistence, String componentId, boolean enabled) {
        MoveFlags.set(persistence, componentId, enabled);
    }
}
