package dev.marie.framework.ui.toolbox;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.ThemeKey;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A collapsible group of rows: a header (arrow, title, row count) that a click folds or unfolds, and the
 * child rows indented beneath it while open. Starts collapsed so a tab with several groups reads as a short
 * list of headings. Its height follows its state, so the hosting {@link OptionLayout} re-measures every frame.
 * The open/closed state lives in memory only. Collapsed children get no input.
 */
@ApiStatus.Internal
public final class SectionRow implements OptionRow {

    private static final int HEADER_HEIGHT = 12;
    private static final int INDENT = 6;
    private static final int ARROW = 5;

    private final String key;
    private final String title;
    private final List<OptionRow> children = new ArrayList<>();
    private boolean open;
    private Bounds header = new Bounds(0, 0, 0, 0);

    /** {@code key} identifies the section within a tab (see {@link OptionLayout#section}); {@code title} is the localized heading. */
    public SectionRow(String key, String title) {
        this.key = key;
        this.title = title;
    }

    public String key() {
        return key;
    }

    public void add(OptionRow row) {
        children.add(row);
    }

    /** The most recently added child, or null if none. */
    OptionRow last() {
        return children.isEmpty() ? null : children.get(children.size() - 1);
    }

    @Override
    public int height() {
        if (!open || children.isEmpty()) {
            return HEADER_HEIGHT;
        }
        int total = HEADER_HEIGHT + OptionStyle.ROW_GAP;
        for (OptionRow row : children) {
            total += row.height();
        }
        return total + (children.size() - 1) * OptionStyle.ROW_GAP;
    }

    /** The heading has no state of its own to grey out; each child keeps its own {@code enabledWhen}. */
    @Override
    public void enabledWhen(BooleanSupplier enabled) {}

    @Override
    public void resetToDefault() {
        for (OptionRow row : children) {
            row.resetToDefault();
        }
    }

    @Override
    public void render(RenderContext context, Bounds bounds) {
        header = new Bounds(bounds.x(), bounds.y(), bounds.width(), HEADER_HEIGHT);
        int border = context.theme().color(ThemeKey.BORDER);
        context.drawRoundedRect(header.x(), header.y(), header.width(), header.height(), 1,
                open ? (0x24 << 24) | (OptionStyle.ACCENT & 0x00FFFFFF) : 0x14FFFFFF, open ? OptionStyle.ACCENT : border);
        int arrowColor = open ? OptionStyle.ACCENT : OptionStyle.labelColor(context, true);
        drawArrow(context, header.x() + 4, header.y() + (HEADER_HEIGHT - ARROW) / 2, arrowColor);
        int textX = header.x() + 4 + ARROW + 4;
        String count = String.valueOf(children.size());
        OptionStyle.drawLabelAndValue(context, title, count, textX, header.y() + 2, header.width() - (textX - header.x()) - 4,
                open ? OptionStyle.ACCENT : OptionStyle.labelColor(context, true), OptionStyle.labelColor(context, false));
        if (!open) {
            return;
        }
        int y = bounds.y() + HEADER_HEIGHT + OptionStyle.ROW_GAP;
        int x = bounds.x() + INDENT;
        int width = Math.max(0, bounds.width() - INDENT);
        for (OptionRow row : children) {
            row.render(context, new Bounds(x, y, width, row.height()));
            y += row.height() + OptionStyle.ROW_GAP;
        }
    }

    /** A {@value #ARROW}px triangle from fills: pointing down while open, right while closed. */
    private void drawArrow(RenderContext context, int x, int y, int color) {
        for (int i = 0; i < ARROW; i++) {
            int span = ARROW - i * 2;
            if (span <= 0) {
                break;
            }
            if (open) {
                context.fillRect(x + i, y + i, span, 1, color);
            } else {
                context.fillRect(x + i, y + i, 1, span, color);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY) {
        if (header.contains((int) mouseX, (int) mouseY)) {
            open = !open;
            return true;
        }
        if (open) {
            for (OptionRow row : children) {
                if (row.mouseClicked(mouseX, mouseY)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY) {
        if (open) {
            for (OptionRow row : children) {
                if (row.mouseDragged(mouseX, mouseY)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY) {
        if (open) {
            for (OptionRow row : children) {
                if (row.mouseReleased(mouseX, mouseY)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (open) {
            for (OptionRow row : children) {
                if (row.mouseScrolled(mouseX, mouseY, scrollY)) {
                    return true;
                }
            }
        }
        return false;
    }
}
