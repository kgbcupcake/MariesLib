package dev.marie.framework.ui.itemeditor;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.ThemeKey;
import dev.marie.framework.ui.geometry.Bounds;
import dev.marie.framework.ui.toolbox.OptionStyle;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link ItemEditorPanel}'s small left-aligned File/Info menu bar and right-aligned Save/Revert
 * controls, styled after a desktop window's own menu bar rather than a stretched tab strip.
 * Pulled out of {@code ItemEditorPanel} itself since the dropdown/hover/confirm-flash bookkeeping
 * here is pure presentation — all of the actual business logic (what Save does, what the File
 * dropdown lists) stays on the panel, reached back through {@link Host} so this class never needs
 * to know about {@code ValueDefinition}s, {@code SourceClassificationRegistry}, or any of the
 * panel's other internals.
 */
@ApiStatus.Internal
final class ItemEditorMenuBar {

    /** What {@link ItemEditorPanel} supplies so this class can render/click without knowing the panel's internals. */
    interface Host {
        /** Whether Save/Revert should be clickable right now (there's a targeted item to act on). */
        boolean actionsEnabled();

        /** Whether the Info view (the classification trace) is the one currently showing. */
        boolean infoActive();

        /** Info was clicked — switch to (or, if a mod screen was showing, straight to) the trace view. */
        void onInfoClicked();

        /** Fresh navigation entries for the File dropdown — a "Values" entry plus one per registered {@link ItemEditorScreenProvider}. */
        List<MenuEntry> buildFileMenuEntries();

        /** Save was clicked — act on whichever page is currently active. */
        void onSaveClicked();

        /** Revert was clicked — act on whichever page is currently active. */
        void onRevertClicked();
    }

    /** One entry the File dropdown shows, before layout: {@code null} label means a separator; {@code active} highlights the entry for the currently selected view. */
    record MenuEntry(@Nullable String label, @Nullable Runnable action, boolean active) {
        static MenuEntry separator() {
            return new MenuEntry(null, null, false);
        }

        boolean isSeparator() {
            return label == null;
        }
    }

    /** One rendered row of the open File dropdown: its clickable area and the action a click on it runs — null for a non-clickable separator. */
    private record MenuRow(Bounds bounds, @Nullable Runnable action) {
    }

    /** Height of this row, for a host (e.g. {@code ItemEditorWindow}) that draws it itself, above its own divider, instead of as part of {@code ItemEditorPanel#render}'s body. */
    static final int HEIGHT = 11;
    private static final int LABEL_PADDING = 4;
    private static final int LABEL_GAP = 2;
    private static final int ITEM_HEIGHT = 12;
    private static final int SEPARATOR_HEIGHT = 5;
    private static final int MIN_WIDTH = 96;
    private static final String FILE_LABEL = "File";
    private static final String INFO_LABEL = "Info";
    private static final String SAVE_LABEL = "Save";
    private static final String REVERT_LABEL = "Revert";
    private static final String SAVED_CONFIRM_LABEL = "Saved ✓";
    private static final String REVERTED_CONFIRM_LABEL = "Reverted ✓";
    /** Gap between the right-aligned Save/Revert labels. */
    private static final int ACTION_LABEL_GAP = 4;
    /** How long Save/Revert show their confirmation label after a click, so a click that landed is never silently indistinguishable from one that didn't. */
    private static final long CONFIRM_DURATION_MS = 1100;
    private static final int CONFIRM_COLOR = 0xFF55FF55;

    private Bounds barBounds = new Bounds(0, 0, 0, 0);
    private Bounds fileLabelBounds = new Bounds(0, 0, 0, 0);
    private Bounds infoLabelBounds = new Bounds(0, 0, 0, 0);
    private Bounds saveLabelBounds = new Bounds(0, 0, 0, 0);
    private Bounds revertLabelBounds = new Bounds(0, 0, 0, 0);
    private boolean fileMenuOpen;
    private final List<MenuRow> fileMenuRows = new ArrayList<>();
    /** {@code System.currentTimeMillis()} deadline until Save shows {@link #SAVED_CONFIRM_LABEL} instead of {@link #SAVE_LABEL} — 0 while idle, so a click that landed is never silently indistinguishable from one that didn't. */
    private long saveConfirmUntilMs;
    /** Same as {@link #saveConfirmUntilMs}, for Revert. */
    private long revertConfirmUntilMs;

