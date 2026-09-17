package dev.marie.framework.ui.scaleconfig;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.ThemeKey;
import dev.marie.framework.ui.component.ComponentState;
import dev.marie.framework.ui.edit.ContentScaleController;
import dev.marie.framework.ui.geometry.Anchor;
import dev.marie.framework.ui.geometry.Bounds;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private static final int CARD_WIDTH = 200;
    private static final int CARD_GAP = 6;
    private static final int CARD_PADDING = 6;
    private static final int HEADER_HEIGHT = 12;
    private static final int ROW_LABEL_HEIGHT = 9;
    private static final int LABEL_TRACK_GAP = 2;
    private static final int SLIDER_HEIGHT = 6;
    private static final int ROW_GAP = 4;
    private static final int PILL_WIDTH = 34;
    private static final int PILL_HEIGHT = 10;

    private static final int CARD_HEIGHT = CARD_PADDING + HEADER_HEIGHT + ROW_GAP
            + 2 * (ROW_LABEL_HEIGHT + LABEL_TRACK_GAP + SLIDER_HEIGHT) + ROW_GAP + CARD_PADDING;

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

    private final List<ScaleConfigEntry> entries;
    private final PersistenceProvider persistence;
    private final Anchor anchor;

    /** This frame's rendered card/slider hit regions, rebuilt every render() pass, for click/scroll hit-testing. */
    private final List<CardLayout> lastLayout = new ArrayList<>();

    /** This frame's overall rendered bounding box, for the outside-panel early-out in mouseClicked/mouseScrolled. */
    private Bounds lastPanelBounds = new Bounds(0, 0, 0, 0);

    /** componentId of the slider currently held from mouseClicked, or null when no drag is in progress. */
    private String draggingComponentId;

    /** Which of that entry's two tracks is held — true for contentScale, false for paddingScale. */
    private boolean draggingContentTrack;

    public ScaleConfigPanel(List<ScaleConfigEntry> entries, PersistenceProvider persistence, Anchor anchor) {
        this.entries = entries;
        this.persistence = persistence;
        this.anchor = anchor;
    }

    public void render(RenderContext context, Bounds bounds) {
        lastLayout.clear();
        int panelHeight = entries.size() * CARD_HEIGHT + Math.max(0, entries.size() - 1) * CARD_GAP;
        int panelX = anchorX(bounds, anchor);
        int stackOffset = claimStackOffset(panelHeight);
        int panelY = anchorY(bounds, anchor, panelHeight, stackOffset);
        lastPanelBounds = new Bounds(panelX, panelY, CARD_WIDTH, panelHeight);

        int y = panelY;
        for (int i = 0; i < entries.size(); i++) {
            CardLayout layout = layoutCard(entries.get(i), i, panelX, y);
            drawCard(context, layout);
            lastLayout.add(layout);
            y += CARD_HEIGHT + CARD_GAP;
        }
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

    private CardLayout layoutCard(ScaleConfigEntry entry, int index, int cardX, int cardY) {
        Bounds cardBounds = new Bounds(cardX, cardY, CARD_WIDTH, CARD_HEIGHT);
        int trackX = cardX + CARD_PADDING;
        int trackW = CARD_WIDTH - CARD_PADDING * 2;
        int row1Y = cardY + CARD_PADDING + HEADER_HEIGHT + ROW_GAP;
        int track1Y = row1Y + ROW_LABEL_HEIGHT + LABEL_TRACK_GAP;
        int row2Y = track1Y + SLIDER_HEIGHT + ROW_GAP;
        int track2Y = row2Y + ROW_LABEL_HEIGHT + LABEL_TRACK_GAP;
        Bounds textTrack = new Bounds(trackX, track1Y, trackW, SLIDER_HEIGHT);
        Bounds paddingTrack = new Bounds(trackX, track2Y, trackW, SLIDER_HEIGHT);
        int accent = entry.accentColor() != null ? entry.accentColor() : ACCENT_PALETTE[index % ACCENT_PALETTE.length];
        return new CardLayout(entry, accent, cardBounds, row1Y, textTrack, row2Y, paddingTrack);
    }

    private void drawCard(RenderContext context, CardLayout layout) {
        Bounds card = layout.cardBounds();
        int panelBg = context.theme().color(ThemeKey.PANEL_BACKGROUND);
        int border = context.theme().color(ThemeKey.BORDER);
        int textSecondary = context.theme().color(ThemeKey.TEXT_SECONDARY);
        context.drawRoundedRect(card.x(), card.y(), card.width(), card.height(), 1, panelBg, border);

        double contentScale = persistence.load(layout.entry().componentId()).map(ComponentState::contentScale).orElse(DEFAULT_STATE.contentScale());
        double paddingScale = persistence.load(layout.entry().componentId()).map(ComponentState::paddingScale).orElse(DEFAULT_STATE.paddingScale());

        // "Bold" header: no font-weight primitive on RenderContext, so accent color + a slightly larger scale stand in for it.
        context.drawText(layout.entry().label().getString(), card.x() + CARD_PADDING, card.y() + CARD_PADDING, layout.accentColor(), 1.05f);
        drawPill(context, card.x() + card.width() - CARD_PADDING - PILL_WIDTH, card.y() + CARD_PADDING, layout.accentColor(), combinedPercent(contentScale, paddingScale));

        drawSliderRow(context, "config.marieslib.scaleconfig.textScale", card.x() + CARD_PADDING, layout.row1Y(), layout.textTrack(),
                contentScale, textSecondary, layout.accentColor());
        drawSliderRow(context, "config.marieslib.scaleconfig.padding", card.x() + CARD_PADDING, layout.row2Y(), layout.paddingTrack(),
                paddingScale, textSecondary, layout.accentColor());
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

    // Pill shows the average of both sliders' current percentages — a single glance summary of this card's overall state, not either slider's value alone.
    private static int combinedPercent(double contentScale, double paddingScale) {
        return (int) Math.round((contentScale + paddingScale) / 2.0 * 100);
    }

    private static void drawPill(RenderContext context, int x, int y, int accent, int percent) {
        int pillBg = (0x40 << 24) | (accent & 0x00FFFFFF);
        context.drawRoundedRect(x, y, PILL_WIDTH, PILL_HEIGHT, 1, pillBg, accent);
        context.drawText(String.format(Locale.ROOT, "%d%%", percent), x + 4, y + 1, accent, 0.7f);
    }

    /** Hit-tests against the panel's own rendered bounds first: false (uncontested) outside them so the host screen keeps handling its own dragging, true for anything inside — jumping the slider under the click, if any. */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!lastPanelBounds.contains((int) mouseX, (int) mouseY)) {
            return false;
        }
        if (button == 0) {
            for (CardLayout layout : lastLayout) {
                if (layout.textTrack().contains((int) mouseX, (int) mouseY)) {
                    draggingComponentId = layout.entry().componentId();
                    draggingContentTrack = true;
                    saveContentScale(draggingComponentId, valueAt(mouseX, layout.textTrack()));
                    break;
                }
                if (layout.paddingTrack().contains((int) mouseX, (int) mouseY)) {
                    draggingComponentId = layout.entry().componentId();
                    draggingContentTrack = false;
                    savePaddingScale(draggingComponentId, valueAt(mouseX, layout.paddingTrack()));
                    break;
                }
            }
        }
        return true;
    }

    /**
     * Continues updating the slider grabbed in {@link #mouseClicked} from the drag's live X, as
     * long as button 0 is still held — matching the track under the initial click rather than
     * whatever's under the cursor now, so a fast drag past the card's edges (or outside {@link
     * #lastPanelBounds} entirely) keeps controlling the same slider instead of losing it.
     */
    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (button != 0 || draggingComponentId == null) {
            return false;
        }
        for (CardLayout layout : lastLayout) {
            if (!layout.entry().componentId().equals(draggingComponentId)) {
                continue;
            }
            if (draggingContentTrack) {
                saveContentScale(draggingComponentId, valueAt(mouseX, layout.textTrack()));
            } else {
                savePaddingScale(draggingComponentId, valueAt(mouseX, layout.paddingTrack()));
            }
            break;
        }
        return true;
    }

    /** Releases whatever slider {@link #mouseClicked}/{@link #mouseDragged} were holding, if any. */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0 || draggingComponentId == null) {
            return false;
        }
        draggingComponentId = null;
        return true;
    }

    /** Hit-tests against the panel's own rendered bounds first: false outside them, true for anything inside — nudging the slider under the cursor by one step, if any, matching {@code ContentScaleController.handleScroll}'s in-world scroll-to-adjust gesture. */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!lastPanelBounds.contains((int) mouseX, (int) mouseY)) {
            return false;
        }
        if (scrollY != 0) {
            double delta = scrollY > 0 ? SCROLL_STEP : -SCROLL_STEP;
            for (CardLayout layout : lastLayout) {
                if (layout.textTrack().contains((int) mouseX, (int) mouseY)) {
                    double current = persistence.load(layout.entry().componentId()).map(ComponentState::contentScale).orElse(DEFAULT_STATE.contentScale());
                    saveContentScale(layout.entry().componentId(), clampStorage(current + delta));
                    break;
                }
                if (layout.paddingTrack().contains((int) mouseX, (int) mouseY)) {
                    double current = persistence.load(layout.entry().componentId()).map(ComponentState::paddingScale).orElse(DEFAULT_STATE.paddingScale());
                    savePaddingScale(layout.entry().componentId(), clampStorage(current + delta));
                    break;
                }
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

    private record CardLayout(ScaleConfigEntry entry, int accentColor, Bounds cardBounds,
                               int row1Y, Bounds textTrack, int row2Y, Bounds paddingTrack) {}
}
