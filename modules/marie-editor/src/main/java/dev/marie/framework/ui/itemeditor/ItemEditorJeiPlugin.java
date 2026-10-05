package dev.marie.framework.ui.itemeditor;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.core.MarieCore;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.resources.ResourceLocation;

/**
 * Registers {@link ItemEditorGhostIngredientHandler} with JEI, so dropping an ingredient from JEI's
 * list onto an open {@link ItemEditorScreen} targets it. A separate plugin from {@code
 * MarieJeiPlugin} (marie-ui) rather than an addition to it: marie-ui has no dependency on
 * marie-editor, and JEI discovers every {@code @JeiPlugin}-annotated class on the classpath
 * independently, so a second plugin class needs no wiring back to the first.
 */
@JeiPlugin
@ApiStatus.Internal
public final class ItemEditorJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(MarieCore.MOD_ID, "item_editor_jei_plugin");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(ItemEditorScreen.class, new ItemEditorGhostIngredientHandler());
    }
}
