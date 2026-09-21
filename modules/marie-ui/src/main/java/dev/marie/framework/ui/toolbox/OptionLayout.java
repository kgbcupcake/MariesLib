package dev.marie.framework.ui.toolbox;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.component.Constraint;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.geometry.Bounds;
import dev.marie.framework.ui.toolbox.colorpicker.ColorSlot;
import dev.marie.framework.ui.toolbox.colorpicker.ColorSlotRow;

import java.util.ArrayList;
import java.util.List;

/**
 * The panel body a module box hosts: a {@link TabRow} (only when there is more than one tab) above
 * the selected tab's rows, stacked top to bottom. Its preferred height is the tallest tab's, so a
 * host that sizes to it never resizes when the selection changes. If a host gives it less room than
 * the selected tab needs, the rows clip and scroll with the mouse wheel (a thin scroll thumb shows
 * the position); while the rows fit, the wheel nudges the slider under the cursor instead.
 *
 * <p>Draws no window chrome or title of its own — the host owns that; the panel title is only this
 * component's {@link #id()}.
 */
@ApiStatus.Internal
public final class OptionLayout implements MarieComponent {

    private static final int SCROLL_STEP = 10;
    /** Width of the scroll track, reserved beside the rows while they overflow so the thumb never covers text. */
    private static final int SCROLL_GUTTER = 5;
    private static final int SCROLL_TRACK_WIDTH = 3;

    private final String id;
    private final TabRow tabRow = new TabRow();
    private final List<List<OptionRow>> tabs = new ArrayList<>();

    private Bounds rowArea = new Bounds(0, 0, 0, 0);
    private int scroll;
    /** While non-null, {@link #addRow} files rows into this section instead of the tab (see {@link #openSection}). */
    private SectionRow openSection;
    private java.util.function.Consumer<ColorSlot> colorSlotListener = slot -> {};

    public OptionLayout(String id) {
        this.id = id;
    }

    public void addTab(String title) {
        openSection = null;
        tabRow.addTab(title);
        tabs.add(new ArrayList<>());
    }

    /** Adds {@code row} to the most recently added tab. */
    public void addRow(OptionRow row) {
        if (tabs.isEmpty()) {
            throw new IllegalStateException("addTab must be called before addRow");
        }
        if (openSection != null) {
            openSection.add(row);
        } else {
            tabs.get(tabs.size() - 1).add(row);
        }
    }

    /** Starts (or resumes, if {@code title} already heads one on this tab) a collapsible group; rows added until {@link #closeSection} go inside it. */
    public void openSection(String title) {
        openSection = section(title, title);
    }

    public void closeSection() {
        openSection = null;
    }

    /** The section {@code key} on the most recently added tab, created (and appended) on first use. */
    public SectionRow section(String key, String title) {
        for (OptionRow row : currentTabRows()) {
            if (row instanceof SectionRow section && section.key().equals(key)) {
                return section;
            }
        }
        SectionRow section = new SectionRow(key, title);
        currentTabRows().add(section);
        return section;
    }

    /** Applies {@code enabled} to the most recently added row. */
    public void enabledWhenLast(java.util.function.BooleanSupplier enabled) {
        List<OptionRow> rows = tabs.isEmpty() ? List.of() : tabs.get(tabs.size() - 1);
        OptionRow last = openSection != null ? openSection.last() : rows.isEmpty() ? null : rows.get(rows.size() - 1);
        if (last == null) {
            throw new IllegalStateException("no option to attach enabledWhen to");
        }
        last.enabledWhen(enabled);
    }

    /** The live row list of the most recently added tab (for a reset button that acts on its own tab). */
    public List<OptionRow> currentTabRows() {
        if (tabs.isEmpty()) {
            throw new IllegalStateException("addTab must be called first");
        }
        return tabs.get(tabs.size() - 1);
    }

    /** Sets the reset value of the most recently added slider. */
    public void defaultToLast(double value) {
        lastRow().defaultTo(value);
    }

    public void defaultToLast(boolean value) {
        lastRow().defaultTo(value);
    }

    public void defaultToLast(int index) {
        lastRow().defaultTo(index);
    }

    private OptionRow lastRow() {
        List<OptionRow> rows = currentTabRows();
        OptionRow last = openSection != null ? openSection.last() : rows.isEmpty() ? null : rows.get(rows.size() - 1);
        if (last == null) {
            throw new IllegalStateException("no option to set a default on");
        }
        return last;
    }

    /** Adds a swatch row for {@code slot} to the most recently added tab; a click on it reports the slot to {@link #setColorSlotListener}'s listener. */
    public void addColorSlot(ColorSlot slot) {
        addRow(new ColorSlotRow(slot, clicked -> colorSlotListener.accept(clicked)));
    }

    /** The host's "slot clicked" callback (it decides what editing a slot means, e.g. opening a picker window). Until one is set, a click does nothing. */
    public void setColorSlotListener(java.util.function.Consumer<ColorSlot> listener) {
        this.colorSlotListener = listener;
    }

