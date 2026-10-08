package dev.marie.framework.ui.itemeditor;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.api.registry.ValueRegistry;
import dev.marie.framework.api.value.ValueDefinition;
import dev.marie.framework.classification.ClassificationTrace;
import dev.marie.framework.classification.ClassificationTraceStep;
import dev.marie.framework.classification.TraceStepId;
import dev.marie.framework.core.MarieContext;
import dev.marie.framework.runtime.RuntimeResolver;
import dev.marie.framework.runtime.SourceClassificationRegistry;
import dev.marie.framework.runtime.SourceRegistry;
import dev.marie.framework.ui.component.widgets.ItemSlotComponent;
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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link ItemEditorPanel}'s built-in "Values" view and classification trace: the per-{@link
 * ValueDefinition} sliders, Calories/Enabled rows, {@link SourceClassificationRegistry} load/save/
 * revert, and the {@link RuntimeResolver#resolveWithTrace} readout backing the Info tab. Pulled out
 * of {@code ItemEditorPanel} itself since this is a cohesive, self-contained unit of state (nothing
 * outside it needs {@link #editedValues}/{@link #editedCalories}/{@link #editedEnabled} directly) —
 * the same reasoning that already split the menu bar and preview column into their own classes.
 *
 * <p>Every registry call this makes runs inside {@link MarieContext#runAs} scoped to {@code modId},
 * so editing an item in a multi-mod setup always reads/writes that mod's own config directory.
 */
@ApiStatus.Internal
final class ItemEditorValuesModel {

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

    ItemEditorValuesModel(String id, String modId, @Nullable RecipeManager recipeManager, ItemSlotComponent slot) {
        this.id = id;
        this.modId = modId;
        this.recipeManager = recipeManager;
        this.slot = slot;
    }

    @Nullable
    String sourceId() {
        ItemStack stack = slot.item();
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ResourceLocation key = MarieRegistryUtils.itemKey(stack.getItem());
        return key != null ? key.toString() : null;
    }

    OptionLayout layout() {
        return valuesLayout;
    }

    MarieTextList traceList() {
        return traceList;
    }

    /** Reloads everything from the current {@link #slot} item — called by {@code ItemEditorPanel#retarget} whenever the targeted item changes. */
    void retarget() {
        loadOverride();
        rebuildValuesLayout();
        refreshTrace();
    }

    void save() {
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

    void revert() {
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

    private void loadOverride() {
        editedValues.clear();
        editedCalories = 0;
        editedEnabled = true;
        String sourceId = sourceId();
        if (sourceId == null) {
            return;
        }
        ItemStack stack = slot.item();
        MarieContext.runAs(MarieContext.forMod(modId), () -> {
            SourceClassificationRegistry.SourceClassification existing = SourceClassificationRegistry.get(sourceId);
            if (existing != null) {
                editedValues.putAll(existing.values());
                editedCalories = existing.calories();
                editedEnabled = existing.enabled();
            } else {
                // No saved SourceClassificationRegistry override yet — start the sliders from
                // whatever gameplay is actually using right now instead of a blank 0%. That is NOT
                // RuntimeResolver.resolve()'s raw tag/keyword cascade guess: when a source already has
                // an external/API or cached-scanner classification registered (SourceRegistry —
                // exactly what resolveWithTrace's EXTERNAL_CLASSIFICATION trace step calls out as what
                // gameplay actually uses, "NOT the live inference below"), that registered result takes
                // priority. Only a source with no registered classification at all falls back to the
                // live inference cascade as a starting guess.
                ResourceLocation itemId = MarieRegistryUtils.itemKey(stack);
                Map<String, Float> external = itemId != null ? SourceRegistry.getExternalClassification(itemId) : null;
                if (external != null && !external.isEmpty()) {
                    editedValues.putAll(external);
                } else {
                    editedValues.putAll(RuntimeResolver.getInstance().resolve(stack, recipeManager));
                }
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

    /** Human phrase for each {@link TraceStepId}, shown in place of the raw enum constant. */
    private static final Map<TraceStepId, String> STEP_LABELS = Map.ofEntries(
            Map.entry(TraceStepId.ITEM_DISCOVERY, "Item lookup"),
            Map.entry(TraceStepId.VALUE_TAG_LOOKUP, "Tag match"),
            Map.entry(TraceStepId.EXTERNAL_CLASSIFICATION, "Saved/API override"),
            Map.entry(TraceStepId.RESOLVER_CACHE, "Cache check"),
            Map.entry(TraceStepId.COMMUNITY_TAG_SIGNAL, "Community tag signal"),
            Map.entry(TraceStepId.KEYWORD_SUFFIX_SCORING, "Name keyword scoring"),
            Map.entry(TraceStepId.RECIPE_LOOKUP, "Recipe lookup"),
            Map.entry(TraceStepId.INGREDIENT_RESOLUTION, "Ingredient resolution"),
            Map.entry(TraceStepId.NAMESPACE_PEER, "Namespace peer match"),
            Map.entry(TraceStepId.PRIMARY_RECIPE_MERGE, "Recipe merge"),
            Map.entry(TraceStepId.TAG_RUNTIME_BLEND, "Tag + recipe blend"),
            Map.entry(TraceStepId.SIGNAL_AGGREGATION, "Combining signals"),
            Map.entry(TraceStepId.WINNER_SELECTION, "Picking the winner"),
            Map.entry(TraceStepId.CONFIDENCE, "Confidence check"),
            Map.entry(TraceStepId.HARD_FALLBACK, "Fallback"),
            Map.entry(TraceStepId.APPLY_GATE, "Final gate"));

    private static final int COLOR_SUCCESS = 0xFF6EDC6E;
    private static final int COLOR_FAILURE = 0xFFE05A5A;
    private static final int COLOR_WARNING = 0xFFE0C04A;
    private static final int COLOR_SKIPPED = 0xFF8A8A8A;
    private static final int COLOR_LABEL = 0xFFB0B0B0;
    private static final int COLOR_VALUE = 0xFFE0E0E0;
    private static final int COLOR_ACCENT = 0xFF7EC8FF;

    private void refreshTrace() {
        ItemStack stack = slot.item();
        if (stack == null || stack.isEmpty()) {
            traceList.setColoredLines(List.of(new MarieTextList.Line("No item selected.", COLOR_LABEL)));
            return;
        }
        AtomicReference<List<MarieTextList.Line>> lines = new AtomicReference<>();
        MarieContext.runAs(MarieContext.forMod(modId), () -> lines.set(buildTraceLines(stack)));
        traceList.setColoredLines(lines.get());
    }

    private List<MarieTextList.Line> buildTraceLines(ItemStack stack) {
        if (!MarieContext.isRegistered()) {
            return List.of(new MarieTextList.Line("MarieLib context not registered.", COLOR_FAILURE));
        }
        ClassificationTrace trace = RuntimeResolver.getInstance().resolveWithTrace(stack, recipeManager);
        if (trace == null) {
            return List.of(new MarieTextList.Line("Not resolvable as a value source.", COLOR_FAILURE));
        }
        List<MarieTextList.Line> lines = new ArrayList<>();
        for (ClassificationTraceStep step : trace.steps()) {
            String icon = switch (step.status()) {
                case SUCCESS -> "✓";
                case FAILURE -> "✗";
                case WARNING -> "⚠";
                case SKIPPED -> "-";
            };
            int color = switch (step.status()) {
                case SUCCESS -> COLOR_SUCCESS;
                case FAILURE -> COLOR_FAILURE;
                case WARNING -> COLOR_WARNING;
                case SKIPPED -> COLOR_SKIPPED;
            };
            String label = STEP_LABELS.getOrDefault(step.id(), prettify(step.id().name()));
            lines.add(new MarieTextList.Line(icon + " " + label + ": " + step.message(), color));
        }
        lines.add(new MarieTextList.Line("", COLOR_VALUE));
        if (trace.dominant() != null) {
            lines.add(new MarieTextList.Line("Result: " + prettify(trace.dominant()), COLOR_ACCENT));
        }
        if (trace.resolutionStage() != null) {
            lines.add(new MarieTextList.Line("Determined by: " + prettify(trace.resolutionStage().name()), COLOR_LABEL));
        }
        if (trace.cascadeStage() != null) {
            lines.add(new MarieTextList.Line("Matching method: " + prettify(trace.cascadeStage().displayName()), COLOR_LABEL));
        }
        if (trace.uncertain()) {
            lines.add(new MarieTextList.Line("⚠ Uncertain — this item's classification may not be reliable.", COLOR_WARNING));
        }
        if (!trace.summaryReason().isEmpty()) {
            lines.add(new MarieTextList.Line("Why: " + trace.summaryReason(), COLOR_VALUE));
        }
        return lines;
    }

    /** {@code "KEYWORD_SUFFIX+RECIPE"}/{@code "HARD_FALLBACK"} → {@code "Keyword suffix + recipe"}/{@code "Hard fallback"}. */
    private static String prettify(String rawName) {
        String spaced = rawName.replace('_', ' ').replace("+", " + ").toLowerCase(Locale.ROOT);
        return spaced.isEmpty() ? spaced : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
