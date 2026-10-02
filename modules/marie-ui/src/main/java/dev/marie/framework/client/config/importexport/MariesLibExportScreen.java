package dev.marie.framework.client.config.importexport;

import dev.marie.framework.api.ApiStatus;

import com.google.gson.JsonObject;
import dev.marie.framework.core.MarieCore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Select export sections and write a file or share code.
 */
@ApiStatus.Internal
public final class MariesLibExportScreen extends Screen {

    private final Screen returnTo;
    private final List<SectionRow> rows = new ArrayList<>();
    private EditBox shareBox;
    private Component status = Component.empty();

    public MariesLibExportScreen(Screen returnTo) {
        super(Component.translatable(configKey("importExport.export.title")));
        this.returnTo = returnTo;
    }

    private static String configKey(String suffix) {
        return "config." + MarieCore.MOD_ID + "." + suffix;
    }

    @Override
    protected void init() {
        int y = 40;
        for (ImportExportManager.Section section : ImportExportManager.Section.values()) {
            Checkbox box = Checkbox.builder(section.label(), this.font)
                    .pos(this.width / 2 - 100, y)
                    .selected(true)
                    .build();
            addRenderableWidget(box);
            rows.add(new SectionRow(section, box));
            y += 22;
        }

        shareBox = new EditBox(this.font, this.width / 2 - 150, y + 10, 300, 20,
                Component.translatable(configKey("importExport.export.shareCode")));
        shareBox.setMaxLength(8192);
        addRenderableWidget(shareBox);

        addRenderableWidget(Button.builder(Component.translatable(configKey("importExport.export.toFile")), b -> exportFile())
                .bounds(this.width / 2 - 154, y + 40, 150, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable(configKey("importExport.export.toShareCode")), b -> exportShare())
                .bounds(this.width / 2 + 4, y + 40, 150, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20)
                .build());
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(returnTo);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        if (!status.getString().isEmpty()) {
            graphics.drawCenteredString(this.font, status, this.width / 2, this.height - 44, 0xA0A0A0);
        }
    }

    private EnumSet<ImportExportManager.Section> selectedSections() {
        EnumSet<ImportExportManager.Section> out = EnumSet.noneOf(ImportExportManager.Section.class);
        for (SectionRow row : rows) {
            if (row.checkbox.selected()) {
                out.add(row.section);
            }
        }
        return out;
    }

    private void exportFile() {
        try {
            JsonObject root = ImportExportManager.buildExportRoot(selectedSections());
            var path = ImportExportManager.writeExportFile(root);
            status = Component.translatable(configKey("importExport.export.saved"), path.getFileName().toString());
        } catch (Exception e) {
            status = Component.translatable(configKey("importExport.export.failed"), e.getMessage());
        }
    }

    private void exportShare() {
        try {
            JsonObject root = ImportExportManager.buildExportRoot(selectedSections());
            shareBox.setValue(ImportExportManager.buildShareCode(root));
            status = Component.translatable(configKey("importExport.export.shareReady"));
        } catch (Exception e) {
            status = Component.translatable(configKey("importExport.export.failed"), e.getMessage());
        }
    }

    private record SectionRow(ImportExportManager.Section section, Checkbox checkbox) {}
}
