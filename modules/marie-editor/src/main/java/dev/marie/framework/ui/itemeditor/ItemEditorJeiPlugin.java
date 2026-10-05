package dev.marie.framework.ui.itemeditor;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.core.MarieCore;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

/**
 * Registers ghost-ingredient drag targets and a {@code addGuiScreenHandler} with JEI for the item
 * editor's two hosts: {@link ItemEditorGhostIngredientHandler}/{@code addGuiScreenHandler} for
 * standalone {@link ItemEditorScreen} (a plain {@code Screen}, not an {@code
 * AbstractContainerScreen}, so without the latter JEI has no {@code IGuiProperties} for it and
 * renders nothing to drag from), and {@link ItemEditorOverlayGhostIngredientHandler} for {@link
 * ItemEditorOverlay} (drawn on top of whatever real screen is already open — that screen already
 * has its own native JEI support, so no {@code addGuiScreenHandler} is needed for it). {@link
 * ItemEditorOverlayExclusionHandler} additionally reports the overlay's own box as an area JEI's
 * list should avoid, since nothing else here tells JEI about it when it's drawn over some other
 * screen's own area. A separate
 * plugin from {@code MarieJeiPlugin} (marie-ui) rather than an addition to it: marie-ui has no
 * dependency on marie-editor, and JEI discovers every {@code @JeiPlugin}-annotated class on the
 * classpath independently, so a second plugin class needs no wiring back to the first.
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
        registration.addGuiScreenHandler(ItemEditorScreen.class, ItemEditorJeiPlugin::guiProperties);
        registration.addGhostIngredientHandler(Screen.class, new ItemEditorOverlayGhostIngredientHandler());
        registration.addGlobalGuiHandler(new ItemEditorOverlayExclusionHandler());
    }

    private static IGuiProperties guiProperties(ItemEditorScreen screen) {
        Rect2i area = screen.occupiedArea();
        return new IGuiProperties() {
            @Override
            public Class<? extends net.minecraft.client.gui.screens.Screen> screenClass() {
                return ItemEditorScreen.class;
            }

            @Override
            public int guiLeft() {
                return area.getX();
            }

            @Override
            public int guiTop() {
                return area.getY();
            }

            @Override
            public int guiXSize() {
                return area.getWidth();
            }

            @Override
            public int guiYSize() {
                return area.getHeight();
            }

            @Override
            public int screenWidth() {
                return screen.width;
            }

            @Override
            public int screenHeight() {
                return screen.height;
            }
        };
    }
}
