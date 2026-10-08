package dev.marie.framework.ui.itemeditor;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.api.value.ValueDefinition;
import dev.marie.framework.runtime.RuntimeResolver;
import dev.marie.framework.runtime.SourceClassificationRegistry;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.component.Constraint;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.component.widgets.ItemSlotComponent;
import dev.marie.framework.ui.geometry.Bounds;
import dev.marie.framework.ui.itemeditor.recipe.RecipeDisplay;
import dev.marie.framework.ui.itemeditor.recipe.RecipeDisplayLookup;
import dev.marie.framework.ui.toolbox.OptionStyle;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic item value editor: a target {@link ItemSlotComponent} (settable by drag/drop — see
 * {@link ItemSlotComponent#acceptDrop}), a live {@link RuntimeResolver#resolveWithTrace} readout of
 * why the item resolved the way it did, and one editable row per {@link ValueDefinition} the owning
 * mod has registered, writing through {@link SourceClassificationRegistry#setOverride}.
 *
 * <p>This class owns page/view selection (Values vs. Info vs. a mod's own {@link
 * ItemEditorScreenProvider} screen) and wires three self-contained pieces together:
 * <ul>
 *   <li>{@link ItemEditorPreviewColumn} — the left-hand icon card, recipe grid, and 3D station.</li>
 *   <li>{@link ItemEditorMenuBar} — the File/Info/Save/Revert row (this class implements its
 *       {@link ItemEditorMenuBar.Host} to supply the actual business logic behind those controls).</li>
 *   <li>{@link ItemEditorValuesModel} — the built-in Values sliders and classification trace,
 *       including the {@link MarieContext#runAs} scoping every override load/save goes through.</li>
 * </ul>
 */
@ApiStatus.Internal
public final class ItemEditorPanel implements MarieComponent, ItemEditorMenuBar.Host {

    private static final int COLUMN_GAP = 8;
    private static final int TRACE_MIN_HEIGHT = 70;

    private final String id;
    private final String modId;
    @Nullable
    private final RecipeManager recipeManager;

    private final ItemSlotComponent slot;
    private final ItemEditorMenuBar menuBar = new ItemEditorMenuBar();
    private final ItemEditorValuesModel valuesModel;

    /** The recipe (if any) that produces the targeted item, shown in {@link ItemEditorPreviewColumn}. Recomputed by {@link #retarget} alongside everything else that depends on the current item. */
    @Nullable
    private RecipeDisplay currentRecipe;

    /** Whatever's currently rendered below the header — the value rows, the trace, or a mod's own {@link #activeScreen} — all share this one hit-test region. */
    private Bounds bodyBounds = new Bounds(0, 0, 0, 0);
    /** Which body view the Info label has selected — false shows the value rows, true shows the classification trace. Forced false whenever {@link #activeScreen} is non-null. */
    private boolean infoSelected;
    /** A mod-supplied screen (see {@link ItemEditorScreenProvider}) selected from the File dropdown, replacing the value rows/trace entirely; null shows the built-in Values view. */
    @Nullable
    private ItemEditorPage activeScreen;
    /** The provider {@link #activeScreen} came from, so the File dropdown can highlight which screen is currently selected — compared by identity, not equality, since two providers could share a label. */
    @Nullable
    private ItemEditorScreenProvider activeScreenProvider;

    public ItemEditorPanel(String id, String modId, ItemStack initial, @Nullable RecipeManager recipeManager) {
        this.id = id;
        this.modId = modId;
        this.recipeManager = recipeManager;
        this.slot = new ItemSlotComponent(id + "-slot", initial).onItemChanged(this::retarget);
        this.valuesModel = new ItemEditorValuesModel(id, modId, recipeManager, slot);
        retarget(initial);
    }

    public ItemSlotComponent slot() {
        return slot;
    }

    /** Switches the editor to a new item — called by the slot's drop callback, or directly by a host that drives selection itself (e.g. a ghost-ingredient drop). Always returns to the built-in Values view, since a mod screen built for the old item (e.g. Nourished's Exclude Food list) has nothing to show for the new one — unless {@link #activeScreen} claims the drop itself via {@link ItemEditorPage#acceptDraggedItem}, e.g. to append it to a multi-item selection instead, in which case none of that happens and the single-item slot/screen are left exactly as they were. */
    public void retarget(ItemStack stack) {
        if (stack != null && activeScreen != null && activeScreen.acceptDraggedItem(stack)) {
            return;
        }
        if (stack != null && slot.item() != stack) {
            slot.setItem(stack);
        }
        infoSelected = false;
        activeScreen = null;
        activeScreenProvider = null;
        valuesModel.retarget();
        currentRecipe = RecipeDisplayLookup.find(slot.item(), recipeManager);
    }

    @Nullable
    private String sourceId() {
        return valuesModel.sourceId();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Constraint constraint() {
        int valuesHeight = valuesModel.layout() != null ? valuesModel.layout().constraint().preferredSize().height() : 0;
        int screenHeight = activeScreen != null ? activeScreen.constraint().preferredSize().height() : 0;
        int bodyHeight = Math.max(Math.max(valuesHeight, TRACE_MIN_HEIGHT), screenHeight);
        // The preview column and the body sit side by side, not stacked, so the panel's height is
        // whichever is taller, not their sum.
        int height = Math.max(ItemEditorPreviewColumn.totalHeight(sourceId(), currentRecipe), bodyHeight);
        int width = ItemEditorPreviewColumn.WIDTH + COLUMN_GAP + OptionStyle.PREFERRED_WIDTH;
        return Constraint.preferred(width, height);
    }

    /** Height of {@link #renderMenuBar}'s row, for a host (e.g. {@code ItemEditorWindow}) that draws it itself, above its own divider, instead of as part of {@link #render}'s body. */
    public int menuBarHeight() {
        return ItemEditorMenuBar.HEIGHT;
    }

    /** Draws the File/Info/Save/Revert row — see {@link ItemEditorMenuBar#render}. */
    public void renderMenuBar(RenderContext context, Bounds bounds, int mouseX, int mouseY) {
        menuBar.render(context, bounds, mouseX, mouseY, this);
    }

    /** Draws the File dropdown, if open — see {@link ItemEditorMenuBar#renderOverlay}. The host must call this <em>last</em>, after its own header/body content. */
    public void renderFileMenuOverlay(RenderContext context) {
        menuBar.renderOverlay(context, this);
    }

    @Override
    public void render(RenderContext context, Bounds bounds) {
        ItemStack stack = slot.item();
        boolean hasItem = stack != null && !stack.isEmpty();
        if (menuBar.isFileMenuOpen()) {
            // The open dropdown (drawn afterward, on top, by the host) already covers this whole
            // area, and nothing here can safely share space with it anyway: an item icon renders
            // through Minecraft's 3D item pipeline, which doesn't reliably respect normal 2D draw
            // order against content drawn after it in the same frame the way the dropdown's own flat
            // fills/text do. Simplest and fully robust: draw none of it while open.
            bodyBounds = new Bounds(0, 0, 0, 0);
            return;
        }

        ItemEditorPreviewColumn.render(context, bounds, slot, sourceId(), currentRecipe);
        if (!hasItem) {
            bodyBounds = new Bounds(0, 0, 0, 0);
            return;
        }

        int bodyX = bounds.x() + ItemEditorPreviewColumn.WIDTH + COLUMN_GAP;
        bodyBounds = new Bounds(bodyX, bounds.y(), Math.max(0, bounds.x() + bounds.width() - bodyX), bounds.height());
        if (activeScreen != null) {
            activeScreen.render(context, bodyBounds);
        } else if (infoSelected) {
            valuesModel.traceList().render(context, bodyBounds);
        } else {
            valuesModel.layout().render(context, bodyBounds);
        }
    }

    /**
     * Pure navigation — a "Values" entry for the built-in view plus one entry per {@link
     * ItemEditorScreenProvider} registered for {@link #modId} (e.g. Nourished's "Options" screen).
     * Nothing here saves or reverts anything; that's {@link #onSaveClicked}/{@link
     * #onRevertClicked}, acting on whichever page is selected. Rebuilt fresh each time the dropdown
     * draws, so it always reflects the current provider registrations and selection.
     */
    @Override
    public List<ItemEditorMenuBar.MenuEntry> buildFileMenuEntries() {
        List<ItemEditorMenuBar.MenuEntry> entries = new ArrayList<>();
        entries.add(new ItemEditorMenuBar.MenuEntry("Values", () -> selectScreen(null), activeScreen == null));
        for (ItemEditorScreenProvider provider : ItemEditorScreenProviderRegistry.get(modId)) {
            entries.add(new ItemEditorMenuBar.MenuEntry(provider.menuLabel(), () -> selectScreen(provider), activeScreenProvider == provider));
        }
        return entries;
    }

    /** Switches the body to {@code provider}'s screen (built fresh for the current item), or back to the built-in Values view when {@code provider} is null. */
    private void selectScreen(@Nullable ItemEditorScreenProvider provider) {
        infoSelected = false;
        activeScreenProvider = provider;
        activeScreen = provider == null ? null : provider.buildScreen(modId, sourceId(), slot.item());
        if (activeScreen != null) {
            activeScreen.attachRetargetHandler((stack, target) -> {
                retarget(stack);
                if (target != null) {
                    selectScreen(target);
                }
            });
        }
    }

    @Override
    public boolean actionsEnabled() {
        return sourceId() != null;
    }

    @Override
    public boolean infoActive() {
        return infoSelected && activeScreen == null;
    }

    @Override
    public void onInfoClicked() {
        if (activeScreen != null) {
            // A mod screen was showing: Info always switches straight to the trace, rather than
            // toggling against whatever infoSelected was left at before the screen took over.
            selectScreen(null);
            infoSelected = true;
        } else {
            infoSelected = !infoSelected;
        }
    }

    @Override
    public void onSaveClicked() {
        if (activeScreen != null) {
            activeScreen.save();
        } else {
            valuesModel.save();
        }
    }

    @Override
    public void onRevertClicked() {
        if (activeScreen != null) {
            activeScreen.revert();
        } else {
            valuesModel.revert();
        }
    }

    /** Handles a click on the menu bar — see {@link ItemEditorMenuBar#mouseClicked}. */
    public boolean menuBarMouseClicked(double mouseX, double mouseY, int button) {
        return menuBar.mouseClicked(mouseX, mouseY, button, this);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!bodyBounds.contains((int) mouseX, (int) mouseY)) {
            return false;
        }
        if (activeScreen != null) {
            return activeScreen.mouseClicked(mouseX, mouseY, button);
        }
        return !infoSelected && valuesModel.layout().mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (activeScreen != null) {
            return activeScreen.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        return !infoSelected && valuesModel.layout().mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (activeScreen != null) {
            return activeScreen.mouseReleased(mouseX, mouseY, button);
        }
        return !infoSelected && valuesModel.layout().mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!bodyBounds.contains((int) mouseX, (int) mouseY)) {
            return false;
        }
        if (activeScreen != null) {
            return activeScreen.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return infoSelected ? valuesModel.traceList().mouseScrolled(mouseX, mouseY, scrollX, scrollY)
                : valuesModel.layout().mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return activeScreen != null && activeScreen.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return activeScreen != null && activeScreen.keyPressed(keyCode, scanCode, modifiers);
    }

    /** {@link ItemEditorPage#renderOverlay} for whichever page is active, e.g. for a host ({@code ItemEditorWindow}) to call after its own clip around {@link #render}'s body is popped. No-op while the built-in Values/Info views are showing (neither currently defines an overlay). */
    public void renderActiveScreenOverlay(RenderContext context, Bounds screenBounds) {
        if (activeScreen != null) {
            activeScreen.renderOverlay(context, screenBounds);
        }
    }

    public boolean activeScreenOverlayMouseClicked(double mouseX, double mouseY, int button) {
        return activeScreen != null && activeScreen.overlayMouseClicked(mouseX, mouseY, button);
    }

    public boolean activeScreenOverlayMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return activeScreen != null && activeScreen.overlayMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public boolean activeScreenOverlayMouseReleased(double mouseX, double mouseY, int button) {
        return activeScreen != null && activeScreen.overlayMouseReleased(mouseX, mouseY, button);
    }

    public boolean activeScreenOverlayMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return activeScreen != null && activeScreen.overlayMouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
