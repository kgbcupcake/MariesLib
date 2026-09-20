package dev.marie.framework.client.config.cloth;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.toolbox.colorpicker.HexColors;
import dev.marie.framework.color.ColorDefinition;
import dev.marie.framework.color.ColorDefinitionRegistry;
import dev.marie.framework.color.ColorKey;
import dev.marie.framework.color.ColorPreviewOverrides;
import dev.marie.framework.color.ColorRegistry;
import dev.marie.framework.color.MarieColors;
import me.shedaniel.clothconfig2.gui.entries.TooltipListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Generic Cloth Config row for a MarieLib {@link ColorKey}: swatch, {@code #RRGGBB} hex field
 * with validation, reset, and live preview. Resolves and edits the effective color through
 * {@link MarieColors#resolveColor(ColorKey)} and {@link ColorRegistry}, rather than any
 * domain-specific default-resolution logic.
 */
@ApiStatus.Stable
public final class ColorHexRowWidget extends TooltipListEntry<Integer> {

    public static final int SWATCH = 10;

    private final ColorKey key;
    private final Component label;
    private final int startArgb;
    private final String initialHex;
    private final EditBox hexBox;
    private final Button resetButton;
    private boolean suppressHexResponder;

    public ColorHexRowWidget(ColorKey key, Component label, Component... tooltip) {
        super(Component.empty(), () -> Optional.of(tooltip), false);
        this.key = key;
        this.label = label;
        this.startArgb = MarieColors.resolveColor(key);
        this.initialHex = formatRgbHex(startArgb);
        this.hexBox = new EditBox(Minecraft.getInstance().font, 0, 0, 96, 18, Component.translatable("config.marielib.colors.hex"));
        this.hexBox.setMaxLength(7);
        this.hexBox.setFilter(ColorHexRowWidget::hexInputFilter);
        this.hexBox.setHint(Component.translatable("config.marielib.colors.hexHint"));
        this.hexBox.setValue(initialHex);
        this.hexBox.setResponder(this::onHexChanged);
        this.resetButton = Button.builder(Component.translatable("config.marielib.colors.reset"), b -> onReset())
                .bounds(0, 0, 56, 18)
                .build();
        setErrorSupplier(this::computeError);
        pushLivePreview();
    }

    private static String registryKey(ColorKey key) {
        return key.id().toString();
    }

    private static boolean hexInputFilter(String s) {
        return HexColors.hexInputFilter(s);
    }

    private Optional<Component> computeError() {
        if (parseStrictRgbHex(hexBox.getValue()).isPresent()) {
            return Optional.empty();
        }
        if (hexBox.getValue().trim().isEmpty()) {
            return Optional.of(Component.translatable("config.marielib.colors.error.empty"));
        }
        return Optional.of(Component.translatable("config.marielib.colors.error.invalid"));
    }

    private void onHexChanged(String s) {
        if (suppressHexResponder) {
            return;
        }
        pushLivePreview();
        requestReferenceRebuilding();
    }

    private void pushLivePreview() {
        ColorPreviewOverrides.setOverride(key, parseStrictRgbHex(hexBox.getValue()).orElse(null));
    }

    private void onReset() {
        ColorRegistry.remove(registryKey(key));
        ColorPreviewOverrides.setOverride(key, null);
        syncHexFromEffectiveColor();
        pushLivePreview();
        requestReferenceRebuilding();
    }

    /**
     * Re-reads the current effective color (transient preview override if present, else
     * {@link ColorRegistry}, else the registered default) and updates this row's swatch/hex
     * field in place. Call after an out-of-band change to the color (e.g. a bulk "reset all")
     * so the row reflects it without rebuilding the whole config screen.
     */
    @ApiStatus.Stable
    public void syncFromEffectiveColor() {
        ColorPreviewOverrides.setOverride(key, null);
        syncHexFromEffectiveColor();
        pushLivePreview();
    }

    private void syncHexFromEffectiveColor() {
        int effective = MarieColors.resolveColor(key);
        suppressHexResponder = true;
        hexBox.setValue(formatRgbHex(effective));
        suppressHexResponder = false;
    }

    private static String formatRgbHex(int argb) {
        return HexColors.formatRgbHex(argb);
    }

    /** Strict {@code #RRGGBB} (hash required, exactly six hex digits). */
    static Optional<Integer> parseStrictRgbHex(String raw) {
        return HexColors.parseStrictRgbHex(raw);
    }

    private int previewArgb() {
        return parseStrictRgbHex(hexBox.getValue()).orElse(startArgb);
    }

    @Override
    public Integer getValue() {
        return parseStrictRgbHex(hexBox.getValue()).orElse(startArgb);
    }

    @Override
    public Optional<Integer> getDefaultValue() {
        return Optional.of(startArgb);
    }

    @Override
    public boolean isEdited() {
        Optional<Integer> cur = parseStrictRgbHex(hexBox.getValue());
        if (cur.isEmpty()) {
            return !hexBox.getValue().trim().equalsIgnoreCase(initialHex.trim());
        }
        int rgbA = cur.get() & 0x00_FF_FF_FF;
        int rgbB = startArgb & 0x00_FF_FF_FF;
        return rgbA != rgbB;
    }

    @Override
    public void save() {
        Optional<Integer> parsed = parseStrictRgbHex(hexBox.getValue());
        if (parsed.isEmpty()) {
            return;
        }
        int now = parsed.get();
        ColorDefinition definition = ColorDefinitionRegistry.get(key);
        int def = definition != null ? definition.getDefaultArgb() : 0xFFFF00FF;
        int nowRgb = now & 0x00_FF_FF_FF;
        int defRgb = def & 0x00_FF_FF_FF;
        if (nowRgb == defRgb) {
            ColorRegistry.remove(registryKey(key));
        } else {
            ColorRegistry.setArgb(registryKey(key), now);
        }
    }

    @Override
    public int getItemHeight() {
        return 24;
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int index,
            int y,
            int x,
            int entryWidth,
            int entryHeight,
            int mouseX,
            int mouseY,
            boolean isHovered,
            float delta
    ) {
        super.render(graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta);

        int cy = y + (entryHeight - SWATCH) / 2;
        int sx = x + 6;
        int argb = previewArgb();
        graphics.fill(sx, cy, sx + SWATCH, cy + SWATCH, argb);
        graphics.renderOutline(sx, cy, SWATCH, SWATCH, 0xFF404040);

        var font = Minecraft.getInstance().font;
        int nameX = sx + SWATCH + 6;
        int resetW = 56;
        int hexW = Mth.clamp(entryWidth - 24 - SWATCH - 6 - 8 - resetW - 8, 56, 120);
        int resetX = x + entryWidth - resetW - 6;
        int hexX = resetX - 6 - hexW;
        int nameMaxW = Math.max(0, hexX - nameX - 4);
        if (nameMaxW > 0) {
            graphics.drawString(font, font.plainSubstrByWidth(label.getString(), nameMaxW), nameX, y + 6, getPreferredTextColor(), false);
        }

        hexBox.setX(hexX);
        hexBox.setY(y + 3);
        hexBox.setWidth(hexW);
        hexBox.setHeight(18);
        hexBox.setEditable(isEditable());

        resetButton.setX(resetX);
        resetButton.setY(y + 3);
        resetButton.active = isEditable();

        boolean invalid = parseStrictRgbHex(hexBox.getValue()).isEmpty();
        if (invalid) {
            int border = 0xFFFF3333;
            graphics.renderOutline(hexX - 1, y + 2, hexW + 2, 20, border);
        }

        hexBox.render(graphics, mouseX, mouseY, delta);
        resetButton.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() {
        List<net.minecraft.client.gui.components.events.GuiEventListener> list = new ArrayList<>();
        list.add(hexBox);
        list.add(resetButton);
        return list;
    }

    @Override
    public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
        return List.of(hexBox, resetButton);
    }
}