    public int tabCount() {
        return tabs.size();
    }

    @Override
    public String id() {
        return id;
    }

    /** Preferred size: {@link OptionStyle#PREFERRED_WIDTH} by tab row plus the tallest tab. */
    @Override
    public Constraint constraint() {
        int tallest = 0;
        for (List<OptionRow> rows : tabs) {
            tallest = Math.max(tallest, stackHeight(rows));
        }
        return Constraint.preferred(OptionStyle.PREFERRED_WIDTH, tabStripHeight() + tallest + 2 * OptionStyle.PANEL_PAD);
    }

    @Override
    public void render(RenderContext context, Bounds bounds) {
        int top = bounds.y();
        if (tabs.size() > 1) {
            tabRow.render(context, new Bounds(bounds.x(), top, bounds.width(), OptionStyle.TAB_HEIGHT));
            top += tabStripHeight();
        }
        rowArea = new Bounds(bounds.x(), top, bounds.width(), Math.max(0, bounds.y() + bounds.height() - top));
        List<OptionRow> rows = selectedRows();
        scroll = Math.min(scroll, maxScroll(rows));
        int pad = OptionStyle.PANEL_PAD;
        int rowWidth = Math.max(0, rowArea.width() - 2 * pad - (maxScroll(rows) > 0 ? SCROLL_GUTTER : 0));

        context.pushClip(rowArea.x(), rowArea.y(), rowArea.width(), rowArea.height());
        try {
            int y = rowArea.y() + pad - scroll;
            for (OptionRow row : rows) {
                row.render(context, new Bounds(rowArea.x() + pad, y, rowWidth, row.height()));
                y += row.height() + OptionStyle.ROW_GAP;
            }
        } finally {
            context.popClip();
        }
        drawScrollThumb(context, rows);
    }

    /** Outlined track and thumb in the gutter on the right edge of the row area, only while the rows overflow it. */
    private void drawScrollThumb(RenderContext context, List<OptionRow> rows) {
        int max = maxScroll(rows);
        if (max <= 0 || rowArea.height() <= 0) {
            return;
        }
        int total = stackHeight(rows);
        int trackX = rowArea.x() + rowArea.width() - SCROLL_TRACK_WIDTH - 2;
        int trackY = rowArea.y() + 2;
        int trackH = rowArea.height() - 4;
        int thumbH = Math.max(6, trackH * (rowArea.height() - 2 * OptionStyle.PANEL_PAD) / total);
        int thumbY = trackY + (trackH - thumbH) * scroll / max;
        context.fillRect(trackX, trackY, SCROLL_TRACK_WIDTH, trackH, OptionStyle.dimmed(OptionStyle.ACCENT));
        context.drawBorder(trackX, trackY, SCROLL_TRACK_WIDTH, trackH, 1, OptionStyle.ACCENT);
        context.fillRect(trackX, thumbY, SCROLL_TRACK_WIDTH, thumbH, OptionStyle.ACCENT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (tabs.size() > 1 && tabRow.mouseClicked(mouseX, mouseY)) {
            scroll = 0;
            return true;
        }
        if (!rowArea.contains((int) mouseX, (int) mouseY)) {
            return false;
        }
        for (OptionRow row : selectedRows()) {
            if (row.mouseClicked(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        for (OptionRow row : selectedRows()) {
            if (row.mouseDragged(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (OptionRow row : selectedRows()) {
            if (row.mouseReleased(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!rowArea.contains((int) mouseX, (int) mouseY)) {
            return false;
        }
        List<OptionRow> rows = selectedRows();
        int max = maxScroll(rows);
        if (max > 0) {
            // Overflowing: the wheel scrolls, everywhere — otherwise a slider under the cursor would
            // swallow it and the rows beneath could never be reached.
            if (scrollY != 0) {
                scroll = Math.max(0, Math.min(max, scroll + (scrollY > 0 ? -SCROLL_STEP : SCROLL_STEP)));
            }
            return true;
        }
        for (OptionRow row : rows) {
            if (row.mouseScrolled(mouseX, mouseY, scrollY)) {
                return true;
            }
        }
        return false;
    }

    private List<OptionRow> selectedRows() {
        return tabs.isEmpty() ? List.of() : tabs.get(Math.min(tabRow.selected(), tabs.size() - 1));
    }

    private int tabStripHeight() {
        return tabs.size() > 1 ? OptionStyle.TAB_HEIGHT + OptionStyle.ROW_GAP : 0;
    }

    private int maxScroll(List<OptionRow> rows) {
        return Math.max(0, stackHeight(rows) - (rowArea.height() - 2 * OptionStyle.PANEL_PAD));
    }

    private static int stackHeight(List<OptionRow> rows) {
        int total = 0;
        for (OptionRow row : rows) {
            total += row.height();
        }
        return total + Math.max(0, rows.size() - 1) * OptionStyle.ROW_GAP;
    }
}
