package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.component.MarieComponent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

import static dev.marie.framework.ui.api.MarieModuleSettings.MAX_BRIGHTNESS;
import static dev.marie.framework.ui.api.MarieModuleSettings.MIN_BRIGHTNESS;

@ApiStatus.Experimental
/** Builder returned by {@link MarieModuleSettings#standardPanel}. */
public final class StandardPanelBuilder {

    private final String title;
    private final PersistenceProvider store;
    private final String panelId;
    private DoubleSupplier opacity;
    private DoubleConsumer setOpacity;
    private boolean hasOpacityDefault;
    private double opacityDefault;
    private DoubleSupplier textBrightness;
    private DoubleConsumer setTextBrightness;
    private DoubleSupplier iconBrightness;
    private DoubleConsumer setIconBrightness;
    private Runnable onCommit = () -> {};
    private Runnable onReset = () -> {};
    private boolean bars = true;
    private boolean icons = true;
    private boolean header;
    private boolean moveText = true;
    private boolean hideText = true;
    private boolean storedBrightness;
    private boolean padding = true;
    private boolean moveAndHide = true;
    private boolean sizes = true;
    private DoubleSupplier backgroundShade;
    private DoubleConsumer setBackgroundShade;
    private DoubleSupplier borderOpacity;
    private DoubleConsumer setBorderOpacity;
    private DoubleSupplier borderShade;
    private DoubleConsumer setBorderShade;
    private Consumer<MarieToolbox.PanelBuilder> layoutRows;
    private Consumer<MarieToolbox.PanelBuilder> behaviorRows;
    private Consumer<MarieToolbox.PanelBuilder> styleRows;
    private Consumer<MarieToolbox.PanelBuilder> extraTabs;

    StandardPanelBuilder(String title, PersistenceProvider store, String panelId) {
        this.title = title;
        this.store = store;
        this.panelId = panelId;
    }

    /** Background opacity slider over 0..1. */
    public StandardPanelBuilder opacity(DoubleSupplier getter, DoubleConsumer setter) {
        this.opacity = getter;
        this.setOpacity = setter;
        return this;
    }

    /** Same, with the value "Reset This Tab" puts it back to. */
    public StandardPanelBuilder opacity(DoubleSupplier getter, DoubleConsumer setter, double defaultValue) {
        opacity(getter, setter);
        this.hasOpacityDefault = true;
        this.opacityDefault = defaultValue;
        return this;
    }

    public StandardPanelBuilder textBrightness(DoubleSupplier getter, DoubleConsumer setter) {
        this.textBrightness = getter;
        this.setTextBrightness = setter;
        return this;
    }

    public StandardPanelBuilder iconBrightness(DoubleSupplier getter, DoubleConsumer setter) {
        this.iconBrightness = getter;
        this.setIconBrightness = setter;
        return this;
    }

    /** Background shade slider (-1..1, darker to lighter), in the Background group next to opacity. */
    public StandardPanelBuilder backgroundShade(DoubleSupplier getter, DoubleConsumer setter) {
        this.backgroundShade = getter;
        this.setBackgroundShade = setter;
        return this;
    }

    /** Border opacity slider (0..1), in the Border group. */
    public StandardPanelBuilder borderOpacity(DoubleSupplier getter, DoubleConsumer setter) {
        this.borderOpacity = getter;
        this.setBorderOpacity = setter;
        return this;
    }

    /** Border shade slider (-1..1, darker to lighter), in the Border group. */
    public StandardPanelBuilder borderShade(DoubleSupplier getter, DoubleConsumer setter) {
        this.borderShade = getter;
        this.setBorderShade = setter;
        return this;
    }

    /**
     * Lets the caller add its own rows to the Layout tab, before its "Reset This Tab" button; {@code rows}
     * receives the builder once, when {@link #build()} runs. A group the caller opens is closed for it afterwards.
     */
    public StandardPanelBuilder layoutRows(Consumer<MarieToolbox.PanelBuilder> rows) {
        this.layoutRows = rows;
        return this;
    }

    /** Same for the Behavior tab: {@code rows} runs before the standard Move and Hide groups. */
    public StandardPanelBuilder behaviorRows(Consumer<MarieToolbox.PanelBuilder> rows) {
        this.behaviorRows = rows;
        return this;
    }

    /** Called after each finished edit of a value you bound (e.g. to save your config file). */
    public StandardPanelBuilder onCommit(Runnable onCommit) {
        this.onCommit = onCommit;
        return this;
    }

    /** Leaves out Bar size and Move Bars, for a module that has no bars. */
    public StandardPanelBuilder withoutBars() {
        this.bars = false;
        return this;
    }

    /** Leaves out Move Icons, for a module that draws no icons. */
    public StandardPanelBuilder withoutIcons() {
        this.icons = false;
        return this;
    }

    /** Leaves out the Padding slider, for a window whose module has no padding to adjust. */
    public StandardPanelBuilder withoutPadding() {
        this.padding = false;
        return this;
    }

    /**
     * Leaves out the Move and Hide groups (and Reset Positions), for a window that configures a whole screen
     * rather than one module's text, icons and bars — its own content has nothing to move or hide.
     */
    public StandardPanelBuilder withoutMoveAndHide() {
        this.moveAndHide = false;
        return this;
    }

    /** Leaves out the Sizes group (Text/Icon/Bar size), for a window whose module has no text or icon size of its own. */
    public StandardPanelBuilder withoutSizes() {
        this.sizes = false;
        return this;
    }

