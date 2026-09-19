package dev.marie.framework.ui.toolbox;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.ThemeKey;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.ArrayList;
import java.util.List;

/**
 * Strip of equal-width tab buttons. Owns only which tab is selected. Each label is truncated with
 * an ellipsis to fit its tab, so a narrow box never overflows the strip.
 */
@ApiStatus.Internal
public final class TabRow {

    private static final int LABEL_PADDING = 3;

    private final List<String> titles = new ArrayList<>();
    private final List<Bounds> lastBounds = new ArrayList<>();
    private int selected;

    public void addTab(String title) {
        titles.add(title);
    }

    public int selected() {
        return selected;
    }

    public void render(RenderContext context, Bounds bounds) {
        lastBounds.clear();
        int count = titles.size();
        for (int i = 0; i < count; i++) {
            int x0 = bounds.x() + bounds.width() * i / count;
            int x1 = bounds.x() + bounds.width() * (i + 1) / count;
            Bounds tab = new Bounds(x0, bounds.y(), Math.max(0, x1 - x0 - 1), bounds.height());
            lastBounds.add(tab);
            boolean active = i == selected;
            int border = active ? OptionStyle.ACCENT : context.theme().color(ThemeKey.BORDER);
            int fill = active ? (0x40 << 24) | (OptionStyle.ACCENT & 0x00FFFFFF) : context.theme().color(ThemeKey.PANEL_BACKGROUND);
            context.drawRoundedRect(tab.x(), tab.y(), tab.width(), tab.height(), 1, fill, border);
            String text = OptionStyle.fit(context, titles.get(i), OptionStyle.TEXT_SCALE, tab.width() - 2 * LABEL_PADDING);
            int textX = tab.x() + (tab.width() - context.textWidth(text, OptionStyle.TEXT_SCALE)) / 2;
            context.drawText(text, textX, tab.y() + (tab.height() - 7) / 2,
                    active ? OptionStyle.ACCENT : context.theme().color(ThemeKey.TEXT_SECONDARY), OptionStyle.TEXT_SCALE);
        }
    }

    /** Selects the tab under the cursor; true if one was hit. */
    public boolean mouseClicked(double mouseX, double mouseY) {
        for (int i = 0; i < lastBounds.size(); i++) {
            if (lastBounds.get(i).contains((int) mouseX, (int) mouseY)) {
                selected = i;
                return true;
            }
        }
        return false;
    }
}