    /** Whether the File dropdown is currently open — a host (e.g. {@code ItemEditorPanel#render}) checks this to avoid drawing content the dropdown would otherwise paint over. */
    boolean isFileMenuOpen() {
        return fileMenuOpen;
    }

    /**
     * Draws the File/Info labels on the left and Save/Revert on the right of {@code bounds} — a
     * host-chosen row, deliberately outside {@code ItemEditorPanel#render}'s own bounds so it can
     * sit above a title divider instead of inside the scrollable body. The dropdown itself is drawn
     * separately by {@link #renderOverlay} — drawing it here, this early, had it painted *under* the
     * header/body content a host draws afterward, garbling both, since nothing after this call knew
     * to leave it alone.
     *
     * <p>Save/Revert dim (but still render, so the row never jumps around) when {@link
     * Host#actionsEnabled()} is false; while hovered (and enabled) they highlight the same way
     * File/Info do, so it's visually obvious they're clickable. For a beat after an actual click
     * lands, the label itself swaps to a green "Saved ✓"/"Reverted ✓" confirmation.
     */
    void render(RenderContext context, Bounds bounds, int mouseX, int mouseY, Host host) {
        barBounds = bounds;
        int labelX = barBounds.x();
        int fileWidth = context.textWidth(FILE_LABEL, OptionStyle.TEXT_SCALE) + 2 * LABEL_PADDING;
        fileLabelBounds = new Bounds(labelX, barBounds.y(), fileWidth, barBounds.height());
        labelX += fileWidth + LABEL_GAP;
        int infoWidth = context.textWidth(INFO_LABEL, OptionStyle.TEXT_SCALE) + 2 * LABEL_PADDING;
        infoLabelBounds = new Bounds(labelX, barBounds.y(), infoWidth, barBounds.height());
        drawMenuLabel(context, fileLabelBounds, FILE_LABEL, fileMenuOpen || fileLabelBounds.contains(mouseX, mouseY));
        drawMenuLabel(context, infoLabelBounds, INFO_LABEL, host.infoActive() || infoLabelBounds.contains(mouseX, mouseY));

        boolean actionsEnabled = host.actionsEnabled();
        long now = System.currentTimeMillis();
        boolean showSaveConfirm = now < saveConfirmUntilMs;
        boolean showRevertConfirm = now < revertConfirmUntilMs;
        String saveLabel = showSaveConfirm ? SAVED_CONFIRM_LABEL : SAVE_LABEL;
        String revertLabel = showRevertConfirm ? REVERTED_CONFIRM_LABEL : REVERT_LABEL;

        int revertWidth = context.textWidth(revertLabel, OptionStyle.TEXT_SCALE) + 2 * LABEL_PADDING;
        int saveWidth = context.textWidth(saveLabel, OptionStyle.TEXT_SCALE) + 2 * LABEL_PADDING;
        int rightX = barBounds.x() + barBounds.width();
        revertLabelBounds = new Bounds(rightX - revertWidth, barBounds.y(), revertWidth, barBounds.height());
        rightX = revertLabelBounds.x() - ACTION_LABEL_GAP;
        saveLabelBounds = new Bounds(rightX - saveWidth, barBounds.y(), saveWidth, barBounds.height());

        boolean saveHovered = actionsEnabled && !showSaveConfirm && saveLabelBounds.contains(mouseX, mouseY);
        boolean revertHovered = actionsEnabled && !showRevertConfirm && revertLabelBounds.contains(mouseX, mouseY);
        drawActionLabel(context, saveLabelBounds, saveLabel, actionsEnabled, showSaveConfirm, saveHovered);
        drawActionLabel(context, revertLabelBounds, revertLabel, actionsEnabled, showRevertConfirm, revertHovered);
    }

