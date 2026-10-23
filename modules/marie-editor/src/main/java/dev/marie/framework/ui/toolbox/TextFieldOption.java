package dev.marie.framework.ui.toolbox;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.ThemeKey;
import dev.marie.framework.ui.geometry.Bounds;
import org.lwjgl.glfw.GLFW;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Single-line editable text row: a label on the left and a bordered field on the right, click to
 * focus, type to edit. The value commits live on every keystroke (the setter runs immediately,
 * same as {@link ToggleOption}/{@link CycleOption}), so there is nothing to "submit" — focus just
 * tracks where typed characters currently go.
 *
 * <p>Requires its host chain to actually forward {@link #charTyped}/{@link #keyPressed} down to
 * {@link OptionLayout} (see {@code OptionLayout#charTyped}/{@code OptionLayout#keyPressed}) — a
 * host that never calls those (e.g. one that only forwards {@code mouseClicked}/{@code
 * mouseDragged}/{@code mouseReleased}/{@code mouseScrolled}) will let this row be clicked into
 * focus but never receive a single typed character.
 */
@ApiStatus.Internal
public final class TextFieldOption implements OptionRow {

    private static final int HEIGHT = 12;
    private static final int FIELD_WIDTH_RATIO_PERCENT = 60;
    private static final int GAP = 4;
    private static final long CURSOR_BLINK_MS = 500;

    private final String label;
    private final Supplier<String> getter;
    private final Consumer<String> setter;
    private final Runnable onCommit;
    private final int maxLength;
    private String hint = "";
    private BooleanSupplier enabled = () -> true;

    private boolean focused;
    private int cursor;
    private Bounds bounds = new Bounds(0, 0, 0, 0);
    private Bounds fieldBounds = new Bounds(0, 0, 0, 0);

    public TextFieldOption(String label, Supplier<String> getter, Consumer<String> setter, Runnable onCommit) {
        this(label, getter, setter, onCommit, 256);
    }

    public TextFieldOption(String label, Supplier<String> getter, Consumer<String> setter, Runnable onCommit, int maxLength) {
        this.label = label;
        this.getter = getter;
        this.setter = setter;
        this.onCommit = onCommit;
        this.maxLength = maxLength;
    }

    /** Placeholder text drawn (dimmed) when the field's current value is empty and unfocused. */
    public TextFieldOption hint(String hint) {
        this.hint = hint;
        return this;
    }

    @Override
    public int height() {
        return HEIGHT;
    }

    @Override
    public void enabledWhen(BooleanSupplier enabled) {
        this.enabled = enabled;
    }

    @Override
    public void render(RenderContext context, Bounds bounds) {
        this.bounds = bounds;
        boolean on = enabled.getAsBoolean();
        String value = getter.get();
        if (value == null) {
            value = "";
        }
        cursor = Math.max(0, Math.min(cursor, value.length()));

        int fieldWidth = Math.max(40, bounds.width() * FIELD_WIDTH_RATIO_PERCENT / 100);
        int labelWidth = Math.max(0, bounds.width() - fieldWidth - GAP);
        fieldBounds = new Bounds(bounds.x() + bounds.width() - fieldWidth, bounds.y(), fieldWidth, bounds.height());

        float scale = OptionStyle.TEXT_SCALE;
        int textY = bounds.y() + Math.max(0, (bounds.height() - Math.round(8 * scale)) / 2);
        context.drawText(OptionStyle.fit(context, label, scale, labelWidth), bounds.x(), textY,
                OptionStyle.labelColor(context, on), scale);

        int border = focused ? OptionStyle.ACCENT : context.theme().color(ThemeKey.BORDER);
        context.drawBorder(fieldBounds.x(), fieldBounds.y(), fieldBounds.width(), fieldBounds.height(), 1, on ? border : OptionStyle.dimmed(border));

        int pad = 2;
        context.pushClip(fieldBounds.x() + pad, fieldBounds.y(), Math.max(0, fieldBounds.width() - 2 * pad), fieldBounds.height());
        try {
            boolean showingHint = value.isEmpty() && !focused;
            String shown = showingHint ? hint : value;
            int color = showingHint ? OptionStyle.labelColor(context, false) : OptionStyle.labelColor(context, on);
            context.drawText(shown, fieldBounds.x() + pad, textY, color, scale);
            if (focused && (System.currentTimeMillis() / CURSOR_BLINK_MS) % 2 == 0) {
                int cursorX = fieldBounds.x() + pad + context.textWidth(value.substring(0, cursor), scale);
                context.fillRect(cursorX, fieldBounds.y() + 1, 1, fieldBounds.height() - 2, OptionStyle.ACCENT);
            }
        } finally {
            context.popClip();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!bounds.contains((int) mouseX, (int) mouseY)) {
            return false;
        }
        if (enabled.getAsBoolean() && fieldBounds.contains((int) mouseX, (int) mouseY)) {
            focused = true;
            String value = getter.get();
            cursor = value != null ? value.length() : 0;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY) {
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY) {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        return false;
    }

    @Override
    public void blur() {
        focused = false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!focused || !enabled.getAsBoolean()) {
            return false;
        }
        if (Character.isISOControl(codePoint)) {
            return false;
        }
        String value = getter.get();
        if (value == null) {
            value = "";
        }
        if (value.length() >= maxLength) {
            return true;
        }
        String next = value.substring(0, cursor) + codePoint + value.substring(cursor);
        cursor++;
        setter.accept(next);
        onCommit.run();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!focused || !enabled.getAsBoolean()) {
            return false;
        }
        String value = getter.get();
        if (value == null) {
            value = "";
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (cursor > 0) {
                    setter.accept(value.substring(0, cursor - 1) + value.substring(cursor));
                    cursor--;
                    onCommit.run();
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (cursor < value.length()) {
                    setter.accept(value.substring(0, cursor) + value.substring(cursor + 1));
                    onCommit.run();
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                cursor = Math.max(0, cursor - 1);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                cursor = Math.min(value.length(), cursor + 1);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                cursor = 0;
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                cursor = value.length();
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_ESCAPE -> {
                focused = false;
                return true;
            }
            default -> {
                // Still consumed, not just ignored: while this field holds focus every other key
                // must be swallowed too, or a letter key that also happens to be a vanilla keybind
                // (E for "open/close inventory" is the obvious one) falls through to the host
                // screen's own keyPressed handling and fires that keybind instead of typing the
                // letter — same reasoning vanilla's own EditBox follows by always reporting itself
                // focused/hovered to Screen#getFocused() while active.
                return true;
            }
        }
    }
}