    /** Adds a Move Header toggle, for a module with a title separate from its body text (Move Text then moves the body only). */
    public StandardPanelBuilder withHeader() {
        this.header = true;
        return this;
    }

    /** Leaves out "Move Text", for a module whose body content already moves under some other toggle here (e.g. "Move Bars") and has nothing left for "Move Text" to actually move. */
    public StandardPanelBuilder withoutMoveText() {
        this.moveText = false;
        return this;
    }

    /** Leaves out "Hide Text", for a module whose text serves no purpose hiding on its own. */
    public StandardPanelBuilder withoutHideText() {
        this.hideText = false;
        return this;
    }

    /** Adds text/icon brightness sliders over values kept in the panel's own store (see {@link MarieModuleSettings#withDisplaySettings}) instead of caller-bound getters and setters. */
    public StandardPanelBuilder storedBrightness() {
        this.storedBrightness = true;
        return this;
    }

    /** Runs when "Reset Positions" is clicked, after the icon/bar offsets and move modes are reset — reset here whatever offset you store yourself. */
    public StandardPanelBuilder onReset(Runnable onReset) {
        this.onReset = onReset;
        return this;
    }

    /**
     * Lets the caller add rows of its own to the end of the Style tab, before its "Reset This Tab" button (so that
     * button resets them too, for rows given a {@code defaultValue}); {@code rows} receives the builder once, when
     * {@link #build()} runs, positioned on the Style tab.
     */
    public StandardPanelBuilder styleRows(Consumer<MarieToolbox.PanelBuilder> rows) {
        this.styleRows = rows;
        return this;
    }

    /**
     * Lets the caller append tabs of its own after the standard ones (e.g. a {@code colorTab}); {@code more}
     * receives the builder once, when {@link #build()} runs, positioned after the last standard tab.
     */
    public StandardPanelBuilder extraTabs(Consumer<MarieToolbox.PanelBuilder> more) {
        this.extraTabs = more;
        return this;
    }

    public MarieComponent build() {
        MarieToolbox.PanelBuilder panel = MarieToolbox.panel(title).tab(label("layout"));
        if (padding) {
            panel.padding(store, panelId);
        }
        if (layoutRows != null) {
            layoutRows.accept(panel);
            panel.endSection();
        }
        panel.resetTab().tab(label("behavior"));
        if (behaviorRows != null) {
            behaviorRows.accept(panel);
            panel.endSection();
        }
        if (moveAndHide) {
            panel.moveToggles(store, panelId, bars, icons, header, moveText, hideText).resetPositions(store, panelId, onReset);
        }
        panel.tab(label("appearance"));
        if (sizes) {
            panel.section(text("config.marieslib.moduleoptions.section.sizes")).textAndIconSizes(store, panelId);
            if (bars) {
                panel.barSize(store, panelId);
            }
        }
        if (storedBrightness || textBrightness != null || iconBrightness != null) {
            panel.section(text("config.marieslib.moduleoptions.section.brightness"));
        }
        if (storedBrightness) {
            panel.storedBrightness(store, panelId);
        }
        if (textBrightness != null) {
            panel.slider(text("config.marieslib.moduleoptions.textBrightness"), textBrightness, setTextBrightness,
                    MIN_BRIGHTNESS, MAX_BRIGHTNESS, 0.01d, onCommit).defaultValue(1.0d);
        }
        if (iconBrightness != null) {
            panel.slider(text("config.marieslib.moduleoptions.iconBrightness"), iconBrightness, setIconBrightness,
                    MIN_BRIGHTNESS, MAX_BRIGHTNESS, 0.01d, onCommit).defaultValue(1.0d);
        }
        if (opacity != null || backgroundShade != null) {
            panel.section(text("config.marieslib.moduleoptions.section.background"));
        }
        if (opacity != null) {
            panel.slider(text("config.marieslib.moduleoptions.backgroundOpacity"), opacity, setOpacity, 0.0d, 1.0d, 0.01d, onCommit);
            if (hasOpacityDefault) {
                panel.defaultValue(opacityDefault);
            }
        }
        if (backgroundShade != null) {
            panel.slider(text("config.marieslib.moduleoptions.backgroundShade"), backgroundShade, setBackgroundShade,
                    -1.0d, 1.0d, 0.01d, onCommit).defaultValue(0.0d);
        }
        if (borderOpacity != null || borderShade != null) {
            panel.section(text("config.marieslib.moduleoptions.section.border"));
        }
        if (borderOpacity != null) {
            panel.slider(text("config.marieslib.moduleoptions.borderOpacity"), borderOpacity, setBorderOpacity,
                    0.0d, 1.0d, 0.01d, onCommit).defaultValue(1.0d);
        }
        if (borderShade != null) {
            panel.slider(text("config.marieslib.moduleoptions.borderShade"), borderShade, setBorderShade,
                    -1.0d, 1.0d, 0.01d, onCommit).defaultValue(0.0d);
        }
        panel.endSection();
        if (styleRows != null) {
            styleRows.accept(panel);
        }
        panel.resetTab();
        if (extraTabs != null) {
            extraTabs.accept(panel);
        }
        return panel.build();
    }

    private static String label(String tab) {
        return text("config.marieslib.moduleoptions.tab." + tab);
    }

    private static String text(String key) {
        return Component.translatable(key).getString();
    }
}