    /** Draws the File dropdown, if open, anchored under wherever {@link #render} last put the File label. The host must call this <em>last</em> — after its own header/body content — so the dropdown always paints on top instead of getting painted over. No-op while the dropdown is closed. */
    void renderOverlay(RenderContext context, Host host) {
        if (!fileMenuOpen) {
            return;
        }
        List<MenuEntry> entries = host.buildFileMenuEntries();
        int width = MIN_WIDTH;
        for (MenuEntry e : entries) {
            if (!e.isSeparator()) {
                width = Math.max(width, context.textWidth(e.label(), OptionStyle.TEXT_SCALE) + 2 * LABEL_PADDING + 4);
            }
        }
        int height = 0;
        for (MenuEntry e : entries) {
            height += e.isSeparator() ? SEPARATOR_HEIGHT : ITEM_HEIGHT;
        }

        int x = fileLabelBounds.x();
        int y = fileLabelBounds.y() + fileLabelBounds.height() + 1;
        int background = context.theme().color(ThemeKey.PANEL_BACKGROUND);
        int border = context.theme().color(ThemeKey.BORDER);
        context.drawRoundedRect(x, y, width, height, 1, background, border);

        fileMenuRows.clear();
        int rowY = y;
        for (MenuEntry e : entries) {
            if (e.isSeparator()) {
                context.fillRect(x + 2, rowY + SEPARATOR_HEIGHT / 2, width - 4, 1, border);
                fileMenuRows.add(new MenuRow(new Bounds(x, rowY, width, SEPARATOR_HEIGHT), null));
                rowY += SEPARATOR_HEIGHT;
            } else {
                int color = e.active() ? OptionStyle.ACCENT : context.theme().color(ThemeKey.TEXT_PRIMARY);
                context.drawText(e.label(), x + 3, rowY + (ITEM_HEIGHT - 7) / 2, color, OptionStyle.TEXT_SCALE);
                fileMenuRows.add(new MenuRow(new Bounds(x, rowY, width, ITEM_HEIGHT), e.action()));
                rowY += ITEM_HEIGHT;
            }
        }
    }

    /**
     * Handles a click on the row drawn by {@link #render} (including its File dropdown, which can
     * extend below the row a host gave that method) — called by the host ahead of its own other
     * hit-testing (e.g. a gear/Style button), the same way {@link #render} is drawn ahead of the
     * host's own divider. Returns false (not handled) only when the dropdown is closed and the click
     * missed both labels, so the host's other controls still get a chance at it.
     */
    boolean mouseClicked(double mouseX, double mouseY, int button, Host host) {
        if (fileMenuOpen) {
            fileMenuOpen = false;
            for (MenuRow row : fileMenuRows) {
                if (row.action() != null && row.bounds().contains((int) mouseX, (int) mouseY)) {
                    row.action().run();
                    break;
                }
            }
            return true;
        }
        if (button != 0) {
            return false;
        }
        if (fileLabelBounds.contains((int) mouseX, (int) mouseY)) {
            fileMenuOpen = true;
            return true;
        }
        if (infoLabelBounds.contains((int) mouseX, (int) mouseY)) {
            host.onInfoClicked();
            return true;
        }
        if (host.actionsEnabled() && saveLabelBounds.contains((int) mouseX, (int) mouseY)) {
            host.onSaveClicked();
            saveConfirmUntilMs = System.currentTimeMillis() + CONFIRM_DURATION_MS;
            return true;
        }
        if (host.actionsEnabled() && revertLabelBounds.contains((int) mouseX, (int) mouseY)) {
            host.onRevertClicked();
            revertConfirmUntilMs = System.currentTimeMillis() + CONFIRM_DURATION_MS;
            return true;
        }
        return false;
    }

    /** Small, auto-sized menu-bar label (left-aligned, not a stretched tab) — highlighted while its menu/view is open or selected. */
    private static void drawMenuLabel(RenderContext context, Bounds bounds, String label, boolean active) {
        if (active) {
            context.fillRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), OptionStyle.dimmed(OptionStyle.ACCENT));
        }
        int color = active ? OptionStyle.ACCENT : context.theme().color(ThemeKey.TEXT_SECONDARY);
        context.drawText(label, bounds.x() + LABEL_PADDING, bounds.y() + (bounds.height() - 7) / 2, color, OptionStyle.TEXT_SCALE);
    }

    /**
     * Right-aligned Save/Revert label: dimmed while {@code enabled} is false, highlighted the same
     * way File/Info are while {@code hovered}, and shown in {@link #CONFIRM_COLOR} without a hover
     * highlight while {@code confirming} (it already reads "Saved ✓"/"Reverted ✓" by that point, so
     * there's nothing left to invite another click).
     */
    private static void drawActionLabel(RenderContext context, Bounds bounds, String label, boolean enabled, boolean confirming, boolean hovered) {
        if (hovered) {
            context.fillRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), OptionStyle.dimmed(OptionStyle.ACCENT));
        }
        int color = confirming ? CONFIRM_COLOR
                : enabled ? OptionStyle.ACCENT : OptionStyle.dimmed(context.theme().color(ThemeKey.TEXT_SECONDARY));
        context.drawText(label, bounds.x() + LABEL_PADDING, bounds.y() + (bounds.height() - 7) / 2, color, OptionStyle.TEXT_SCALE);
    }
}
