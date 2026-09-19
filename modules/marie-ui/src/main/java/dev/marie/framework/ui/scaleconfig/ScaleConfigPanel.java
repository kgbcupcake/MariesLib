package dev.marie.framework.ui.scaleconfig;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.ThemeKey;
import dev.marie.framework.ui.component.ComponentState;
import dev.marie.framework.ui.edit.ContentScaleController;
import dev.marie.framework.ui.edit.DraggableResizable;
import dev.marie.framework.ui.geometry.Anchor;
import dev.marie.framework.ui.geometry.Bounds;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Embeddable dashboard-style overlay for adjusting a set of components' persisted contentScale/paddingScale via sliders — not a Screen; a host screen owns/renders/forwards input to an instance only while visible. */
@ApiStatus.Experimental
public final class ScaleConfigPanel {

    private static final ComponentState DEFAULT_STATE =
            new ComponentState(0, 0, 0, 0, false, false, false, 0);

    /** Cycling fallback palette for entries with no explicit {@link ScaleConfigEntry#accentColor()}. */
    private static final int[] ACCENT_PALETTE = {0xFF5DA9E9, 0xFFE98F5D, 0xFF7ED9A6, 0xFFD97ED9, 0xFFE9DE5D};

    /** contentScale/paddingScale change applied per scroll notch — same step as {@code ContentScaleController.SCROLL_STEP}. */
    private static final double SCROLL_STEP = 0.05d;

    private static final int PANEL_MARGIN = 8;
    private static final int CARD_WIDTH = 224;
    private static final int CARD_GAP = 6;
    static final int CARD_PADDING = 6;
    static final int HEADER_HEIGHT = 14;
    private static final int ROW_LABEL_HEIGHT = 10;
    private static final int LABEL_TRACK_GAP = 2;
    private static final int SLIDER_HEIGHT = 7;
    static final int ROW_GAP = 5;

    private static final int CARD_HEIGHT = CARD_PADDING + HEADER_HEIGHT + ROW_GAP
            + 2 * (ROW_LABEL_HEIGHT + LABEL_TRACK_GAP + SLIDER_HEIGHT) + ROW_GAP + ROW_LABEL_HEIGHT + ROW_GAP + CARD_PADDING;

    /** Collapsed-tab row height — same anchor-stacking approach as the open card once used, just shorter. */
    private static final int TAB_HEIGHT = 18;

    /** Small header control that collapses the open window back to a tab. */
    private static final int COLLAPSE_BUTTON_SIZE = 9;

    /** The open window's content is still just the two slider rows, so it can never shrink below the original card size. */
    private static final int MIN_WINDOW_WIDTH = CARD_WIDTH;
    private static final int MIN_WINDOW_HEIGHT = CARD_HEIGHT;
    private static final int MAX_WINDOW_WIDTH = CARD_WIDTH * 2;
    static final int MAX_WINDOW_HEIGHT = CARD_HEIGHT * 2;

    /** Default persisted window state for an entry that has never been opened: collapsed (tab), with a sensible fallback size. */
    private static final ComponentState DEFAULT_WINDOW_STATE =
            new ComponentState(0, 0, CARD_WIDTH, CARD_HEIGHT, true, false, false, 0);

    /**
     * Per-anchor registry of panels currently claiming a vertical stacking slot, in
     * registration (render) order — mirrors the shape {@code EditOverlayScreen} already uses for
     * ordering multiple targets, applied here to collision-avoid multiple {@code ScaleConfigPanel}s
     * anchored to the same corner instead of a single list of render targets.
     *
     * <p>There's no explicit "hide" call a panel can make when it stops being visible (a host
     * screen simply stops calling {@link #render}), so staleness is self-healing instead: a panel
     * already present in its anchor's list when it renders again means a new render pass has
     * started, so the whole list — including any now-invisible panels that never got a chance to
     * drop out — is cleared before this pass's claims are recorded.
     */
    private static final Map<Anchor, List<StackClaim>> VISIBLE_CLAIMS = new EnumMap<>(Anchor.class);

    private record StackClaim(ScaleConfigPanel panel, int height) {}

