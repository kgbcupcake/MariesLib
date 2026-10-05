package dev.marie.framework.ui.itemeditor;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.api.value.ValueDefinition;
import dev.marie.framework.api.registry.ValueRegistry;
import dev.marie.framework.classification.ClassificationTrace;
import dev.marie.framework.classification.ClassificationTraceStep;
import dev.marie.framework.core.MarieContext;
import dev.marie.framework.runtime.RuntimeResolver;
import dev.marie.framework.runtime.SourceClassificationRegistry;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.ThemeKey;
import dev.marie.framework.ui.component.Constraint;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.component.widgets.ItemSlotComponent;
import dev.marie.framework.ui.geometry.Bounds;
import dev.marie.framework.ui.toolbox.OptionLayout;
import dev.marie.framework.ui.toolbox.OptionRow;
import dev.marie.framework.ui.toolbox.OptionStyle;
import dev.marie.framework.ui.toolbox.SliderOption;
import dev.marie.framework.ui.toolbox.ToggleOption;
import dev.marie.framework.ui.widget.MarieTextList;
import dev.marie.framework.util.MarieRegistryUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic item value editor: a target {@link ItemSlotComponent} (settable by drag/drop — see
 * {@link ItemSlotComponent#acceptDrop}), a live {@link RuntimeResolver#resolveWithTrace} readout of
 * why the item resolved the way it did, and one editable row per {@link ValueDefinition} the owning
 * mod has registered, writing through {@link SourceClassificationRegistry#setOverride}.
 *
 * <p>Every registry call this panel makes runs inside {@link MarieContext#runAs} scoped to {@code
 * modId} (via {@link MarieContext#forMod}), so editing an item in a multi-mod setup always reads
 * and writes that mod's own config directory rather than whichever mod last attached.
 */
@ApiStatus.Internal
public final class ItemEditorPanel implements MarieComponent {

    private static final int GAP = 4;
    private static final int HEADER_HEIGHT = ItemSlotComponent.SIZE;
    private static final int TRACE_MIN_HEIGHT = 70;
    private static final int PREFERRED_WIDTH = 230;
    /** Height of the small left-aligned File/Info menu-bar row above the header, styled after a desktop window's own menu bar rather than a stretched tab strip. */
    private static final int MENU_BAR_HEIGHT = 11;
    private static final int MENU_LABEL_PADDING = 4;
    private static final int MENU_LABEL_GAP = 2;
    private static final int MENU_ITEM_HEIGHT = 12;
    private static final int MENU_WIDTH = 96;
    private static final String FILE_LABEL = "File";
    private static final String INFO_LABEL = "Info";
    private static final String[] FILE_MENU_ITEMS = {"Save Override", "Revert Override"};

    /** Generous default range for a bar/weight override — the registry itself has no inherent bound. */
    private static final double VALUE_MIN = 0.0;
    private static final double VALUE_MAX = 10.0;
    private static final double VALUE_STEP = 0.05;
    private static final int CALORIES_MIN = 0;
    private static final int CALORIES_MAX = 5000;
    private static final int CALORIES_STEP = 10;

    private final String id;
    private final String modId;
    @Nullable
    private final RecipeManager recipeManager;

    private final ItemSlotComponent slot;
    private final MarieTextList traceList = new MarieTextList("item-editor-trace", 500).withTextColor(0xFFC0C0C0);

    private OptionLayout valuesLayout;
    private final Map<String, Float> editedValues = new LinkedHashMap<>();
    private int editedCalories;
    private boolean editedEnabled = true;

    private Bounds menuBarBounds = new Bounds(0, 0, 0, 0);
    private Bounds fileLabelBounds = new Bounds(0, 0, 0, 0);
    private Bounds infoLabelBounds = new Bounds(0, 0, 0, 0);
    private Bounds headerBounds = new Bounds(0, 0, 0, 0);
    private Bounds valuesBounds = new Bounds(0, 0, 0, 0);
    private Bounds traceBounds = new Bounds(0, 0, 0, 0);
    private boolean fileMenuOpen;
    /** Which body view the Info label has selected — false shows the value rows, true shows the classification trace. */
    private boolean infoSelected;
    private final List<Bounds> fileMenuItemBounds = new ArrayList<>();

    public ItemEditorPanel(String id, String modId, ItemStack initial, @Nullable RecipeManager recipeManager) {
        this.id = id;
        this.modId = modId;
        this.recipeManager = recipeManager;
        this.slot = new ItemSlotComponent(id + "-slot", initial).onItemChanged(this::retarget);
        retarget(initial);
    }

    public ItemSlotComponent slot() {
        return slot;
    }

    /** Switches the editor to a new item — called by the slot's drop callback, or directly by a host that drives selection itself (e.g. a ghost-ingredient drop). */
    public void retarget(ItemStack stack) {
        if (stack != null && slot.item() != stack) {
            slot.setItem(stack);
        }
        loadOverride();
        rebuildValuesLayout();
        refreshTrace();
    }

    @Nullable
    private String sourceId() {
        ItemStack stack = slot.item();
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ResourceLocation key = MarieRegistryUtils.itemKey(stack.getItem());
        return key != null ? key.toString() : null;
    }

    private void loadOverride() {
        editedValues.clear();
        editedCalories = 0;
        editedEnabled = true;
        String sourceId = sourceId();
        if (sourceId == null) {
            return;
        }
        MarieContext.runAs(MarieContext.forMod(modId), () -> {
            SourceClassificationRegistry.SourceClassification existing = SourceClassificationRegistry.get(sourceId);
            if (existing != null) {
                editedValues.putAll(existing.values());
                editedCalories = existing.calories();
                editedEnabled = existing.enabled();
            }
        });
    }

    private void rebuildValuesLayout() {
        OptionLayout layout = new OptionLayout(id + "-values");
        layout.addTab("");
        if (sourceId() == null) {
            valuesLayout = layout;
            return;
        }
        List<ValueDefinition> owned = new ArrayList<>();
        for (ValueDefinition def : ValueRegistry.getAll()) {
            if (modId.equals(ValueRegistry.ownerModId(def.getId()))) {
                owned.add(def);
            }
        }
        owned.sort((a, b) -> a.getId().compareTo(b.getId()));
        for (ValueDefinition def : owned) {
            layout.addRow(new SliderOption(def.getDisplayName(),
                    () -> editedValues.getOrDefault(def.getId(), 0f),
                    v -> editedValues.put(def.getId(), (float) v),
                    VALUE_MIN, VALUE_MAX, VALUE_STEP, () -> {}));
        }
        String sourceId = sourceId();
        for (ItemEditorFieldProvider provider : ItemEditorFieldProviderRegistry.get(modId)) {
            for (OptionRow row : provider.buildRows(modId, sourceId, slot.item())) {
                layout.addRow(row);
            }
        }
        layout.addRow(SliderOption.ofInt("Calories", () -> editedCalories, v -> editedCalories = v,
                CALORIES_MIN, CALORIES_MAX, CALORIES_STEP, "cal", () -> {}));
        layout.addRow(new ToggleOption("Enabled", () -> editedEnabled, v -> editedEnabled = v, () -> {}));
        valuesLayout = layout;
    }

    private void save() {
        String sourceId = sourceId();
        if (sourceId == null) {
            return;
        }
        MarieContext.runAs(MarieContext.forMod(modId), () -> {
            SourceClassificationRegistry.setOverride(sourceId, new LinkedHashMap<>(editedValues), editedCalories, editedEnabled);
            SourceClassificationRegistry.save();
        });
        refreshTrace();
    }

    private void revert() {
        String sourceId = sourceId();
        if (sourceId == null) {
            return;
        }
        MarieContext.runAs(MarieContext.forMod(modId), () -> {
            SourceClassificationRegistry.removeOverride(sourceId);
            SourceClassificationRegistry.save();
        });
        loadOverride();
        rebuildValuesLayout();
        refreshTrace();
    }

    private void refreshTrace() {
        ItemStack stack = slot.item();
        if (stack == null || stack.isEmpty()) {
            traceList.setLines(List.of("No item selected."));
            return;
        }
        java.util.concurrent.atomic.AtomicReference<List<String>> lines = new java.util.concurrent.atomic.AtomicReference<>();
        MarieContext.runAs(MarieContext.forMod(modId), () -> lines.set(buildTraceLines(stack)));
        traceList.setLines(lines.get());
    }

    private List<String> buildTraceLines(ItemStack stack) {
        if (!MarieContext.isRegistered()) {
            return List.of("MarieLib context not registered.");
        }
        ClassificationTrace trace = RuntimeResolver.getInstance().resolveWithTrace(stack, recipeManager);
        if (trace == null) {
            return List.of("Not resolvable as a value source.");
        }
        List<String> lines = new ArrayList<>();
        for (ClassificationTraceStep step : trace.steps()) {
            lines.add("[" + step.status() + "] " + step.id() + ": " + step.message());
        }
        lines.add("");
        if (trace.dominant() != null) {
            lines.add("Dominant: " + trace.dominant());
        }
        if (trace.resolutionStage() != null) {
            lines.add("Resolution stage: " + trace.resolutionStage());
        }
        if (trace.cascadeStage() != null) {
            lines.add("Cascade stage: " + trace.cascadeStage());
        }
        if (trace.uncertain()) {
            lines.add("Uncertain classification.");
        }
        if (!trace.summaryReason().isEmpty()) {
            lines.add("Summary: " + trace.summaryReason());
        }
        return lines;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Constraint constraint() {
        int valuesHeight = valuesLayout != null ? valuesLayout.constraint().preferredSize().height() : 0;
        int bodyHeight = Math.max(valuesHeight, TRACE_MIN_HEIGHT);
        int height = HEADER_HEIGHT + GAP + bodyHeight;
        return Constraint.preferred(PREFERRED_WIDTH, height);
    }

    /** Height of {@link #renderMenuBar}'s row, for a host (e.g. {@code ItemEditorWindow}) that draws it itself, above its own divider, instead of as part of {@link #render}'s body. */
    public int menuBarHeight() {
        return MENU_BAR_HEIGHT;
    }

    /** Draws just the File/Info labels at {@code bounds} — a host-chosen row, deliberately outside {@link #render}'s own bounds so it can sit above a title divider instead of inside the scrollable body. The dropdown itself is drawn separately by {@link #renderFileMenuOverlay}: drawing it here, this early, had it painted *under* the header/body content a host draws afterward (the icon and item name rendered right on top of "Save Override", garbling both) since nothing after this call knew to leave it alone. */
    public void renderMenuBar(RenderContext context, Bounds bounds) {
        menuBarBounds = bounds;
        int labelX = menuBarBounds.x();
        int fileWidth = context.textWidth(FILE_LABEL, OptionStyle.TEXT_SCALE) + 2 * MENU_LABEL_PADDING;
        fileLabelBounds = new Bounds(labelX, menuBarBounds.y(), fileWidth, menuBarBounds.height());
        labelX += fileWidth + MENU_LABEL_GAP;
        int infoWidth = context.textWidth(INFO_LABEL, OptionStyle.TEXT_SCALE) + 2 * MENU_LABEL_PADDING;
        infoLabelBounds = new Bounds(labelX, menuBarBounds.y(), infoWidth, menuBarBounds.height());
        drawMenuLabel(context, fileLabelBounds, FILE_LABEL, fileMenuOpen);
        drawMenuLabel(context, infoLabelBounds, INFO_LABEL, infoSelected);
    }

    /** Draws the File dropdown, if open, anchored under wherever {@link #renderMenuBar} last put the File label. The host must call this <em>last</em> — after its own header/body content — so the dropdown always paints on top instead of getting painted over. No-op while the dropdown is closed. */
    public void renderFileMenuOverlay(RenderContext context) {
        if (fileMenuOpen) {
            renderFileMenu(context);
        }
    }

    @Override
    public void render(RenderContext context, Bounds bounds) {
        ItemStack stack = slot.item();
        boolean hasItem = stack != null && !stack.isEmpty();
        if (!hasItem) {
            renderEmptyState(context, bounds);
            valuesBounds = new Bounds(0, 0, 0, 0);
            traceBounds = new Bounds(0, 0, 0, 0);
            return;
        }

        headerBounds = new Bounds(bounds.x(), bounds.y(), bounds.width(), HEADER_HEIGHT);
        slot.render(context, new Bounds(headerBounds.x(), headerBounds.y(), ItemSlotComponent.SIZE, ItemSlotComponent.SIZE));
        context.drawText(stack.getHoverName().getString(), headerBounds.x() + ItemSlotComponent.SIZE + GAP, headerBounds.y() + 2,
                context.theme().color(ThemeKey.TEXT_PRIMARY), 0.9f);
        String idLine = sourceId();
        if (idLine != null) {
            context.drawText(idLine, headerBounds.x() + ItemSlotComponent.SIZE + GAP, headerBounds.y() + 11,
                    context.theme().color(ThemeKey.TEXT_SECONDARY), 0.7f);
        }

        int bodyY = headerBounds.y() + HEADER_HEIGHT + GAP;
        Bounds body = new Bounds(bounds.x(), bodyY, bounds.width(), Math.max(0, bounds.y() + bounds.height() - bodyY));
        valuesBounds = infoSelected ? new Bounds(0, 0, 0, 0) : body;
        traceBounds = infoSelected ? body : new Bounds(0, 0, 0, 0);
        if (infoSelected) {
            traceList.render(context, traceBounds);
        } else {
            valuesLayout.render(context, valuesBounds);
        }
    }

    /**
     * No item targeted yet: the slot has nothing to sit beside (no name, no sliders, no trace — the
     * values/trace tabs are both empty with {@link #sourceId()} null), so pinning it at the usual
     * top-left header spot just strands a tiny drop target above a mostly empty box. Centers the slot
     * (and its label) in the whole body instead, both as a visually balanced empty state and as a
     * bigger, easier-to-hit target for dragging an item in from JEI/EMI.
     */
    private void renderEmptyState(RenderContext context, Bounds bounds) {
        String label = "No item selected";
        int labelHeight = 9;
        int slotX = bounds.x() + (bounds.width() - ItemSlotComponent.SIZE) / 2;
        int slotY = bounds.y() + (bounds.height() - ItemSlotComponent.SIZE - GAP - labelHeight) / 2;
        headerBounds = new Bounds(slotX, slotY, ItemSlotComponent.SIZE, ItemSlotComponent.SIZE);
        slot.render(context, headerBounds);
        int labelX = bounds.x() + (bounds.width() - context.textWidth(label, 0.9f)) / 2;
        context.drawText(label, labelX, slotY + ItemSlotComponent.SIZE + GAP,
                context.theme().color(ThemeKey.TEXT_PRIMARY), 0.9f);
    }

    /** Small, auto-sized menu-bar label (left-aligned, not a stretched tab) — highlighted while its menu/view is open or selected. */
    private void drawMenuLabel(RenderContext context, Bounds bounds, String label, boolean active) {
        if (active) {
            context.fillRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), OptionStyle.dimmed(OptionStyle.ACCENT));
        }
        int color = active ? OptionStyle.ACCENT : context.theme().color(ThemeKey.TEXT_SECONDARY);
        context.drawText(label, bounds.x() + MENU_LABEL_PADDING, bounds.y() + (bounds.height() - 7) / 2, color, OptionStyle.TEXT_SCALE);
    }

    private void renderFileMenu(RenderContext context) {
        int height = FILE_MENU_ITEMS.length * MENU_ITEM_HEIGHT;
        int x = fileLabelBounds.x();
        int y = fileLabelBounds.y() + fileLabelBounds.height() + 1;
        int background = context.theme().color(ThemeKey.PANEL_BACKGROUND);
        int border = context.theme().color(ThemeKey.BORDER);
        context.drawRoundedRect(x, y, MENU_WIDTH, height, 1, background, border);

        fileMenuItemBounds.clear();
        for (int i = 0; i < FILE_MENU_ITEMS.length; i++) {
            Bounds item = new Bounds(x, y + i * MENU_ITEM_HEIGHT, MENU_WIDTH, MENU_ITEM_HEIGHT);
            fileMenuItemBounds.add(item);
            context.drawText(FILE_MENU_ITEMS[i], item.x() + 3, item.y() + (MENU_ITEM_HEIGHT - 7) / 2,
                    context.theme().color(ThemeKey.TEXT_PRIMARY), OptionStyle.TEXT_SCALE);
        }
    }

    /**
     * Handles a click on the menu bar drawn by {@link #renderMenuBar} (including its File dropdown,
     * which can extend below the row a host gave that method) — called by the host ahead of its own
     * other hit-testing (e.g. a gear/Style button), the same way {@link #renderMenuBar} is drawn
     * ahead of the host's own divider. Returns false (not handled) only when the dropdown is closed
     * and the click missed both labels, so the host's other controls still get a chance at it.
     */
    public boolean menuBarMouseClicked(double mouseX, double mouseY, int button) {
        if (fileMenuOpen) {
            fileMenuOpen = false;
            for (int i = 0; i < fileMenuItemBounds.size(); i++) {
                if (fileMenuItemBounds.get(i).contains((int) mouseX, (int) mouseY)) {
                    if (i == 0) {
                        save();
                    } else {
                        revert();
                    }
                    break;
                }
            }
            return true;
        }
        if (button != 0) {
            return false;
        }
        if (fileLabelBounds.contains((int) mouseX, (int) mouseY)) {
            fileMenuOpen = true;
            return true;
        }
        if (infoLabelBounds.contains((int) mouseX, (int) mouseY)) {
            infoSelected = !infoSelected;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (valuesBounds.contains((int) mouseX, (int) mouseY) && valuesLayout.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return !infoSelected && valuesLayout.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return !infoSelected && valuesLayout.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (traceBounds.contains((int) mouseX, (int) mouseY)) {
            return traceList.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (valuesBounds.contains((int) mouseX, (int) mouseY)) {
            return valuesLayout.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return false;
    }
}
