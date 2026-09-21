package dev.marie.framework.ui.widget;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.component.Constraint;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * A scrollable, word-wrapped list of text lines — usable as an output console (append lines, it
 * follows the tail until the user scrolls up) or as a plain read-only list.
 *
 * <p>Wrapping is cached per width, so a steady-state frame does no string measuring.
 */
@ApiStatus.Experimental
public final class MarieTextList implements MarieComponent {

    public static final int LINE_HEIGHT = 10;
    private static final int SCROLLBAR_WIDTH = 3;

    private final String id;
    private final int maxLines;
    private final List<String> lines = new ArrayList<>();
    private int textColor = 0xFFE0E0E0;
    private int scrollRow;
    private boolean followTail = true;

    private List<String> wrapped = List.of();
    private int wrappedWidth = -1;
    private Bounds lastBounds = new Bounds(0, 0, 0, 0);

    /** @param maxLines oldest lines are dropped beyond this many source lines */
    public MarieTextList(String id, int maxLines) {
        this.id = id;
        this.maxLines = Math.max(1, maxLines);
    }

    public MarieTextList withTextColor(int argb) {
        this.textColor = argb;
        return this;
    }

    public void add(String line) {
        lines.add(line);
        while (lines.size() > maxLines) {
            lines.remove(0);
        }
        wrappedWidth = -1;
    }

    public void addAll(Iterable<String> newLines) {
        newLines.forEach(this::add);
    }

    public void setLines(List<String> newLines) {
        lines.clear();
        addAll(newLines);
        scrollRow = 0;
        followTail = false;
    }

    public void clear() {
        lines.clear();
        wrappedWidth = -1;
        scrollRow = 0;
        followTail = true;
    }

    public int lineCount() {
        return lines.size();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Constraint constraint() {
        return Constraint.preferred(200, 100);
    }

    @Override
    public void render(RenderContext ctx, Bounds bounds) {
        lastBounds = bounds;
        int textWidth = Math.max(1, bounds.width() - SCROLLBAR_WIDTH - 2);
        rewrap(ctx, textWidth);

        int visibleRows = Math.max(1, bounds.height() / LINE_HEIGHT);
        int maxScroll = Math.max(0, wrapped.size() - visibleRows);
        if (followTail) {
            scrollRow = maxScroll;
        }
        scrollRow = Math.min(Math.max(scrollRow, 0), maxScroll);

        ctx.pushClip(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        try {
            int end = Math.min(wrapped.size(), scrollRow + visibleRows);
            for (int row = scrollRow; row < end; row++) {
                ctx.drawText(wrapped.get(row), bounds.x() + 1, bounds.y() + (row - scrollRow) * LINE_HEIGHT, textColor, 1f);
            }
            if (maxScroll > 0) {
                int trackH = bounds.height();
                int thumbH = Math.max(6, trackH * visibleRows / wrapped.size());
                int thumbY = bounds.y() + (trackH - thumbH) * scrollRow / maxScroll;
                int x = bounds.x() + bounds.width() - SCROLLBAR_WIDTH;
                ctx.fillRect(x, bounds.y(), SCROLLBAR_WIDTH, trackH, 0x40FFFFFF);
                ctx.fillRect(x, thumbY, SCROLLBAR_WIDTH, thumbH, 0xC0FFFFFF);
            }
        } finally {
            ctx.popClip();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!lastBounds.contains((int) mouseX, (int) mouseY)) {
            return false;
        }
        int visibleRows = Math.max(1, lastBounds.height() / LINE_HEIGHT);
        int maxScroll = Math.max(0, wrapped.size() - visibleRows);
        scrollRow = Math.min(Math.max(scrollRow - (int) Math.signum(scrollY) * 3, 0), maxScroll);
        followTail = scrollRow >= maxScroll;
        return true;
    }

    private void rewrap(RenderContext ctx, int width) {
        if (width == wrappedWidth) {
            return;
        }
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            out.addAll(wrap(line, width, s -> ctx.textWidth(s, 1f)));
        }
        wrapped = out;
        wrappedWidth = width;
    }

    /**
     * Greedy word wrap. A single word wider than {@code maxWidth} is split by character, so no
     * output line exceeds the width unless one character alone does. An empty input yields one
     * empty line (blank lines are preserved).
     */
    static List<String> wrap(String text, int maxWidth, ToIntFunction<String> measure) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ", -1)) {
            String candidate = cur.isEmpty() ? word : cur + " " + word;
            if (measure.applyAsInt(candidate) <= maxWidth) {
                cur.setLength(0);
                cur.append(candidate);
                continue;
            }
            if (!cur.isEmpty()) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            for (char c : word.toCharArray()) {
                if (!cur.isEmpty() && measure.applyAsInt(cur.toString() + c) > maxWidth) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }
}
