package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.toolbox.BooleanSetter;
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

        public PanelBuilder toggle(String label, BooleanSupplier getter, BooleanSetter setter, Runnable onCommit) {
            layout.addRow(new ToggleOption(label, getter, setter, onCommit));
            return this;
        }

        /** Choice button over {@code labels}, bound to an index into it; a click advances and wraps. */
        public PanelBuilder cycle(String label, String[] labels, IntSupplier indexGetter, IntConsumer indexSetter, Runnable onCommit) {
            layout.addRow(new CycleOption(label, labels, indexGetter, indexSetter, onCommit));
            return this;
        }

        /** Adds the shared Padding slider, over the module's own {@code persistence} store under {@code panelId}. */
        public PanelBuilder padding(PersistenceProvider persistence, String panelId) {
            ModuleOptionRows.addPadding(layout, persistence, panelId);
            return this;
        }

        /** Adds independent Text size and Icon size sliders over {@code persistence} — see {@link MarieModuleSettings#textScale}/{@link MarieModuleSettings#iconScale} for reading them back. */
        public PanelBuilder textAndIconSizes(PersistenceProvider persistence, String panelId) {
            ModuleOptionRows.addSizes(layout, persistence, panelId);
            return this;
        }

        /** Adds a Bar size slider (bar length and thickness, and the value text at the bar's end) over {@code persistence}; read it back with {@link MarieModuleSettings#barScale}. */
        public PanelBuilder barSize(PersistenceProvider persistence, String panelId) {
            ModuleOptionRows.addBarSize(layout, persistence, panelId);
            return this;
        }

        /** Adds the "Move Text", "Move Icons" and "Move Bars" toggles (mutually exclusive) over {@code persistence}; see {@link MarieModuleSettings#isMoveBarsEnabled}. */
        public PanelBuilder moveToggles(PersistenceProvider persistence, String panelId) {
            ModuleOptionRows.addMoveToggles(layout, persistence, panelId);
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
