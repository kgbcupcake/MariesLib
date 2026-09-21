package dev.marie.framework.ui.widget;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.component.Constraint;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/** A scrollable single-select list of labels; a click selects a row and reports its index. */
@ApiStatus.Experimental
public final class MarieListPicker implements MarieComponent {

    public static final int ROW_HEIGHT = 12;

    private final String id;
    private final IntConsumer onSelect;
    private final List<String> items = new ArrayList<>();
    private int selected = -1;
    private int scrollRow;
    private Bounds lastBounds = new Bounds(0, 0, 0, 0);

    public MarieListPicker(String id, IntConsumer onSelect) {
        this.id = id;
        this.onSelect = onSelect;
    }

    public void setItems(List<String> labels) {
        items.clear();
        items.addAll(labels);
        selected = -1;
        scrollRow = 0;
    }

    /** Index of the selected row, or -1 for none. */
    public int selectedIndex() {
        return selected;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Constraint constraint() {
        return Constraint.preferred(160, 96);
    }

    @Override
    public void render(RenderContext ctx, Bounds bounds) {
        lastBounds = bounds;
        int visibleRows = Math.max(1, bounds.height() / ROW_HEIGHT);
        scrollRow = Math.min(Math.max(scrollRow, 0), Math.max(0, items.size() - visibleRows));

        ctx.pushClip(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        try {
            int end = Math.min(items.size(), scrollRow + visibleRows);
            for (int i = scrollRow; i < end; i++) {
                int y = bounds.y() + (i - scrollRow) * ROW_HEIGHT;
                if (i == selected) {
                    ctx.fillRect(bounds.x(), y, bounds.width(), ROW_HEIGHT, 0x6033AACC);
                }
                ctx.drawText(items.get(i), bounds.x() + 3, y + 2, 0xFFE0E0E0, 1f);
            }
        } finally {
            ctx.popClip();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !lastBounds.contains((int) mouseX, (int) mouseY)) {
            return false;
        }
        int row = scrollRow + ((int) mouseY - lastBounds.y()) / ROW_HEIGHT;
        if (row < 0 || row >= items.size()) {
            return false;
        }
        selected = row;
        onSelect.accept(row);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!lastBounds.contains((int) mouseX, (int) mouseY)) {
            return false;
        }
        scrollRow -= (int) Math.signum(scrollY);
        return true;
    }
}
