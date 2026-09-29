package dev.marie.framework.client.config.cloth;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.color.ColorPreviewOverrides;
import dev.marie.framework.config.MariesLibConfigIO;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.AbstractConfigScreen;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Builds the MarieCore Cloth Config screen with framework-only tabs:
 * Overview, Scanner, Diagnostics, and Tools.
 */
@ApiStatus.Internal
public final class MariesLibClothConfig {

    private static volatile boolean previewClearListenerRegistered;

    private MariesLibClothConfig() {}

    public static Screen create(Screen parent) {
        ensurePreviewClearListener();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(ClothConfigHelper.t("title"))
                .setSavingRunnable(MariesLibConfigIO::save);

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        ClothCategoryOverview.build(builder, entryBuilder);
        ClothCategoryScanner.build(builder, entryBuilder, parent);
        ClothCategoryDiagnostics.build(builder, entryBuilder);
        ClothCategoryTools.build(builder, entryBuilder);

        return builder.build();
    }

    /**
     * Cloth Config has no cancel-specific callback — Cancel, Esc, and Save & Quit all just
     * replace the screen. Clearing on every {@link AbstractConfigScreen} close (registered once)
     * catches abandoned previews regardless of how the screen was left.
     */
    private static void ensurePreviewClearListener() {
        if (previewClearListenerRegistered) {
            return;
        }
        synchronized (MariesLibClothConfig.class) {
            if (previewClearListenerRegistered) {
                return;
            }
            NeoForge.EVENT_BUS.addListener((ScreenEvent.Closing event) -> {
                if (event.getScreen() instanceof AbstractConfigScreen) {
                    ColorPreviewOverrides.clearOverrides();
                }
            });
            previewClearListenerRegistered = true;
        }
    }
}