    /**
     * Every componentId any {@code ScaleConfigPanel} instance has rendered a tab/window for, across
     * every instance in the JVM — accumulated the same self-healing, never-pruned way as {@link
     * #VISIBLE_CLAIMS}. A consumer mod commonly constructs one panel per HUD widget rather than one
     * panel with many entries (Nourished has separate call sites for its Nutrient HUD, Calorie
     * History, and Activity Log panels, for instance); "only one window open at a time" needs to
     * hold across all of those sibling instances, not just within whichever single instance's own
     * {@link #entries} happened to receive the click, so {@link #openEntry} force-collapses over
     * this set rather than {@code entries}.
     */
    private static final Set<String> KNOWN_WINDOW_COMPONENT_IDS = new LinkedHashSet<>();

    private final List<ScaleConfigEntry> entries;
    private final PersistenceProvider persistence;
    private final Anchor anchor;

    /** This frame's rendered collapsed-tab hit regions, rebuilt every render() pass, for click hit-testing. */
    private final List<TabLayout> lastTabLayout = new ArrayList<>();

    /** This frame's rendered open window (at most one), or {@code null} if every entry is collapsed. */
    private WindowLayout lastOpenWindow;

    /** This frame's rendered collapsed-tab stack bounding box, for the outside-panel early-out. */
    private Bounds lastPanelBounds = new Bounds(0, 0, 0, 0);

    /** The full screen {@link Bounds} passed into the most recent {@link #render}, used to clamp drag/resize gestures and to place a newly-opened window near the anchor. */
    private Bounds lastRenderBounds = new Bounds(0, 0, 0, 0);

    private String draggingWindowComponentId;
    private int dragGrabOffsetX;
    private int dragGrabOffsetY;

    private String resizingWindowComponentId;
    private int resizeOriginX;
    private int resizeOriginY;

    private String draggingSliderComponentId;
    private boolean draggingSliderIsPadding;
    private Bounds draggingSliderTrack;
    /** Live scrub value while a slider drag is active — held in memory only, not persisted, so scrubbing doesn't do a synchronous save every frame; {@link #mouseReleased} writes it through exactly once when the gesture ends. */
    private double draggingSliderLiveValue;

    public ScaleConfigPanel(List<ScaleConfigEntry> entries, PersistenceProvider persistence, Anchor anchor) {
        this.entries = entries;
        this.persistence = persistence;
        this.anchor = anchor;
    }

    public void render(RenderContext context, Bounds bounds) {
        lastTabLayout.clear();
        lastOpenWindow = null;
        lastRenderBounds = bounds;

        // Only the first entry found with a non-collapsed window state is honored as "open" — any
        // others (e.g. from a hand-edited save file) render as tabs instead of a second window.
        ScaleConfigEntry openEntry = null;
        ComponentState openState = null;
        int openIndex = -1;
        List<ScaleConfigEntry> collapsedEntries = new ArrayList<>();
        List<Integer> collapsedIndices = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            ScaleConfigEntry entry = entries.get(i);
            KNOWN_WINDOW_COMPONENT_IDS.add(entry.componentId());
            ComponentState windowState = loadWindowState(entry.componentId());
            if (openEntry == null && !windowState.collapsed()) {
                openEntry = entry;
                openState = windowState;
                openIndex = i;
            } else {
                collapsedEntries.add(entry);
                collapsedIndices.add(i);
            }
        }

        int panelHeight = collapsedEntries.size() * TAB_HEIGHT + Math.max(0, collapsedEntries.size() - 1) * CARD_GAP;
        int panelX = anchorX(bounds, anchor);
        int stackOffset = claimStackOffset(panelHeight);
        int panelY = anchorY(bounds, anchor, panelHeight, stackOffset);
        lastPanelBounds = new Bounds(panelX, panelY, CARD_WIDTH, panelHeight);

        int y = panelY;
        for (int i = 0; i < collapsedEntries.size(); i++) {
            ScaleConfigEntry entry = collapsedEntries.get(i);
            int accent = accentFor(entry, collapsedIndices.get(i));
            Bounds tabBounds = new Bounds(panelX, y, CARD_WIDTH, TAB_HEIGHT);
            drawTab(context, entry, accent, tabBounds);
            lastTabLayout.add(new TabLayout(entry, tabBounds));
            y += TAB_HEIGHT + CARD_GAP;
        }

