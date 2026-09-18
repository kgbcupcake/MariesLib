package dev.marie.framework.ui;

import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.edit.DraggableResizable;
import dev.marie.framework.ui.geometry.Bounds;
import net.minecraft.world.item.ItemStack;

/**
 * Everything a {@link MarieComponent} needs to draw itself: frame/screen metadata, the active
 * {@link Theme}, and generic drawing primitives.
 *
 * <p>No Minecraft <em>rendering-pipeline</em> types (e.g. {@code GuiGraphics}, {@code PoseStack})
 * leak into this contract — concrete implementations (e.g. a GuiGraphics-backed one) live outside
 * dev.marie.framework.ui. Domain types intrinsic to what's being drawn, such as {@link ItemStack},
 * are unavoidable for a Minecraft-native UI framework and are accepted as parameters where a
 * primitive genuinely requires them.
 */
public interface RenderContext {

    int screenWidth();

    int screenHeight();

    float partialTick();

    Theme theme();

    void fillRect(int x, int y, int width, int height, int argbColor);

    void drawBorder(int x, int y, int width, int height, int thickness, int argbColor);

    void drawDashedBorder(int x, int y, int width, int height, int argbColor);

    /**
     * Draws a soft highlight ring around a rectangle — distinct from {@link #drawBorder}'s flat
     * single-pixel border, this marks a component as draggable/movable without implying a resize
     * handle.
     */
    void drawGlow(int x, int y, int width, int height, int argbColor);

    /**
     * Fills and borders a rectangle with 1px diagonal-notched corners — the classic pixel-art
     * "rounded rect" fake used throughout Minecraft GUIs: the outermost corner pixel is left
     * untouched (transparent) and the pixel diagonally inset from it is drawn in
     * {@code borderColor}, producing an octagonal cut instead of a hard square corner. Ported
     * from Nourished's pre-MarieUI {@code DietScreen#drawRoundedPanel}, collapsed to a single
     * border color — that legacy version used separate light-top/dark-bottom bevel shades, which
     * none of marie-ui's current callers need. Geometry is exact for {@code thickness == 1} (the
     * only value any caller uses today); other thicknesses scale the corner inset by
     * {@code thickness + 1} rather than a fixed 1px notch.
     */
    default void drawRoundedRect(int x, int y, int width, int height, int thickness, int fillColor, int borderColor) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int x2 = x + width;
        int y2 = y + height;
        int inset = thickness + 1;
        int innerW = Math.max(0, width - 2 * inset);
        int innerH = Math.max(0, height - 2 * inset);

        fillRect(x + inset, y, innerW, height, fillColor);
        fillRect(x, y + inset, width, innerH, fillColor);

        fillRect(x + inset, y, innerW, thickness, borderColor);
        fillRect(x + inset, y2 - thickness, innerW, thickness, borderColor);
        fillRect(x, y + inset, thickness, innerH, borderColor);
        fillRect(x2 - thickness, y + inset, thickness, innerH, borderColor);

