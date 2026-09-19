package dev.marie.framework.ui.scaleconfig;

import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.component.ComponentState;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The persisted open/collapsed state and bounds of each {@link ScaleConfigPanel} entry's window, under
 * a {@code <componentId>#window} key in the panel's {@link PersistenceProvider}, reusing the
 * (otherwise unused for this) {@code collapsed}/{@code x}/{@code y}/{@code width}/{@code height} fields
 * of {@link ComponentState}. An entry with no saved window state renders collapsed.
 */
final class WindowStates {

    /** Default persisted window state for an entry that has never been opened: collapsed (tab), with a sensible fallback size. */
    private static final ComponentState DEFAULT =
            new ComponentState(0, 0, ScaleConfigPanel.CARD_WIDTH, ScaleConfigPanel.CARD_HEIGHT, true, false, false, 0);

    /**
     * Every componentId any panel instance has rendered a tab/window for, across every instance in the
     * JVM — accumulated, never pruned. A consumer mod commonly constructs one panel per HUD widget
     * rather than one panel with many entries; "only one window open at a time" has to hold across all
     * of those sibling instances, so {@link #open} force-collapses over this set, not one panel's entries.
     */
    private static final Set<String> KNOWN_IDS = new LinkedHashSet<>();

    private final PersistenceProvider persistence;

    WindowStates(PersistenceProvider persistence) {
        this.persistence = persistence;
    }

    void register(String componentId) {
        KNOWN_IDS.add(componentId);
    }

    ComponentState load(String componentId) {
        return persistence.load(key(componentId)).orElse(DEFAULT);
    }

    void collapse(String componentId) {
        save(componentId, withCollapsed(load(componentId), true));
    }

    /** Persists {@code bounds} as the (open) window bounds, keeping the rest of the entry's saved fields. */
    void persistBounds(String componentId, Bounds bounds) {
        ComponentState base = load(componentId);
        save(componentId, new ComponentState(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                false, base.widthManual(), base.heightManual(), base.leftMargin(), base.contentScale(), base.paddingScale()));
    }

    /**
     * Opens {@code componentId}'s window at its previously persisted bounds, or at {@code defaultBounds}
     * if it has never been opened, and force-collapses every other known entry that was open, so at
     * most one window is ever open — across every panel instance.
     */
    void open(String componentId, Supplier<Bounds> defaultBounds) {
        for (String otherId : KNOWN_IDS) {
            if (!otherId.equals(componentId) && !load(otherId).collapsed()) {
                collapse(otherId);
            }
        }
        ComponentState existing = persistence.load(key(componentId)).orElse(null);
        Bounds bounds = existing != null
                ? new Bounds(existing.x(), existing.y(), existing.width(), existing.height())
                : defaultBounds.get();
        save(componentId, new ComponentState(bounds.x(), bounds.y(), bounds.width(), bounds.height(), false, false, false, 0));
    }

    /**
     * {@code bounds} kept at or above the window's minimum size and fully inside {@code screen} — there
     * is no maximum beyond the screen itself, so a window can be dragged as large as the player likes.
     */
    static Bounds clamp(Bounds bounds, Bounds screen) {
        int width = between(bounds.width(), ScaleConfigPanel.CARD_WIDTH, Math.max(ScaleConfigPanel.CARD_WIDTH, screen.width()));
        int height = between(bounds.height(), ScaleConfigPanel.CARD_HEIGHT, Math.max(ScaleConfigPanel.CARD_HEIGHT, screen.height()));
        int maxX = Math.max(screen.x(), screen.x() + screen.width() - width);
        int maxY = Math.max(screen.y(), screen.y() + screen.height() - height);
        return new Bounds(between(bounds.x(), screen.x(), maxX), between(bounds.y(), screen.y(), maxY), width, height);
    }

    private void save(String componentId, ComponentState state) {
        persistence.save(key(componentId), state);
    }

    private static int between(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }

    private static ComponentState withCollapsed(ComponentState base, boolean collapsed) {
        return new ComponentState(base.x(), base.y(), base.width(), base.height(), collapsed,
                base.widthManual(), base.heightManual(), base.leftMargin(), base.contentScale(), base.paddingScale());
    }

    private static String key(String componentId) {
        return componentId + "#window";
    }
}