        if (openEntry != null) {
            Bounds raw = HostedWindow.fit(openEntry, new Bounds(openState.x(), openState.y(), openState.width(), openState.height()));
            Bounds windowBounds = clampWindowBounds(raw, bounds);
            WindowLayout layout = layoutWindow(openEntry, accentFor(openEntry, openIndex), windowBounds);
            drawWindow(context, layout);
            lastOpenWindow = layout;
        }
    }

    private int accentFor(ScaleConfigEntry entry, int index) {
        return entry.accentColor() != null ? entry.accentColor() : ACCENT_PALETTE[index % ACCENT_PALETTE.length];
    }

    private static int anchorX(Bounds bounds, Anchor anchor) {
        return switch (anchor) {
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> bounds.x() + bounds.width() - PANEL_MARGIN - CARD_WIDTH;
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> bounds.x() + (bounds.width() - CARD_WIDTH) / 2;
            default -> bounds.x() + PANEL_MARGIN;
        };
    }

    private static int anchorY(Bounds bounds, Anchor anchor, int panelHeight, int stackOffset) {
        return switch (anchor) {
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT ->
                    bounds.y() + bounds.height() - PANEL_MARGIN - panelHeight - stackOffset;
            case CENTER_LEFT, CENTER, CENTER_RIGHT -> bounds.y() + (bounds.height() - panelHeight) / 2 + stackOffset;
            default -> bounds.y() + PANEL_MARGIN + stackOffset;
        };
    }

    /**
     * Claims this panel's vertical stacking slot for {@link #anchor}: returns the combined height
     * of other panels already claiming that same anchor earlier in this render pass (0 when this
     * is the only visible panel at the anchor, reproducing the pre-stacking position exactly), then
     * records this panel's own height so panels registering after it stack below/above accordingly.
     * See {@link #VISIBLE_CLAIMS} for how stale claims from now-invisible panels get dropped.
     */
    private int claimStackOffset(int panelHeight) {
        List<StackClaim> claims = VISIBLE_CLAIMS.computeIfAbsent(anchor, key -> new ArrayList<>());
        for (StackClaim claim : claims) {
            if (claim.panel() == this) {
                claims.clear();
                break;
            }
        }
        int offset = 0;
        for (StackClaim claim : claims) {
            offset += claim.height();
        }
        claims.add(new StackClaim(this, panelHeight));
        return offset;
    }

    private void drawTab(RenderContext context, ScaleConfigEntry entry, int accent, Bounds tabBounds) {
        int panelBg = context.theme().color(ThemeKey.PANEL_BACKGROUND);
        int border = context.theme().color(ThemeKey.BORDER);
        context.drawRoundedRect(tabBounds.x(), tabBounds.y(), tabBounds.width(), tabBounds.height(), 1, panelBg, border);
        context.fillRect(tabBounds.x() + 1, tabBounds.y() + 1, 2, tabBounds.height() - 2, accent);
        context.drawText(entry.label().getString(), tabBounds.x() + CARD_PADDING, tabBounds.y() + (tabBounds.height() - 8) / 2, accent, 0.85f);
    }

    private WindowLayout layoutWindow(ScaleConfigEntry entry, int accent, Bounds windowBounds) {
        int trackX = windowBounds.x() + CARD_PADDING;
        int trackW = windowBounds.width() - CARD_PADDING * 2;
        int row1Y = windowBounds.y() + CARD_PADDING + HEADER_HEIGHT + ROW_GAP;
        int track1Y = row1Y + ROW_LABEL_HEIGHT + LABEL_TRACK_GAP;
        int row2Y = track1Y + SLIDER_HEIGHT + ROW_GAP;
        int track2Y = row2Y + ROW_LABEL_HEIGHT + LABEL_TRACK_GAP;
        int row3Y = track2Y + SLIDER_HEIGHT + ROW_GAP;
        Bounds textTrack = new Bounds(trackX, track1Y, trackW, SLIDER_HEIGHT);
        Bounds paddingTrack = new Bounds(trackX, track2Y, trackW, SLIDER_HEIGHT);
        // Click/drag hit-testing uses these, not the drawn track directly — the track itself is a
        // thin SLIDER_HEIGHT-tall strip, too easy to miss with an initial press. Each row's hit
        // region spans from its label down through the bottom of its track, same x/width as the
        // track, so a press anywhere in the row (label included) starts the drag.
        Bounds textRowHit = new Bounds(trackX, row1Y, trackW, (track1Y + SLIDER_HEIGHT) - row1Y);
        Bounds paddingRowHit = new Bounds(trackX, row2Y, trackW, (track2Y + SLIDER_HEIGHT) - row2Y);
        // No track/scrub for this row — a single click flips it — so its hit region is just its own label height.
        Bounds moveContentRowHit = new Bounds(trackX, row3Y, trackW, ROW_LABEL_HEIGHT);
        Bounds headerBounds = new Bounds(windowBounds.x(), windowBounds.y(), windowBounds.width(), CARD_PADDING + HEADER_HEIGHT);
        Bounds collapseButton = new Bounds(
                windowBounds.x() + windowBounds.width() - CARD_PADDING - COLLAPSE_BUTTON_SIZE,
                windowBounds.y() + (CARD_PADDING + HEADER_HEIGHT - COLLAPSE_BUTTON_SIZE) / 2,
                COLLAPSE_BUTTON_SIZE, COLLAPSE_BUTTON_SIZE);
        if (entry.content() != null) {
            textRowHit = paddingRowHit = moveContentRowHit = HostedWindow.NO_HIT;
        }
        return new WindowLayout(entry, accent, windowBounds, row1Y, textTrack, textRowHit, row2Y, paddingTrack, paddingRowHit,
                row3Y, moveContentRowHit, headerBounds, collapseButton);
    }

    private void drawWindow(RenderContext context, WindowLayout layout) {
        Bounds window = layout.windowBounds();
        int panelBg = context.theme().color(ThemeKey.PANEL_BACKGROUND);
        int border = context.theme().color(ThemeKey.BORDER);
        int textSecondary = context.theme().color(ThemeKey.TEXT_SECONDARY);
        context.drawRoundedRect(window.x(), window.y(), window.width(), window.height(), 1, panelBg, border);

        String componentId = layout.entry().componentId();
        boolean draggingThis = componentId.equals(draggingSliderComponentId);
        double contentScale = draggingThis && !draggingSliderIsPadding
                ? draggingSliderLiveValue
                : persistence.load(componentId).map(ComponentState::contentScale).orElse(DEFAULT_STATE.contentScale());
        double paddingScale = draggingThis && draggingSliderIsPadding
                ? draggingSliderLiveValue
                : persistence.load(componentId).map(ComponentState::paddingScale).orElse(DEFAULT_STATE.paddingScale());

        // "Bold" header: no font-weight primitive on RenderContext, so accent color + a slightly larger scale stand in for it.
        context.drawText(layout.entry().label().getString(), window.x() + CARD_PADDING, window.y() + CARD_PADDING, layout.accentColor(), 1.05f);
        drawCollapseButton(context, layout.collapseButtonBounds(), layout.accentColor());

        if (!HostedWindow.render(context, layout.entry(), window)) {
            drawSliderRow(context, "config.marieslib.scaleconfig.textScale", window.x() + CARD_PADDING, layout.row1Y(), layout.textTrack(),
                    contentScale, textSecondary, layout.accentColor());
            drawSliderRow(context, "config.marieslib.scaleconfig.padding", window.x() + CARD_PADDING, layout.row2Y(), layout.paddingTrack(),
                    paddingScale, textSecondary, layout.accentColor());
            drawToggleRow(context, "config.marieslib.scaleconfig.moveContent", window.x() + CARD_PADDING, layout.row3Y(), layout.moveContentRowHit(),
                    isMoveContentEnabled(componentId), textSecondary, layout.accentColor());
        }

        boolean resizingThis = componentId.equals(resizingWindowComponentId);
        context.drawResizeHandle(window.x() + window.width() - DraggableResizable.RESIZE_HANDLE_SIZE,
                window.y() + window.height() - DraggableResizable.RESIZE_HANDLE_SIZE, false, resizingThis);
    }

    private static void drawCollapseButton(RenderContext context, Bounds bounds, int accent) {
        int bg = (0x40 << 24) | (accent & 0x00FFFFFF);
        context.drawRoundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 1, bg, accent);
        context.drawText("x", bounds.x() + 2, bounds.y() + 1, accent, 0.65f);
    }

    private void drawSliderRow(RenderContext context, String labelKey, int labelX, int labelY, Bounds track,
                                double value, int textColor, int accent) {
        context.drawText(Component.translatable(labelKey).getString(), labelX, labelY, textColor, 0.8f);
        String pct = Math.round(value * 100) + "%";
        context.drawText(pct, track.x() + track.width() - 24, labelY, textColor, 0.8f);
        float fillPct = (float) ((value - ContentScaleController.SCALE_STORAGE_MIN)
                / (ContentScaleController.SCALE_STORAGE_MAX - ContentScaleController.SCALE_STORAGE_MIN));
        int trackBg = context.theme().color(ThemeKey.BAR_BACKGROUND);
        context.drawBar(track.x(), track.y(), track.width(), track.height(), fillPct, trackBg, accent);
    }

    /** A single-line "label ... ON/OFF" row with no track/scrub — a click anywhere in {@code rowHit} just flips the state, handled in {@link #handleWindowClick}. */
    private void drawToggleRow(RenderContext context, String labelKey, int labelX, int labelY, Bounds rowHit,
                                boolean enabled, int textColor, int accent) {
        context.drawText(Component.translatable(labelKey).getString(), labelX, labelY, textColor, 0.8f);
        String state = enabled ? "ON" : "OFF";
        context.drawText(state, rowHit.x() + rowHit.width() - 24, labelY, enabled ? accent : textColor, 0.8f);
    }

    /**
     * Hit-tests against the panel's own rendered bounds first (the collapsed-tab stack, plus the
     * open window's bounds if any): false (uncontested) outside them so the host screen keeps
     * handling its own dragging, true for anything inside. Left-clicks act on whatever was hit —
     * a tab opens it (collapsing any other open entry), the open window's header starts a
     * reposition drag, its corner handle starts a resize, its collapse control re-collapses it,
     * and its slider tracks jump to the clicked value and start a scrub gesture continued by
     * {@link #mouseDragged} while the button stays held, same as before.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (!withinInteractiveArea(mx, my)) {
            return false;
        }
        if (button == 0) {
            if (lastOpenWindow != null && lastOpenWindow.windowBounds().contains(mx, my)) {
                handleWindowClick(mouseX, mouseY, mx, my);
            } else {
                for (TabLayout tab : lastTabLayout) {
                    if (tab.tabBounds().contains(mx, my)) {
                        openEntry(tab.entry());
                        break;
                    }
                }
            }
        }
        return true;
    }

    private boolean withinInteractiveArea(int mx, int my) {
        if (lastOpenWindow != null && lastOpenWindow.windowBounds().contains(mx, my)) {
            return true;
        }
        return lastPanelBounds.contains(mx, my);
    }

    private void handleWindowClick(double mouseX, double mouseY, int mx, int my) {
        WindowLayout window = lastOpenWindow;
        Bounds windowBounds = window.windowBounds();
        if (HostedWindow.mouseClicked(window.entry(), windowBounds, mouseX, mouseY)) {
            return;
        }
        // Slider rows are checked first: on a small/short window the corner resize handle's hitbox
        // can overlap the right end of the padding row, and a slider press landing in that overlap
        // must still win — the resize handle only gets a look at whatever the sliders didn't claim.
        if (window.textRowHit().contains(mx, my)) {
            draggingSliderComponentId = window.entry().componentId();
            draggingSliderIsPadding = false;
            draggingSliderTrack = window.textTrack();
            draggingSliderLiveValue = valueAt(mouseX, draggingSliderTrack);
            return;
        }
        if (window.paddingRowHit().contains(mx, my)) {
            draggingSliderComponentId = window.entry().componentId();
            draggingSliderIsPadding = true;
            draggingSliderTrack = window.paddingTrack();
            draggingSliderLiveValue = valueAt(mouseX, draggingSliderTrack);
            return;
        }
        if (window.moveContentRowHit().contains(mx, my)) {
            toggleMoveContent(window.entry().componentId());
            return;
        }
        if (DraggableResizable.handleBounds(windowBounds).contains(mx, my)) {
            resizingWindowComponentId = window.entry().componentId();
            resizeOriginX = windowBounds.x();
            resizeOriginY = windowBounds.y();
            return;
        }
        if (window.collapseButtonBounds().contains(mx, my)) {
            collapseEntry(window.entry());
            return;
        }
        if (window.headerBounds().contains(mx, my)) {
            draggingWindowComponentId = window.entry().componentId();
            dragGrabOffsetX = mx - windowBounds.x();
            dragGrabOffsetY = my - windowBounds.y();
        }
    }

    /** Continues whatever drag/resize/slider gesture {@link #mouseClicked} started on the open window, clamped to the last {@link #render}-supplied screen {@link Bounds} (window drag/resize) or the track (slider scrub). Uncontested (false) if no such gesture is active. */
    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (lastOpenWindow != null && HostedWindow.mouseDragged(lastOpenWindow.entry(), mouseX, mouseY, button)) {
            return true;
        }
        if (draggingSliderComponentId != null) {
            draggingSliderLiveValue = valueAt(mouseX, draggingSliderTrack);
            return true;
        }
        if (draggingWindowComponentId != null && lastOpenWindow != null) {
            Bounds current = lastOpenWindow.windowBounds();
            Bounds moved = new Bounds(mx - dragGrabOffsetX, my - dragGrabOffsetY, current.width(), current.height());
            Bounds clamped = clampWindowBounds(moved, lastRenderBounds);
            persistWindowBounds(draggingWindowComponentId, clamped);
            return true;
        }
        if (resizingWindowComponentId != null) {
            Bounds resized = new Bounds(resizeOriginX, resizeOriginY, mx - resizeOriginX, my - resizeOriginY);
            Bounds clamped = clampWindowBounds(resized, lastRenderBounds);
            persistWindowBounds(resizingWindowComponentId, clamped);
            return true;
        }
        return false;
    }

    /**
     * Finalizes and ends any active drag/resize gesture started on the open window's header/corner
     * handle by {@link #mouseClicked} and continued by {@link #mouseDragged} — persists the final
     * bounds the same way {@link #mouseDragged}'s own preview does, then clears the gesture state.
     * Returns true if a gesture was consumed, false (uncontested) if none was active, matching
     * {@link #mouseClicked}/{@link #mouseDragged}/{@link #mouseScrolled}'s boolean contract.
     */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (lastOpenWindow != null && HostedWindow.mouseReleased(lastOpenWindow.entry(), mouseX, mouseY, button)) {
            return true;
        }
        if (draggingSliderComponentId != null) {
            if (draggingSliderIsPadding) {
                savePaddingScale(draggingSliderComponentId, draggingSliderLiveValue);
            } else {
                saveContentScale(draggingSliderComponentId, draggingSliderLiveValue);
            }
            draggingSliderComponentId = null;
            draggingSliderTrack = null;
            return true;
        }
        if (draggingWindowComponentId != null && lastOpenWindow != null) {
            Bounds current = lastOpenWindow.windowBounds();
            Bounds moved = new Bounds(mx - dragGrabOffsetX, my - dragGrabOffsetY, current.width(), current.height());
            Bounds clamped = clampWindowBounds(moved, lastRenderBounds);
            persistWindowBounds(draggingWindowComponentId, clamped);
            draggingWindowComponentId = null;
            return true;
        }
        if (resizingWindowComponentId != null) {
            Bounds resized = new Bounds(resizeOriginX, resizeOriginY, mx - resizeOriginX, my - resizeOriginY);
            Bounds clamped = clampWindowBounds(resized, lastRenderBounds);
            persistWindowBounds(resizingWindowComponentId, clamped);
            resizingWindowComponentId = null;
            return true;
        }
        return false;
    }

    /** Hit-tests against the panel's own rendered bounds first: false outside them, true for anything inside — nudging the slider under the cursor by one step, if any, matching {@code ContentScaleController.handleScroll}'s in-world scroll-to-adjust gesture. Only the open window has sliders; collapsed tabs ignore scroll. */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (lastOpenWindow == null || !lastOpenWindow.windowBounds().contains(mx, my)) {
            return false;
        }
        if (HostedWindow.mouseScrolled(lastOpenWindow.entry(), lastOpenWindow.windowBounds(), mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        if (scrollY != 0) {
            double delta = scrollY > 0 ? SCROLL_STEP : -SCROLL_STEP;
            WindowLayout window = lastOpenWindow;
            if (window.textRowHit().contains(mx, my)) {
                double current = persistence.load(window.entry().componentId()).map(ComponentState::contentScale).orElse(DEFAULT_STATE.contentScale());
                saveContentScale(window.entry().componentId(), clampStorage(current + delta));
            } else if (window.paddingRowHit().contains(mx, my)) {
                double current = persistence.load(window.entry().componentId()).map(ComponentState::paddingScale).orElse(DEFAULT_STATE.paddingScale());
                savePaddingScale(window.entry().componentId(), clampStorage(current + delta));
            }
        }
        return true;
    }

    private static double valueAt(double mouseX, Bounds track) {
        double pct = (mouseX - track.x()) / track.width();
        double clampedPct = Math.min(1.0, Math.max(0.0, pct));
        return clampStorage(ContentScaleController.SCALE_STORAGE_MIN
                + clampedPct * (ContentScaleController.SCALE_STORAGE_MAX - ContentScaleController.SCALE_STORAGE_MIN));
    }

    private static double clampStorage(double value) {
        return Math.min(ContentScaleController.SCALE_STORAGE_MAX, Math.max(ContentScaleController.SCALE_STORAGE_MIN, value));
    }

    // Reads the current persisted state fresh at click/scroll time and touches only its own field, so adjusting one slider can never clobber the other's already-saved value.
    private void saveContentScale(String componentId, double contentScale) {
        ComponentState base = persistence.load(componentId).orElse(DEFAULT_STATE);
        persistence.save(componentId, withContentScale(base, contentScale));
    }

    private void savePaddingScale(String componentId, double paddingScale) {
        ComponentState base = persistence.load(componentId).orElse(DEFAULT_STATE);
        persistence.save(componentId, withPaddingScale(base, paddingScale));
    }

    private static ComponentState withContentScale(ComponentState base, double contentScale) {
        return new ComponentState(base.x(), base.y(), base.width(), base.height(), base.collapsed(),
                base.widthManual(), base.heightManual(), base.leftMargin(), contentScale, base.paddingScale());
    }

    private static ComponentState withPaddingScale(ComponentState base, double paddingScale) {
        return new ComponentState(base.x(), base.y(), base.width(), base.height(), base.collapsed(),
                base.widthManual(), base.heightManual(), base.leftMargin(), base.contentScale(), paddingScale);
    }

    /**
     * Opens {@code entry}'s window (using its previously persisted bounds, or a fresh default near
     * {@link #anchor} if it's never been opened) and force-collapses every other known entry that
     * wasn't already collapsed, so at most one window is ever open — across every {@code
     * ScaleConfigPanel} instance in the JVM, per {@link #KNOWN_WINDOW_COMPONENT_IDS}, not just this
     * instance's own {@link #entries}.
     */
    private void openEntry(ScaleConfigEntry entry) {
        for (String otherId : KNOWN_WINDOW_COMPONENT_IDS) {
            if (otherId.equals(entry.componentId())) {
                continue;
            }
            ComponentState otherState = loadWindowState(otherId);
            if (!otherState.collapsed()) {
                saveWindowState(otherId, withCollapsed(otherState, true));
            }
        }
        ComponentState existing = persistence.load(windowKey(entry.componentId())).orElse(null);
        Bounds bounds = existing != null
                ? new Bounds(existing.x(), existing.y(), existing.width(), existing.height())
                : defaultWindowBounds();
        saveWindowState(entry.componentId(), new ComponentState(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                false, false, false, 0));
    }

    private void collapseEntry(ScaleConfigEntry entry) {
        ComponentState state = loadWindowState(entry.componentId());
        saveWindowState(entry.componentId(), withCollapsed(state, true));
    }

    private void persistWindowBounds(String componentId, Bounds bounds) {
        ComponentState base = loadWindowState(componentId);
        saveWindowState(componentId, new ComponentState(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                false, base.widthManual(), base.heightManual(), base.leftMargin(), base.contentScale(), base.paddingScale()));
    }

    /**
     * Default position/size for an entry's window the first time it opens with no prior persisted
     * window state: today's card size, offset clear of the currently-rendered collapsed-tab stack
     * ({@link #lastPanelBounds}) rather than placed directly on top of it — the window (card-sized)
     * is far taller than a single tab, so anchoring it at the bare anchor point would render it over
     * whichever other entries' tabs are still stacked there. Bottom-anchored stacks grow upward from
     * the screen edge, so the window goes above them instead of below.
     */
    private Bounds defaultWindowBounds() {
        int x = anchorX(lastRenderBounds, anchor);
        boolean stacksUpward = switch (anchor) {
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> true;
            default -> false;
        };
        int y = stacksUpward
                ? lastPanelBounds.y() - CARD_GAP - CARD_HEIGHT
                : lastPanelBounds.y() + lastPanelBounds.height() + CARD_GAP;
        return new Bounds(x, y, CARD_WIDTH, CARD_HEIGHT);
    }

    private static Bounds clampWindowBounds(Bounds bounds, Bounds screen) {
        int width = clampInt(bounds.width(), MIN_WINDOW_WIDTH, MAX_WINDOW_WIDTH);
        int height = clampInt(bounds.height(), MIN_WINDOW_HEIGHT, MAX_WINDOW_HEIGHT);
        int minX = screen.x();
        int minY = screen.y();
        int maxX = Math.max(minX, screen.x() + screen.width() - width);
        int maxY = Math.max(minY, screen.y() + screen.height() - height);
        int x = clampInt(bounds.x(), minX, maxX);
        int y = clampInt(bounds.y(), minY, maxY);
        return new Bounds(x, y, width, height);
    }

    private static int clampInt(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }

    private ComponentState loadWindowState(String componentId) {
        return persistence.load(windowKey(componentId)).orElse(DEFAULT_WINDOW_STATE);
    }

    private void saveWindowState(String componentId, ComponentState state) {
        persistence.save(windowKey(componentId), state);
    }

    private static ComponentState withCollapsed(ComponentState base, boolean collapsed) {
        return new ComponentState(base.x(), base.y(), base.width(), base.height(), collapsed,
                base.widthManual(), base.heightManual(), base.leftMargin(), base.contentScale(), base.paddingScale());
    }

    private static String windowKey(String componentId) {
        return componentId + "#window";
    }

    private static String moveContentKey(String componentId) {
        return componentId + "#moveContent";
    }

    /**
     * Whether {@code componentId}'s "Move Text and Icons" toggle is currently on — a host panel
     * polls this each frame (while its own edit mode/scale-config panel is visible) to decide
     * whether dragging its content should translate the content instead of the whole component.
     * Storage reuses {@link ComponentState#collapsed()} as a generic boolean flag under this
     * dedicated {@code #moveContent}-suffixed key — unrelated to any window's own collapsed state,
     * same as how other callers repurpose a {@code ComponentState} field for an unrelated single
     * value rather than inventing a new record shape for one boolean/int pair.
     */
    public boolean isMoveContentEnabled(String componentId) {
        return persistence.load(moveContentKey(componentId)).map(ComponentState::collapsed).orElse(false);
    }

    private void toggleMoveContent(String componentId) {
        boolean enabled = isMoveContentEnabled(componentId);
        persistence.save(moveContentKey(componentId), new ComponentState(0, 0, 0, 0, !enabled, false, false, 0));
    }

    private record TabLayout(ScaleConfigEntry entry, Bounds tabBounds) {}

    private record WindowLayout(ScaleConfigEntry entry, int accentColor, Bounds windowBounds,
                                 int row1Y, Bounds textTrack, Bounds textRowHit,
                                 int row2Y, Bounds paddingTrack, Bounds paddingRowHit,
                                 int row3Y, Bounds moveContentRowHit,
                                 Bounds headerBounds, Bounds collapseButtonBounds) {}
}
