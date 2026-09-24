package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.toolbox.BooleanSetter;
import dev.marie.framework.ui.toolbox.ButtonOption;
import dev.marie.framework.ui.toolbox.colorpicker.ColorSlot;
import dev.marie.framework.ui.toolbox.CycleOption;
import dev.marie.framework.ui.toolbox.ModuleOptionRows;
import dev.marie.framework.ui.toolbox.OptionLayout;
import dev.marie.framework.ui.toolbox.SliderOption;
import dev.marie.framework.ui.toolbox.ToggleOption;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Public facade for tabbed option panels: a strip of tabs above stacked sliders, toggles and
 * cycle buttons, built with a fluent builder and returned as a plain {@link MarieComponent} for a
 * module box to host (e.g. via {@code ScaleConfigEntry#withContent}).
 *
 * <p>Every option edits a value the caller owns through a getter and setter — the panel stores
 * nothing — and reports each finished edit through {@code onCommit}, where the caller persists it.
 * Sliders commit on release (or per scroll notch), toggles and cycle buttons on click. Slider
 * {@code min}/{@code max}/{@code step} are the caller's, so the UI never writes a value the caller's
 * own validation would reject.
 *
 * <pre>{@code
 * MarieComponent panel = MarieToolbox.panel("My panel")
 *         .tab("Layout")
 *             .slider("Scale", () -> cfg.scale, v -> cfg.scale = v, 0.5, 2.0, 0.05, cfg::save)
 *             .toggle("Compact", () -> cfg.compact, v -> cfg.compact = v, cfg::save)
 *             .cycle("Corner", new String[]{"Top left", "Top right"}, cfg::corner, cfg::setCorner, cfg::save)
 *         .tab("Other")
 *             .toggle("Extra", () -> cfg.extra, v -> cfg.extra = v, cfg::save)
 *                 .enabledWhen(() -> cfg.compact)
 *         .build();
 * }</pre>
 */
@ApiStatus.Experimental
public final class MarieToolbox {

    private MarieToolbox() {}

    /** Starts a panel. {@code title} becomes the built component's {@link MarieComponent#id()}; a host draws its own header. */
    public static PanelBuilder panel(String title) {
        return new PanelBuilder(title);
    }

    /** Fluent builder returned by {@link #panel}. Options attach to the most recently added tab. */
    public static final class PanelBuilder {

        private final OptionLayout layout;

        private PanelBuilder(String title) {
            this.layout = new OptionLayout(title);
        }

        public PanelBuilder tab(String title) {
            layout.addTab(title);
            return this;
        }

        /** Percent slider over {@code [min, max]} in {@code step} increments; {@code getter}/{@code setter} use the same unit as {@code min}/{@code max} (1.0 displays as 100%). */
        public PanelBuilder slider(String label, DoubleSupplier getter, DoubleConsumer setter,
                                   double min, double max, double step, Runnable onCommit) {
            layout.addRow(new SliderOption(label, getter, setter, min, max, step, onCommit));
            return this;
        }

        /** Whole-number slider over {@code [min, max]} in {@code step} increments, shown as e.g. "60 px" ({@code unit} is the suffix); same live-setter and commit-on-release behavior as {@link #slider}. Give its reset value with {@code defaultValue(double)}: the {@code int} overload of {@code defaultValue} is for cycles and throws on a slider. */
        public PanelBuilder intSlider(String label, IntSupplier getter, IntConsumer setter,
                                      int min, int max, int step, String unit, Runnable onCommit) {
            layout.addRow(SliderOption.ofInt(label, getter, setter, min, max, step, unit, onCommit));
            return this;
        }

        /** One-shot action row: {@code label} on the left, {@code caption} (e.g. "RESET") on the right; a click runs {@code action}, then {@code onCommit}. */
        public PanelBuilder button(String label, String caption, Runnable action, Runnable onCommit) {
            layout.addRow(new ButtonOption(label, caption, action, onCommit));
            return this;
        }

        public PanelBuilder toggle(String label, BooleanSupplier getter, BooleanSetter setter, Runnable onCommit) {
            layout.addRow(new ToggleOption(label, getter, setter, onCommit));
            return this;
        }

        /** Choice button over {@code labels}, bound to an index into it; a click advances and wraps. */
        public PanelBuilder cycle(String label, String[] labels, IntSupplier indexGetter, IntConsumer indexSetter, Runnable onCommit) {
            layout.addRow(new CycleOption(label, labels, indexGetter, indexSetter, onCommit));
            return this;
        }

        /** Starts a tab of color slots — the same as {@link #tab}, named for what {@link #color} adds to it. */
        public PanelBuilder colorTab(String title) {
            layout.addTab(title);
            return this;
        }

        /**
         * Adds a color slot (a swatch row) to the current tab. Clicking it asks the hosting window to open
         * a picker for it; consumers never manage that window. Safe to call in a loop, for slots only known at runtime.
         *
         * <p><b>RGB only.</b> The picker never edits alpha (keep that on an opacity slider). {@code setter}
         * receives {@code 0xRRGGBB} with the high byte zero, live on every drag tick where the value changes, and
         * {@code defaultValue} is read the same way. If the stored int is ARGB, <b>the setter must keep the
         * existing alpha bits</b>, e.g. {@code rgb -> stored = (stored & 0xFF000000) | rgb}. Of {@code getter}'s
         * result only the low 24 bits are read. {@code onCommit} runs once when the drag is released and after Reset;
         * persisting the value is the caller's job — the toolbox stores nothing. Reset calls {@code setter} with
         * {@code defaultValue}, then {@code onCommit}.
         */
        public PanelBuilder color(String label, IntSupplier getter, IntConsumer setter, int defaultValue, Runnable onCommit) {
            layout.addColorSlot(new ColorSlot(label, getter, setter, defaultValue, onCommit));
            return this;
        }

        /**
         * As {@link #color(String, IntSupplier, IntConsumer, int, Runnable)}, plus {@code onCancel}: run when the
         * picker is closed or moved to another slot, so a setter that only previews a value can discard a preview
         * that was never committed. It may run with nothing pending, so it must be safe to call then.
         */
        public PanelBuilder color(String label, IntSupplier getter, IntConsumer setter, int defaultValue, Runnable onCommit, Runnable onCancel) {
            layout.addColorSlot(new ColorSlot(label, getter, setter, defaultValue, onCommit, onCancel));
            return this;
        }

        /** Adds the shared Padding slider, over the module's own {@code persistence} store under {@code panelId}. */
        public PanelBuilder padding(PersistenceProvider persistence, String panelId) {
            ModuleOptionRows.addPadding(layout, persistence, panelId);
            return this;
        }

        /** Adds independent Text size and Icon size sliders over {@code persistence} — see {@link MarieModuleSettings#textScale}/{@link MarieModuleSettings#iconScale} for reading them back. */
        public PanelBuilder textAndIconSizes(PersistenceProvider persistence, String panelId) {
            return textAndIconSizes(persistence, panelId, null);
        }

        /** Same, with the Text size row's label overridden to {@code textLabelKey} ({@code null}: the standard "Text size" label) — see {@link ModuleOptionRows#addSizes(OptionLayout, PersistenceProvider, String, String)}. */
        public PanelBuilder textAndIconSizes(PersistenceProvider persistence, String panelId, String textLabelKey) {
            ModuleOptionRows.addSizes(layout, persistence, panelId, textLabelKey);
            return this;
        }

        /**
         * Same, but {@code showIconSize} false leaves out the Icon size row — see {@link
         * ModuleOptionRows#addSizes(OptionLayout, PersistenceProvider, String, String, boolean)}.
         */
        public PanelBuilder textAndIconSizes(PersistenceProvider persistence, String panelId, String textLabelKey, boolean showIconSize) {
            ModuleOptionRows.addSizes(layout, persistence, panelId, textLabelKey, showIconSize);
            return this;
        }

        /** Adds a Bar size slider (bar length and thickness, and the value text at the bar's end) over {@code persistence}; read it back with {@link MarieModuleSettings#barScale}. */
        public PanelBuilder barSize(PersistenceProvider persistence, String panelId) {
            ModuleOptionRows.addBarSize(layout, persistence, panelId);
            return this;
        }

        /**
         * Adds a Header size slider (a module's title/header text, independent of Text/Icon/Bar size) over
         * {@code persistence}; read it back with {@link MarieModuleSettings#headerScale}. {@code labelKey}
         * {@code null}: the standard "Header size" label.
         */
        public PanelBuilder headerSize(PersistenceProvider persistence, String panelId, String labelKey) {
            ModuleOptionRows.addHeaderSize(layout, persistence, panelId, labelKey);
            return this;
        }

        /** Adds the "Move Text", "Move Icons" and "Move Bars" toggles (mutually exclusive) over {@code persistence}; see {@link MarieModuleSettings#isMoveBarsEnabled}. */
        public PanelBuilder moveToggles(PersistenceProvider persistence, String panelId) {
            ModuleOptionRows.addMoveToggles(layout, persistence, panelId);
            return this;
        }

        /** Same as {@link #moveToggles(PersistenceProvider, String)} but with {@code bars} false, leaves out "Move Bars" (for modules without bars). */
        public PanelBuilder moveToggles(PersistenceProvider persistence, String panelId, boolean bars) {
            ModuleOptionRows.addMoveToggles(layout, persistence, panelId, bars);
            return this;
        }

        /** Same, choosing whether "Move Icons" appears and whether a "Move Header" toggle is added. */
        public PanelBuilder moveToggles(PersistenceProvider persistence, String panelId, boolean bars, boolean icons, boolean header) {
            ModuleOptionRows.addMoveToggles(layout, persistence, panelId, bars, icons, header);
            return this;
        }

        /** Same, additionally leaving out "Move Text" when {@code moveText} is false (for a module whose body content moves under some other toggle here and has nothing left for "Move Text" to move). */
        public PanelBuilder moveToggles(PersistenceProvider persistence, String panelId, boolean bars, boolean icons, boolean header, boolean moveText) {
            ModuleOptionRows.addMoveToggles(layout, persistence, panelId, bars, icons, header, moveText);
            return this;
        }

        /** Same, additionally leaving out "Hide Text" when {@code hideText} is false (for a module whose text serves no purpose hiding on its own). */
        public PanelBuilder moveToggles(PersistenceProvider persistence, String panelId, boolean bars, boolean icons, boolean header, boolean moveText, boolean hideText) {
            ModuleOptionRows.addMoveToggles(layout, persistence, panelId, bars, icons, header, moveText, hideText);
            return this;
        }

        /** Same, additionally adding "Hide Header" when {@code hideHeader} is true — independent of "Hide Text" — for a module with a title/header separate from its body text that should be hideable on its own. */
        public PanelBuilder moveToggles(PersistenceProvider persistence, String panelId, boolean bars, boolean icons, boolean header, boolean moveText, boolean hideText, boolean hideHeader) {
            ModuleOptionRows.addMoveToggles(layout, persistence, panelId, bars, icons, header, moveText, hideText, hideHeader);
            return this;
        }

        /** Adds text and icon brightness sliders over values kept in {@code persistence} (read back with {@link MarieModuleSettings#textBrightness}/{@link MarieModuleSettings#iconBrightness}), for modules whose brightness isn't a config value. */
        public PanelBuilder storedBrightness(PersistenceProvider persistence, String panelId) {
            ModuleOptionRows.addStoredBrightness(layout, persistence, panelId);
            return this;
        }

        /**
         * Adds a "Reset Positions" button: icon and bar offsets go back to zero (saved), all move modes switch
         * off, then {@code hostReset} runs for anything the host stores itself, such as its text offset.
         */
        public PanelBuilder resetPositions(PersistenceProvider persistence, String panelId, Runnable hostReset) {
            ModuleOptionRows.addResetPositions(layout, persistence, panelId, hostReset);
            return this;
        }

        /**
         * Adds a "Reset Module" button: everything {@link #resetPositions} does, plus the module's own
         * drag/resize position/size, its icon/bar size and text/icon brightness, and every option on
         * every tab that has a default (sizes, background/border, colors) — see {@link
         * ModuleOptionRows#addResetEverything}.
         */
        public PanelBuilder resetEverything(PersistenceProvider persistence, String panelId, Runnable hostReset) {
            ModuleOptionRows.addResetEverything(layout, persistence, panelId, hostReset);
            return this;
        }

        /**
         * Starts a collapsible group headed {@code title} on the current tab (collapsed until the player opens
         * it); every option added until {@link #endSection()} or the next {@code tab}/{@code colorTab} goes
         * inside it. Calling it again with the same title on the same tab resumes that group. {@code defaultValue}
         * and {@code enabledWhen} still act on the option just added, and {@link #resetTab()} resets grouped
         * options too. Put {@code resetTab()} after {@code endSection()} so the button stays visible.
         */
        public PanelBuilder section(String title) {
            layout.openSection(title);
            return this;
        }

        /** Ends the group started by {@link #section(String)}; later options go back to the tab itself. */
        public PanelBuilder endSection() {
            layout.closeSection();
            return this;
        }

        /** Sets the value the slider just added returns to on {@link #resetTab()}. */
        public PanelBuilder defaultValue(double value) {
            layout.defaultToLast(value);
            return this;
        }

        /** Sets the value the toggle just added returns to on {@link #resetTab()}. */
        public PanelBuilder defaultValue(boolean value) {
            layout.defaultToLast(value);
            return this;
        }

        /** Sets the choice index the cycle just added returns to on {@link #resetTab()}. */
        public PanelBuilder defaultValue(int index) {
            layout.defaultToLast(index);
            return this;
        }

        /** Adds a "Reset This Tab" button: every option on the current tab that has a default (see {@code defaultValue}, and the shared module rows) goes back to it. Add it after the tab's options. */
        public PanelBuilder resetTab() {
            ModuleOptionRows.addResetTab(layout);
            return this;
        }

        /** Greys out the option just added and ignores input on it while {@code enabled} reports false. */
        public PanelBuilder enabledWhen(BooleanSupplier enabled) {
            layout.enabledWhenLast(enabled);
            return this;
        }

        /** @throws IllegalStateException if no tab was added */
        public MarieComponent build() {
            if (layout.tabCount() == 0) {
                throw new IllegalStateException("a panel needs at least one tab");
            }
            return layout;
        }
    }
}