        fillRect(x + thickness, y + thickness, 1, 1, borderColor);
        fillRect(x2 - thickness - 1, y + thickness, 1, 1, borderColor);
        fillRect(x + thickness, y2 - thickness - 1, 1, 1, borderColor);
        fillRect(x2 - thickness - 1, y2 - thickness - 1, 1, 1, borderColor);
    }

    void drawText(String text, int x, int y, int argbColor, float scale);

    /** Rendered pixel width of {@code text} at {@code scale}, for centering — no font access otherwise leaks into this contract. */
    int textWidth(String text, float scale);

    void drawBar(int x, int y, int width, int height, float fillPct, int backgroundColor, int fillColor);

    void drawVerticalBar(int x, int y, int width, int height, float fillPct, int backgroundColor, int fillColor);

    /**
     * Draws an already-resolved {@link ItemStack} as a scaled icon at (x, y). Callers are
     * responsible for resolving whatever domain key (e.g. a Nourished nutrient key) to an
     * {@code ItemStack} themselves — this primitive only knows how to draw one.
     */
    void drawItem(ItemStack stack, int x, int y, float scale);

    /**
     * Pushes a rectangular scissor/clip region — nothing drawn between this call and the matching
     * {@link #popClip()} renders outside {@code (x, y, width, height)}, intersected with any
     * currently active clip (nested clips shrink, they never widen back out past a parent's own
     * region). Lets a component draw its content at a fixed, natural size unconditionally and let the
     * box's own live {@link dev.marie.framework.ui.geometry.Bounds} determine how much of that fixed
     * content is actually visible, instead of the component computing its own fit-check and skipping
     * draw calls outright (a resize that hides content is very different from a resize that reveals/
     * hides more of a fixed scene through a window — this is a window, not a fit-check).
     *
     * <p>Callers MUST pair every {@code pushClip} with exactly one {@link #popClip()}, including
     * across early returns — wrap the clipped region in try/finally.
     */
    void pushClip(int x, int y, int width, int height);

    /** Pops the most recent {@link #pushClip} region, restoring whatever clip (if any) was active before it. */
    void popClip();

    /**
     * Draws a resize-handle square at (x, y) — the handle's own top-left corner, e.g. from
     * {@link DraggableResizable#handleBounds(Bounds)} — with a corner glyph, colored via
     * {@link ThemeKey#HANDLE_ACTIVE}/{@link ThemeKey#HANDLE_HOVER}/{@link ThemeKey#HANDLE_BACKGROUND}.
     * Ported from Nourished's {@code HudDrawHelpers#drawResizeHandle} geometry, minus the "Drag to
     * resize" tooltip — that's presentation text a caller can add from its own render() if wanted.
     */
    default void drawResizeHandle(int x, int y, boolean hovered, boolean active) {
        int size = DraggableResizable.RESIZE_HANDLE_SIZE;
        int handleColor = active
                ? theme().color(ThemeKey.HANDLE_ACTIVE)
                : hovered ? theme().color(ThemeKey.HANDLE_HOVER) : theme().color(ThemeKey.HANDLE_BACKGROUND);
        fillRect(x, y, size, size, handleColor);
        drawText("◢", x + 1, y, 0xFF101010, 1f);
    }

    /**
     * Length, in pixels, of the cursor-localized indicator {@link #drawEdgeHandle} paints along an
     * edge — deliberately much shorter than the full hit-region ({@link
     * DraggableResizable#edgeHandleBounds(Bounds, DraggableResizable.Edge)} spans the box's entire
     * width/height, since a large hit-region is good for usability), so hovering/dragging one edge
     * reads as a small localized handle near the cursor rather than the box's whole side lighting up.
     */
    int EDGE_INDICATOR_LENGTH = 16;

    /**
     * Draws a small cursor-localized indicator along one edge — NOT the full {@code (x, y, width,
     * height)} hit-region strip (that stays large, per {@link
     * DraggableResizable#edgeHandleBounds(Bounds, DraggableResizable.Edge)}, purely for hit-testing).
     * The indicator is centered on {@code (mx, my)}'s position along the strip's long axis, clamped
     * so it never spills outside the strip, then colored via the same {@link ThemeKey#HANDLE_ACTIVE}/
     * {@link ThemeKey#HANDLE_HOVER} keys as {@link #drawResizeHandle}. No glyph: {@link
     * DraggableResizable#EDGE_HANDLE_THICKNESS} is too thin for a corner-style glyph to read legibly.
     */
    default void drawEdgeHandle(int x, int y, int width, int height, int mx, int my, boolean hovered, boolean active) {
        if (!hovered && !active) {
            return;
        }
        int handleColor = active ? theme().color(ThemeKey.HANDLE_ACTIVE) : theme().color(ThemeKey.HANDLE_HOVER);
        if (width >= height) {
            int indicatorW = Math.min(EDGE_INDICATOR_LENGTH, width);
            int ix = Math.max(x, Math.min(mx - indicatorW / 2, x + width - indicatorW));
            fillRect(ix, y, indicatorW, height, handleColor);
        } else {
            int indicatorH = Math.min(EDGE_INDICATOR_LENGTH, height);
            int iy = Math.max(y, Math.min(my - indicatorH / 2, y + height - indicatorH));
            fillRect(x, iy, width, indicatorH, handleColor);
        }
    }

    /** Title baseline y, local to the box top — ported from Nourished's {@code DietPanelContainer} title row. */
    int WINDOW_CHROME_TITLE_TEXT_Y = 9;

    /** Divider y, local to the box top, i.e. the title row's height — ported from {@code DietPanelContainer}'s {@code dividerTop} local y. */
    int WINDOW_CHROME_TITLE_ROW_HEIGHT = 26;

    /** Divider line color — ported from {@code DietPanelContainer.COL_DIVIDER}. */
    int WINDOW_CHROME_DIVIDER_COLOR = 0xFF2E2E2E;

    /** Draws a {@code DietPanelContainer}-style window frame — filled/bordered rounded rect, centered title, divider beneath it — and returns the content {@link Bounds} below the divider. */
    default Bounds drawWindowChrome(int x, int y, int width, int height, String title, int titleColor) {
        drawRoundedRect(x, y, width, height, 1, theme().color(ThemeKey.PANEL_BACKGROUND), theme().color(ThemeKey.BORDER));
        int titleX = x + (width - textWidth(title, 1f)) / 2;
        drawText(title, titleX, y + WINDOW_CHROME_TITLE_TEXT_Y, titleColor, 1f);
        int dividerY = y + WINDOW_CHROME_TITLE_ROW_HEIGHT;
        fillRect(x + 1, dividerY, Math.max(0, width - 2), 1, WINDOW_CHROME_DIVIDER_COLOR);
        return new Bounds(x, dividerY + 1, width, Math.max(0, height - WINDOW_CHROME_TITLE_ROW_HEIGHT - 1));
    }
}
