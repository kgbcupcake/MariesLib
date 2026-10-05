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
import dev.marie.framework.ui.toolbox.ButtonOption;
import dev.marie.framework.ui.toolbox.OptionLayout;
import dev.marie.framework.ui.toolbox.OptionRow;
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

    private Bounds headerBounds = new Bounds(0, 0, 0, 0);
    private Bounds valuesBounds = new Bounds(0, 0, 0, 0);
    private Bounds traceBounds = new Bounds(0, 0, 0, 0);

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
        layout.addRow(new ButtonOption("Override", "Save", this::save, () -> {}));
        layout.addRow(new ButtonOption("Override", "Revert", this::revert, () -> {}));
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
        int height = HEADER_HEIGHT + GAP + valuesHeight + GAP + TRACE_MIN_HEIGHT;
        return Constraint.preferred(PREFERRED_WIDTH, height);
    }

    @Override
    public void render(RenderContext context, Bounds bounds) {
        int valuesHeight = valuesLayout.constraint().preferredSize().height();
        headerBounds = new Bounds(bounds.x(), bounds.y(), bounds.width(), HEADER_HEIGHT);
        valuesBounds = new Bounds(bounds.x(), headerBounds.y() + HEADER_HEIGHT + GAP, bounds.width(), valuesHeight);
        int traceY = valuesBounds.y() + valuesBounds.height() + GAP;
        traceBounds = new Bounds(bounds.x(), traceY, bounds.width(), Math.max(TRACE_MIN_HEIGHT, bounds.y() + bounds.height() - traceY));

        slot.render(context, new Bounds(headerBounds.x(), headerBounds.y(), ItemSlotComponent.SIZE, ItemSlotComponent.SIZE));
        ItemStack stack = slot.item();
        String name = stack != null && !stack.isEmpty() ? stack.getHoverName().getString() : "No item selected";
        context.drawText(name, headerBounds.x() + ItemSlotComponent.SIZE + GAP, headerBounds.y() + 2,
                context.theme().color(ThemeKey.TEXT_PRIMARY), 0.9f);
        String idLine = sourceId();
        if (idLine != null) {
            context.drawText(idLine, headerBounds.x() + ItemSlotComponent.SIZE + GAP, headerBounds.y() + 11,
                    context.theme().color(ThemeKey.TEXT_SECONDARY), 0.7f);
        }

        valuesLayout.render(context, valuesBounds);
        traceList.render(context, traceBounds);
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
        return valuesLayout.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return valuesLayout.mouseReleased(mouseX, mouseY, button);
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
