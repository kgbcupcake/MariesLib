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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Import settings from a share code or export file.
 */
@ApiStatus.Internal
public final class MariesLibImportScreen extends Screen {

    private final Screen returnTo;
    private final List<ImportExportManager.Section> available = new ArrayList<>();
    private final List<Checkbox> sectionBoxes = new ArrayList<>();
    private EditBox shareBox;
    private Component status = Component.empty();
    private JsonObject pendingRoot;

    public MariesLibImportScreen(Screen returnTo) {
        super(Component.translatable(configKey("importExport.import.title")));
        this.returnTo = returnTo;
    }

    private static String configKey(String suffix) {
        return "config." + MarieCore.MOD_ID + "." + suffix;
    }

    @Override
    protected void init() {
        shareBox = new EditBox(this.font, this.width / 2 - 150, 36, 300, 20,
                Component.translatable(configKey("importExport.import.pasteShareCode")));
        shareBox.setMaxLength(8192);
        addRenderableWidget(shareBox);

        addRenderableWidget(Button.builder(Component.translatable(configKey("importExport.import.parseShareCode")), b -> parseShare())
                .bounds(this.width / 2 - 154, 60, 150, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable(configKey("importExport.import.pickFile")), b -> pickLatestFile())
                .bounds(this.width / 2 + 4, 60, 150, 20)
                .build());

        rebuildSectionRows(88);

        addRenderableWidget(Button.builder(Component.translatable(configKey("importExport.import.apply")), b -> applySelected())
                .bounds(this.width / 2 - 154, this.height - 52, 150, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(this.width / 2 + 4, this.height - 52, 100, 20)
                .build());
    }

    private void rebuildSectionRows(int startY) {
        for (Checkbox box : sectionBoxes) {
            removeWidget(box);
        }
        sectionBoxes.clear();
        available.clear();
        int y = startY;
        if (pendingRoot != null) {
            available.addAll(ImportExportManager.sectionsPresent(pendingRoot));
            for (ImportExportManager.Section section : available) {
                Checkbox box = Checkbox.builder(section.label(), this.font)
                        .pos(this.width / 2 - 100, y)
                        .selected(true)
                        .build();
                addRenderableWidget(box);
                sectionBoxes.add(box);
                y += 22;
            }
        }
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
            graphics.drawCenteredString(this.font, status, this.width / 2, this.height - 68, 0xA0A0A0);
        }
    }

    private void parseShare() {
        try {
            pendingRoot = ImportExportManager.parseShareCode(shareBox.getValue());
            status = Component.translatable(configKey("importExport.import.parsed"));
            rebuildSectionRows(88);
        } catch (Exception e) {
            status = Component.translatable(configKey("importExport.import.failed"), e.getMessage());
        }
    }

    private void pickLatestFile() {
        try {
            List<Path> files = ImportExportManager.listExportJsonFiles();
            if (files.isEmpty()) {
                status = Component.translatable(configKey("importExport.import.noFiles"));
                return;
            }
            pendingRoot = ImportExportManager.parseJsonFile(files.get(files.size() - 1));
            status = Component.translatable(configKey("importExport.import.parsed"));
            rebuildSectionRows(88);
        } catch (Exception e) {
            status = Component.translatable(configKey("importExport.import.failed"), e.getMessage());
        }
    }

    private void applySelected() {
        if (pendingRoot == null) {
            status = Component.translatable(configKey("importExport.import.nothingToApply"));
            return;
        }
        EnumSet<ImportExportManager.Section> selected = EnumSet.noneOf(ImportExportManager.Section.class);
        for (int i = 0; i < available.size() && i < sectionBoxes.size(); i++) {
            if (sectionBoxes.get(i).selected()) {
                selected.add(available.get(i));
            }
        }
        if (selected.isEmpty()) {
            status = Component.translatable(configKey("importExport.import.selectSection"));
            return;
        }
        try {
            ImportExportManager.applyImport(pendingRoot, selected);
            status = Component.translatable(configKey("importExport.import.applied"));
            ImportExportToast.show(Component.translatable(configKey("importExport.import.applied")));
        } catch (Exception e) {
            status = Component.translatable(configKey("importExport.import.failed"), e.getMessage());
        }
    }
}
