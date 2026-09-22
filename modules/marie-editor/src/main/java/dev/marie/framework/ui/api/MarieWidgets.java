package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.component.Constraint;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.geometry.Bounds;
import dev.marie.framework.ui.toolbox.CycleOption;
import dev.marie.framework.ui.toolbox.OptionRow;
import dev.marie.framework.ui.toolbox.OptionStyle;
import dev.marie.framework.ui.toolbox.SectionRow;
import dev.marie.framework.ui.toolbox.SliderOption;
import dev.marie.framework.ui.toolbox.TabRow;
import dev.marie.framework.ui.toolbox.ToggleOption;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * The one entry point for MariesLib's standalone UI controls — the same tab strip, buttons, sliders, toggles,
 * choice rows and collapsible groups every module options window is built from, usable on their own in any
 * screen or component. A module window that wants the whole standard layout should use
 * {@link MarieModuleSettings#standardPanel} instead; these are the parts.
 *
 * <p>Each factory returns a {@link MarieComponent}: give it {@link Bounds} to {@code render} and forward the
 * mouse events. A control keeps no state of its own beyond what it draws (a tab bar remembers its selection;
 * a group remembers whether it is open); values live behind the getters and setters you pass, and {@code onCommit}
 * runs once per finished edit, for you to save.
 *
 * <pre>{@code
 * MarieWidgets.TabBar tabs = MarieWidgets.tabBar("modes", "Layout", "Style").onChange(i -> show(i));
 * MarieComponent size = MarieWidgets.slider("Size", cfg::size, cfg::setSize, 0.5, 2.0, 0.05, cfg::save);
 * MarieComponent group = MarieWidgets.section("Advanced", size, MarieWidgets.toggle("Debug", cfg::debug, cfg::setDebug, cfg::save));
 * MarieWidgets.Button reset = MarieWidgets.button("reset", "Reset", cfg::reset);
 * }</pre>
 */
@ApiStatus.Experimental
public final class MarieWidgets {

    /** Height of a tab strip, in pixels. */
    public static final int TAB_HEIGHT = 12;
    /** Height of a {@link Button}, in pixels. */
    public static final int BUTTON_HEIGHT = 12;

    private MarieWidgets() {}

    /** A strip of equal-width rounded tabs. */
    public static TabBar tabBar(String id, String... titles) {
        return new TabBar(id, titles);
    }

    /** A rounded, accent-outlined button that runs {@code action} when clicked. */
    public static Button button(String id, String caption, Runnable action) {
        return new Button(id, caption, action);
    }

    /** A slider row: label and value above a rounded bar with an arrow button at each end. {@code step} is what an arrow or a wheel notch moves it by. */
    public static MarieComponent slider(String label, DoubleSupplier getter, DoubleConsumer setter,
                                        double min, double max, double step, Runnable onCommit) {
        return new Row(label, new SliderOption(label, getter, setter, min, max, step, onCommit));
    }

    /** A whole-number slider showing {@code value + " " + unit}. */
    public static MarieComponent intSlider(String label, IntSupplier getter, IntConsumer setter,
                                           int min, int max, int step, String unit, Runnable onCommit) {
        return new Row(label, SliderOption.ofInt(label, getter, setter, min, max, step, unit, onCommit));
    }

    /** An ON/OFF row; a click flips it. */
    public static MarieComponent toggle(String label, BooleanSupplier getter, dev.marie.framework.ui.toolbox.BooleanSetter setter, Runnable onCommit) {
        return new Row(label, new ToggleOption(label, getter, setter, onCommit));
    }

    /** A choice row: shows {@code choices[index]}; a click advances to the next, wrapping. */
    public static MarieComponent choice(String label, String[] choices, IntSupplier getter, IntConsumer setter, Runnable onCommit) {
        return new Row(label, new CycleOption(label, choices, getter, setter, onCommit));
    }

    /** A collapsible group headed {@code title} holding {@code children} (built by this class), collapsed until clicked. */
    public static MarieComponent section(String title, MarieComponent... children) {
        SectionRow section = new SectionRow(title, title);
        for (MarieComponent child : children) {
            if (!(child instanceof Row row)) {
                throw new IllegalArgumentException("a section holds only rows made by MarieWidgets (slider, toggle, choice, section)");
            }
            section.add(row.row);
        }
        return new Row(title, section);
    }

    /** Tab strip. Remembers the selected tab; {@link #onChange} reports a click that moves it. */
    public static final class TabBar implements MarieComponent {

        private final String id;
        private final TabRow tabs = new TabRow();
        private IntConsumer onChange = i -> {};

        private TabBar(String id, String[] titles) {
            this.id = id;
            for (String title : titles) {
                tabs.addTab(title);
            }
        }

        public TabBar onChange(IntConsumer listener) {
            this.onChange = listener;
            return this;
        }

        public int selected() {
            return tabs.selected();
        }

        public TabBar select(int index) {
            tabs.select(index);
            return this;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Constraint constraint() {
            return Constraint.preferred(OptionStyle.PREFERRED_WIDTH, TAB_HEIGHT);
        }

        @Override
        public void render(RenderContext context, Bounds bounds) {
            tabs.render(context, new Bounds(bounds.x(), bounds.y(), bounds.width(), TAB_HEIGHT));
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) {
                return false;
            }
            int before = tabs.selected();
            if (!tabs.mouseClicked(mouseX, mouseY)) {
                return false;
            }
            if (tabs.selected() != before) {
                onChange.accept(tabs.selected());
            }
            return true;
        }
    }

    /** Rounded button. Draws its caption centred, dims and ignores clicks while {@link #enabledWhen} reports false. */
    public static final class Button implements MarieComponent {

        private final String id;
        private String caption;
        private final Runnable action;
        private BooleanSupplier enabled = () -> true;
        private Bounds bounds = new Bounds(0, 0, 0, 0);

        private Button(String id, String caption, Runnable action) {
            this.id = id;
            this.caption = caption;
            this.action = action;
        }

        public Button caption(String caption) {
            this.caption = caption;
            return this;
        }

        public Button enabledWhen(BooleanSupplier enabled) {
            this.enabled = enabled;
            return this;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Constraint constraint() {
            return Constraint.preferred(60, BUTTON_HEIGHT);
        }

        @Override
        public void render(RenderContext context, Bounds bounds) {
            this.bounds = new Bounds(bounds.x(), bounds.y(), bounds.width(), BUTTON_HEIGHT);
            OptionStyle.drawPillButton(context, this.bounds.x(), this.bounds.y(), this.bounds.width(), this.bounds.height(),
                    caption, OptionStyle.TEXT_SCALE, enabled.getAsBoolean());
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0 || !bounds.contains((int) mouseX, (int) mouseY)) {
                return false;
            }
            if (enabled.getAsBoolean()) {
                action.run();
            }
            return true;
        }
    }

    /** A toolbox row as a standalone component; its height is the row's own (a group's changes as it opens). */
    private static final class Row implements MarieComponent {

        private final String id;
        private final OptionRow row;

        private Row(String id, OptionRow row) {
            this.id = id;
            this.row = row;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Constraint constraint() {
            return Constraint.preferred(OptionStyle.PREFERRED_WIDTH, row.height());
        }

        @Override
        public void render(RenderContext context, Bounds bounds) {
            row.render(context, new Bounds(bounds.x(), bounds.y(), bounds.width(), row.height()));
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return button == 0 && row.mouseClicked(mouseX, mouseY);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            return row.mouseDragged(mouseX, mouseY);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            return row.mouseReleased(mouseX, mouseY);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            return row.mouseScrolled(mouseX, mouseY, scrollY);
        }
    }
}
