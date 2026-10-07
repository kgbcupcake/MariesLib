package dev.marie.framework.ui.itemeditor;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.api.value.ValueDefinition;
import dev.marie.framework.api.registry.ValueRegistry;
import dev.marie.framework.classification.ClassificationTrace;
import dev.marie.framework.classification.ClassificationTraceStep;
import dev.marie.framework.core.MarieContext;
import dev.marie.framework.runtime.RuntimeResolver;
import dev.marie.framework.runtime.SourceClassificationRegistry;
import dev.marie.framework.runtime.SourceRegistry;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.Theme;
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
    private static final int TRACE_MIN_HEIGHT = 70;
    /** Fixed width of the left-hand preview box — unlike the Hub Home page's entity box, this doesn't scale with window width: a flat item icon doesn't need to grow just because the window got wider. */
    private static final int LEFT_BOX_WIDTH = 72;
    private static final int COLUMN_GAP = 8;
    private static final int BOX_PADDING = 5;
    /** Same preview-box background the Hub Home page's {@code EntityPreviewBox} uses, so this reads as the same family of "preview box" rather than a different style. */
    private static final int BOX_BACKGROUND = 0xFF15171C;
    private static final int LINE_GAP = 3;
    private static final int NAME_TEXT_HEIGHT = 8;
    private static final int ID_TEXT_HEIGHT = 7;
    private static final float NAME_TEXT_SCALE = 0.8f;
    private static final float ID_TEXT_SCALE = 0.65f;
    /** Height of the small left-aligned File/Info menu-bar row above the header, styled after a desktop window's own menu bar rather than a stretched tab strip. */
    private static final int MENU_BAR_HEIGHT = 11;
    private static final int MENU_LABEL_PADDING = 4;
    private static final int MENU_LABEL_GAP = 2;
    private static final int MENU_ITEM_HEIGHT = 12;
    private static final int MENU_SEPARATOR_HEIGHT = 5;
    private static final int MENU_MIN_WIDTH = 96;
    private static final String FILE_LABEL = "File";
    private static final String INFO_LABEL = "Info";
    private static final String SAVE_LABEL = "Save";
    private static final String REVERT_LABEL = "Revert";
    private static final String SAVED_CONFIRM_LABEL = "Saved ✓";
    private static final String REVERTED_CONFIRM_LABEL = "Reverted ✓";
    /** Gap between the right-aligned Save/Revert labels. */
    private static final int ACTION_LABEL_GAP = 4;
    /** How long Save/Revert show their confirmation label after a click, so a click that landed is never silently indistinguishable from one that didn't. */
    private static final long CONFIRM_DURATION_MS = 1100;
    private static final int CONFIRM_COLOR = 0xFF55FF55;

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
    private Bounds saveLabelBounds = new Bounds(0, 0, 0, 0);
    private Bounds revertLabelBounds = new Bounds(0, 0, 0, 0);
    private Bounds headerBounds = new Bounds(0, 0, 0, 0);
    /** Whatever's currently rendered below the header — the value rows, the trace, or a mod's own {@link #activeScreen} — all share this one hit-test region. */
    private Bounds bodyBounds = new Bounds(0, 0, 0, 0);
    /** {@code System.currentTimeMillis()} deadline until Save shows {@link #SAVED_CONFIRM_LABEL} instead of {@link #SAVE_LABEL} — 0 while idle, so a click that landed is never silently indistinguishable from one that didn't. */
    private long saveConfirmUntilMs;
    /** Same as {@link #saveConfirmUntilMs}, for Revert. */
    private long revertConfirmUntilMs;
    private boolean fileMenuOpen;
    /** Which body view the Info label has selected — false shows the value rows, true shows the classification trace. Forced false whenever {@link #activeScreen} is non-null. */
    private boolean infoSelected;
    /** A mod-supplied screen (see {@link ItemEditorScreenProvider}) selected from the File dropdown, replacing the value rows/trace entirely; null shows the built-in Values view. */
    @Nullable
    private ItemEditorPage activeScreen;
    /** The provider {@link #activeScreen} came from, so the File dropdown can highlight which screen is currently selected — compared by identity, not equality, since two providers could share a label. */
    @Nullable
    private ItemEditorScreenProvider activeScreenProvider;
    private final List<MenuRow> fileMenuRows = new ArrayList<>();

    /** One rendered row of the open File dropdown: its clickable area and the action a click on it runs — null for a non-clickable separator. */
    private record MenuRow(Bounds bounds, @Nullable Runnable action) {}

    /** One entry the File dropdown would show, before layout: null label means a separator; {@code active} highlights the entry for the currently selected view. */
    private record MenuEntry(@Nullable String label, @Nullable Runnable action, boolean active) {
        static MenuEntry separator() {
            return new MenuEntry(null, null, false);
        }

        boolean isSeparator() {
            return label == null;
        }
    }

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

    /** Switches the editor to a new item — called by the slot's drop callback, or directly by a host that drives selection itself (e.g. a ghost-ingredient drop). Always returns to the built-in Values view, since a mod screen built for the old item (e.g. Nourished's Exclude Food list) has nothing to show for the new one. */
    public void retarget(ItemStack stack) {
        if (stack != null && slot.item() != stack) {
            slot.setItem(stack);
        }
        infoSelected = false;
        activeScreen = null;
        activeScreenProvider = null;
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
        int screenHeight = activeScreen != null ? activeScreen.constraint().preferredSize().height() : 0;
        int bodyHeight = Math.max(Math.max(valuesHeight, TRACE_MIN_HEIGHT), screenHeight);
        // The preview box and the body sit side by side now, not stacked, so the panel's height is
        // whichever column is taller, not their sum.
        int height = Math.max(previewBoxHeight(), bodyHeight);
        int width = LEFT_BOX_WIDTH + COLUMN_GAP + OptionStyle.PREFERRED_WIDTH;
        return Constraint.preferred(width, height);
    }

    /** Height of {@link #renderPreviewBox}'s box: padding, the fixed-size slot, the name line, and (when known) the resource-id line below it — content-driven, not stretched to fill the column like the Hub Home page's entity box. */
    private int previewBoxHeight() {
        int height = BOX_PADDING + ItemSlotComponent.SIZE + LINE_GAP + NAME_TEXT_HEIGHT;
        if (sourceId() != null) {
            height += LINE_GAP + ID_TEXT_HEIGHT;
        }
        return height + BOX_PADDING;
    }

    /** Height of {@link #renderMenuBar}'s row, for a host (e.g. {@code ItemEditorWindow}) that draws it itself, above its own divider, instead of as part of {@link #render}'s body. */
    public int menuBarHeight() {
        return MENU_BAR_HEIGHT;
    }

    /**
     * Draws the File/Info labels on the left and Save/Revert on the right of {@code bounds} — a
     * host-chosen row, deliberately outside {@link #render}'s own bounds so it can sit above a
     * title divider instead of inside the scrollable body. The dropdown itself is drawn separately
     * by {@link #renderFileMenuOverlay}: drawing it here, this early, had it painted *under* the
     * header/body content a host draws afterward, garbling both, since nothing after this call knew
     * to leave it alone.
     *
     * <p>Save/Revert always act on whichever page is currently active — the built-in Values view, or
     * a mod's {@link #activeScreen} — never on the File dropdown, which is pure navigation between
     * pages. They dim (but still render, so the row never jumps around) when no item is targeted,
     * since there's nothing to save or revert yet; while hovered (and enabled) they highlight the
     * same way File/Info do, so it's visually obvious they're clickable. For a beat after an actual
     * click lands, the label itself swaps to a green "Saved ✓"/"Reverted ✓" confirmation (see
     * {@link #saveConfirmUntilMs}/{@link #revertConfirmUntilMs}) — a click that did nothing
     * (disabled, or missed the label) was otherwise visually indistinguishable from one that worked.
     */
    public void renderMenuBar(RenderContext context, Bounds bounds, int mouseX, int mouseY) {
        menuBarBounds = bounds;
        int labelX = menuBarBounds.x();
        int fileWidth = context.textWidth(FILE_LABEL, OptionStyle.TEXT_SCALE) + 2 * MENU_LABEL_PADDING;
        fileLabelBounds = new Bounds(labelX, menuBarBounds.y(), fileWidth, menuBarBounds.height());
        labelX += fileWidth + MENU_LABEL_GAP;
        int infoWidth = context.textWidth(INFO_LABEL, OptionStyle.TEXT_SCALE) + 2 * MENU_LABEL_PADDING;
        infoLabelBounds = new Bounds(labelX, menuBarBounds.y(), infoWidth, menuBarBounds.height());
        drawMenuLabel(context, fileLabelBounds, FILE_LABEL, fileMenuOpen || fileLabelBounds.contains(mouseX, mouseY));
        drawMenuLabel(context, infoLabelBounds, INFO_LABEL,
                (infoSelected && activeScreen == null) || infoLabelBounds.contains(mouseX, mouseY));

        boolean actionsEnabled = sourceId() != null;
        long now = System.currentTimeMillis();
        boolean showSaveConfirm = now < saveConfirmUntilMs;
        boolean showRevertConfirm = now < revertConfirmUntilMs;
        String saveLabel = showSaveConfirm ? SAVED_CONFIRM_LABEL : SAVE_LABEL;
        String revertLabel = showRevertConfirm ? REVERTED_CONFIRM_LABEL : REVERT_LABEL;

        int revertWidth = context.textWidth(revertLabel, OptionStyle.TEXT_SCALE) + 2 * MENU_LABEL_PADDING;
        int saveWidth = context.textWidth(saveLabel, OptionStyle.TEXT_SCALE) + 2 * MENU_LABEL_PADDING;
        int rightX = menuBarBounds.x() + menuBarBounds.width();
        revertLabelBounds = new Bounds(rightX - revertWidth, menuBarBounds.y(), revertWidth, menuBarBounds.height());
        rightX = revertLabelBounds.x() - ACTION_LABEL_GAP;
        saveLabelBounds = new Bounds(rightX - saveWidth, menuBarBounds.y(), saveWidth, menuBarBounds.height());

        boolean saveHovered = actionsEnabled && !showSaveConfirm && saveLabelBounds.contains(mouseX, mouseY);
        boolean revertHovered = actionsEnabled && !showRevertConfirm && revertLabelBounds.contains(mouseX, mouseY);
        drawActionLabel(context, saveLabelBounds, saveLabel, actionsEnabled, showSaveConfirm, saveHovered);
        drawActionLabel(context, revertLabelBounds, revertLabel, actionsEnabled, showRevertConfirm, revertHovered);
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
            if (!fileMenuOpen) {
                renderEmptyState(context, bounds);
            }
            bodyBounds = new Bounds(0, 0, 0, 0);
            return;
        }
        if (fileMenuOpen) {
            // The open dropdown (drawn afterward, on top, by the host) already covers this whole
            // area, and nothing here can safely share space with it anyway: an item icon renders
            // through Minecraft's 3D item pipeline, which doesn't reliably respect normal 2D draw
            // order against content drawn after it, and squeezing the header/body into whatever's
            // left below the dropdown risks pushing rows past the window's own fixed bottom edge
            // instead of scrolling within it. Simplest and fully robust: draw none of it while open.
            headerBounds = new Bounds(bounds.x(), bounds.y(), 0, 0);
            bodyBounds = new Bounds(0, 0, 0, 0);
            return;
        }

        int boxHeight = Math.min(bounds.height(), previewBoxHeight());
        headerBounds = new Bounds(bounds.x(), bounds.y(), LEFT_BOX_WIDTH, boxHeight);
        renderPreviewBox(context, headerBounds, stack);

        int bodyX = bounds.x() + LEFT_BOX_WIDTH + COLUMN_GAP;
        bodyBounds = new Bounds(bodyX, bounds.y(), Math.max(0, bounds.x() + bounds.width() - bodyX), bounds.height());
        if (activeScreen != null) {
            activeScreen.render(context, bodyBounds);
        } else if (infoSelected) {
            traceList.render(context, bodyBounds);
        } else {
            valuesLayout.render(context, bodyBounds);
        }
    }

    /**
     * The left-hand preview box: this item's icon, name, and resource id in a small framed card —
     * same visual family as the Hub Home page's {@code EntityPreviewBox} (rounded rect, dark
     * background, accent border) but sized for a flat icon rather than a stretched 3D render: fixed
     * width, content-driven height, centered text instead of a name strip beneath a stage.
     */
    private void renderPreviewBox(RenderContext context, Bounds box, ItemStack stack) {
        Theme theme = context.theme();
        int accent = theme.color(ThemeKey.BORDER_HOVER);
        context.drawRoundedRect(box.x(), box.y(), box.width(), box.height(), 1, BOX_BACKGROUND, accent);

        int innerWidth = Math.max(0, box.width() - 2 * BOX_PADDING);
        int slotX = box.x() + (box.width() - ItemSlotComponent.SIZE) / 2;
        int slotY = box.y() + BOX_PADDING;
        slot.render(context, new Bounds(slotX, slotY, ItemSlotComponent.SIZE, ItemSlotComponent.SIZE));

        int nameY = slotY + ItemSlotComponent.SIZE + LINE_GAP;
        String name = OptionStyle.fit(context, stack.getHoverName().getString(), NAME_TEXT_SCALE, innerWidth);
        context.drawText(name, box.x() + (box.width() - context.textWidth(name, NAME_TEXT_SCALE)) / 2, nameY,
                theme.color(ThemeKey.TEXT_PRIMARY), NAME_TEXT_SCALE);

        String idLine = sourceId();
        if (idLine != null) {
            int idY = nameY + NAME_TEXT_HEIGHT + LINE_GAP;
            String fittedId = OptionStyle.fit(context, idLine, ID_TEXT_SCALE, innerWidth);
            context.drawText(fittedId, box.x() + (box.width() - context.textWidth(fittedId, ID_TEXT_SCALE)) / 2, idY,
                    theme.color(ThemeKey.TEXT_SECONDARY), ID_TEXT_SCALE);
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

    /**
     * Right-aligned Save/Revert label: dimmed while {@code enabled} is false, highlighted the same
     * way File/Info are while {@code hovered}, and shown in {@link #CONFIRM_COLOR} without a hover
     * highlight while {@code confirming} (it already reads "Saved ✓"/"Reverted ✓" by that point, so
     * there's nothing left to invite another click).
     */
    private void drawActionLabel(RenderContext context, Bounds bounds, String label, boolean enabled, boolean confirming, boolean hovered) {
        if (hovered) {
            context.fillRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), OptionStyle.dimmed(OptionStyle.ACCENT));
        }
        int color = confirming ? CONFIRM_COLOR
                : enabled ? OptionStyle.ACCENT : OptionStyle.dimmed(context.theme().color(ThemeKey.TEXT_SECONDARY));
        context.drawText(label, bounds.x() + MENU_LABEL_PADDING, bounds.y() + (bounds.height() - 7) / 2, color, OptionStyle.TEXT_SCALE);
    }

    /**
     * Pure navigation — a "Values" entry for the built-in view plus one entry per {@link
     * ItemEditorScreenProvider} registered for {@link #modId} (e.g. Nourished's "Options" screen).
     * Nothing here saves or reverts anything; that's the header's own Save/Revert controls (see
     * {@link #renderMenuBar}), acting on whichever page is selected. Rebuilt fresh each time the
     * dropdown draws, so it always reflects the current provider registrations and selection.
     */
    private List<MenuEntry> buildFileMenuEntries() {
        List<MenuEntry> entries = new ArrayList<>();
        entries.add(new MenuEntry("Values", () -> selectScreen(null), activeScreen == null));
        for (ItemEditorScreenProvider provider : ItemEditorScreenProviderRegistry.get(modId)) {
            entries.add(new MenuEntry(provider.menuLabel(), () -> selectScreen(provider), activeScreenProvider == provider));
        }
        return entries;
    }

    /** Switches the body to {@code provider}'s screen (built fresh for the current item), or back to the built-in Values view when {@code provider} is null. */
    private void selectScreen(@Nullable ItemEditorScreenProvider provider) {
        infoSelected = false;
        activeScreenProvider = provider;
        activeScreen = provider == null ? null : provider.buildScreen(modId, sourceId(), slot.item());
    }

    private static int menuEntriesHeight(List<MenuEntry> entries) {
        int height = 0;
        for (MenuEntry e : entries) {
            height += e.isSeparator() ? MENU_SEPARATOR_HEIGHT : MENU_ITEM_HEIGHT;
        }
        return height;
    }

    private void renderFileMenu(RenderContext context) {
        List<MenuEntry> entries = buildFileMenuEntries();
        int width = MENU_MIN_WIDTH;
        for (MenuEntry e : entries) {
            if (!e.isSeparator()) {
                width = Math.max(width, context.textWidth(e.label(), OptionStyle.TEXT_SCALE) + 2 * MENU_LABEL_PADDING + 4);
            }
        }
        int height = menuEntriesHeight(entries);

        int x = fileLabelBounds.x();
        int y = fileLabelBounds.y() + fileLabelBounds.height() + 1;
        int background = context.theme().color(ThemeKey.PANEL_BACKGROUND);
        int border = context.theme().color(ThemeKey.BORDER);
        context.drawRoundedRect(x, y, width, height, 1, background, border);

        fileMenuRows.clear();
        int rowY = y;
        for (MenuEntry e : entries) {
            if (e.isSeparator()) {
                context.fillRect(x + 2, rowY + MENU_SEPARATOR_HEIGHT / 2, width - 4, 1, border);
                fileMenuRows.add(new MenuRow(new Bounds(x, rowY, width, MENU_SEPARATOR_HEIGHT), null));
                rowY += MENU_SEPARATOR_HEIGHT;
            } else {
                int color = e.active() ? OptionStyle.ACCENT : context.theme().color(ThemeKey.TEXT_PRIMARY);
                context.drawText(e.label(), x + 3, rowY + (MENU_ITEM_HEIGHT - 7) / 2, color, OptionStyle.TEXT_SCALE);
                fileMenuRows.add(new MenuRow(new Bounds(x, rowY, width, MENU_ITEM_HEIGHT), e.action()));
                rowY += MENU_ITEM_HEIGHT;
            }
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
            for (MenuRow row : fileMenuRows) {
                if (row.action() != null && row.bounds().contains((int) mouseX, (int) mouseY)) {
                    row.action().run();
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
            if (activeScreen != null) {
                // A mod screen was showing: Info always switches straight to the trace, rather than
                // toggling against whatever infoSelected was left at before the screen took over.
                selectScreen(null);
                infoSelected = true;
            } else {
                infoSelected = !infoSelected;
            }
            return true;
        }
        if (sourceId() != null && saveLabelBounds.contains((int) mouseX, (int) mouseY)) {
            if (activeScreen != null) {
                activeScreen.save();
            } else {
                save();
            }
            saveConfirmUntilMs = System.currentTimeMillis() + CONFIRM_DURATION_MS;
            return true;
        }
        if (sourceId() != null && revertLabelBounds.contains((int) mouseX, (int) mouseY)) {
            if (activeScreen != null) {
                activeScreen.revert();
            } else {
                revert();
            }
            revertConfirmUntilMs = System.currentTimeMillis() + CONFIRM_DURATION_MS;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!bodyBounds.contains((int) mouseX, (int) mouseY)) {
            return false;
        }
        if (activeScreen != null) {
            return activeScreen.mouseClicked(mouseX, mouseY, button);
        }
        return !infoSelected && valuesLayout.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (activeScreen != null) {
            return activeScreen.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        return !infoSelected && valuesLayout.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (activeScreen != null) {
            return activeScreen.mouseReleased(mouseX, mouseY, button);
        }
        return !infoSelected && valuesLayout.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!bodyBounds.contains((int) mouseX, (int) mouseY)) {
            return false;
        }
        if (activeScreen != null) {
            return activeScreen.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return infoSelected ? traceList.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
                : valuesLayout.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
