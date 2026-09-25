package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.edit.ContentScaleController;
import dev.marie.framework.ui.modulesettings.ModuleGlow;
import dev.marie.framework.ui.modulesettings.ModuleScales;
import dev.marie.framework.ui.modulesettings.ModuleStyle;
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
    private boolean textSize = true;
    private boolean iconSize = true;
    private boolean iconFollowsText = true;
    private boolean headerSize;
    private boolean hideHeader;
    private boolean iconInnerMove;
    private boolean iconInnerSize;
    private boolean shadow;
    private boolean glow;
    private boolean ownStyle;
    private String textSizeLabelKey;
    private String headerSizeLabelKey;
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

    /** Leaves out Move Icons and the Sizes group's Icon size row, for a module that draws no icons. */
    public StandardPanelBuilder withoutIcons() {
        this.icons = false;
        this.iconSize = false;
        return this;
    }

    /**
     * Leaves out only the Sizes group's Icon size row; Move Icons and Hide Icons are unaffected. For a
     * module whose icons still exist and can be moved/hidden but whose size already follows some other
     * slider here (e.g. Text size, or a content-size slider of the module's own), so a separate Icon
     * size row would have nothing distinct left to control.
     */
    @ApiStatus.Experimental
    public StandardPanelBuilder withoutIconSize() {
        this.iconSize = false;
        return this;
    }

    /**
     * Leaves out only the Sizes group's Text size row; Icon size is unaffected. For a module whose
     * persisted text scale no longer drives anything of its own (e.g. its only text moved onto a
     * separate {@link #withHeaderSize} slider), while it still has an independent icon size to keep.
     */
    @ApiStatus.Experimental
    public StandardPanelBuilder withoutTextSize() {
        this.textSize = false;
        return this;
    }

    /**
     * Makes icon size fully independent of text size from the start, instead of the default "follows
     * text size until icon size has been explicitly set" behavior ({@link #withHeaderSize}'s doc and
     * {@link dev.marie.framework.ui.modulesettings.ModuleScales#iconScale(PersistenceProvider, String, boolean)}
     * explain why that default exists). Reach for this when a module's persisted text scale can carry a
     * stale/leftover value the player never meant as an icon size — e.g. after {@link #withoutTextSize}
     * removes the Text size row entirely (its old value, no longer visible or settable, would otherwise
     * silently keep sizing the icon) — or any module that would rather icon size always start at a
     * plain 100% than visually track whatever Text size happens to be set to. The module's own render
     * code must read icon scale the same way, via {@link MarieModuleSettings#iconScale(PersistenceProvider, String, boolean)}
     * with {@code followText = false}, or this has no visible effect.
     */
    public StandardPanelBuilder independentIconSize() {
        this.iconFollowsText = false;
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

    /**
     * Overrides the Sizes group's "Text size" row label — for a module whose persisted text scale
     * only ever drives one specific part (e.g. just its header, with everything else it draws
     * following Bar size instead), where the generic "Text size" label would misleadingly suggest it
     * resizes the module's body too.
     */
    public StandardPanelBuilder textSizeLabel(String translationKey) {
        this.textSizeLabelKey = translationKey;
        return this;
    }

    /** Adds a Move Header toggle, for a module with a title separate from its body text (Move Text then moves the body only). */
    public StandardPanelBuilder withHeader() {
        this.header = true;
        return this;
    }

    /**
     * Adds a "Header size" row to the Sizes section, independent of Text size/Icon size/Bar size — for a
     * module with a title/header text sized separately from its body. Off unless requested, like {@link
     * #withoutIcons()}'s Icon size row is on unless withdrawn; unlike {@link #withHeader()}, which only
     * controls whether the header can be dragged, this and {@link #withHideHeader()} are independent
     * opt-ins a module reaches for regardless of whether its header moves. Read the value back with
     * {@link MarieModuleSettings#headerScale}; override the row's label with {@link #headerSizeLabel}.
     */
    public StandardPanelBuilder withHeaderSize() {
        this.headerSize = true;
        return this;
    }

    /**
     * Overrides the Header size row's label — for a module whose header serves a specific role the generic
     * "Header size" label wouldn't convey. Has no effect on the Text size row (see {@link #textSizeLabel}).
     */
    public StandardPanelBuilder headerSizeLabel(String translationKey) {
        this.headerSizeLabelKey = translationKey;
        return this;
    }

    /**
     * Adds a "Hide Header" toggle to the Hide group, independent of {@link #withoutHideText()}'s "Hide
     * Text" — for a module with a title/header separate from its body text that should be hideable on its
     * own. Off unless requested; read it back with {@link MarieModuleSettings#isHeaderHidden}.
     */
    public StandardPanelBuilder withHideHeader() {
        this.hideHeader = true;
        return this;
    }

    /**
     * Adds a "Move Icon" toggle, independent of "Move Icons" — for a module whose icon sits inside its
     * own small box (e.g. {@code BarRowComponent}'s icon box) and wants the icon draggable within that
     * box without moving the box itself. Both toggles keep working together: "Move Icons" still moves
     * the box (and the icon with it, since the icon offset is added on top), while this one moves only
     * the icon relative to wherever the box currently is. Read the value back with {@link
     * MarieModuleSettings#iconInnerOffsetX}; the module's render code must add it to its icon draw
     * coordinates (not its box draw) for this to have any visible effect.
     */
    public StandardPanelBuilder withIconInnerMove() {
        this.iconInnerMove = true;
        return this;
    }

    /**
     * Adds an "Icon size (in box)" slider to the Sizes group, independent of the ordinary Icon size
     * row — for a module whose icon draws inside its own small box (e.g. {@code BarRowComponent})
     * and wants the icon graphic resizable within that box without resizing the box itself. Pairs
     * with {@link #withIconInnerMove} the same way Icon size pairs with "Move Icons". Read the value
     * back with {@link MarieModuleSettings#iconInnerScale}; the module's render code must apply it to
     * its icon draw scale (not its box) for this to have any visible effect.
     */
    public StandardPanelBuilder withIconInnerSize() {
        this.iconInnerSize = true;
        return this;
    }

    /**
     * Adds a "Shadow" group to the Style tab: Text shadow and Border shadow strength sliders (0-100%,
     * self-contained — no config field needed). Border shadow needs the module's own box-drawing code
     * to call {@link MarieModuleSettings#drawBoxGlow} before it draws its box; Text shadow applies
     * automatically to any text the module draws through {@link MarieModuleSettings#withDisplaySettings}.
     */
    public StandardPanelBuilder withShadow() {
        this.shadow = true;
        return this;
    }

    /**
     * Adds a "Glow" tab: Text glow and Border glow (color + strength), plus Bar glow when the module
     * has bars ({@link #withoutBars} not called) — all self-contained, no config field needed. Text
     * and Bar glow apply automatically to anything the module draws through {@link
     * MarieModuleSettings#withDisplaySettings}; Border glow needs the module's own box-drawing code
     * to call {@link MarieModuleSettings#drawBoxGlow} before it draws its box.
     */
    public StandardPanelBuilder withGlow() {
        this.glow = true;
        return this;
    }

    /**
     * Adds self-contained Background opacity/shade and Border opacity/shade sliders — no config field
     * needed — for a module with no background/border color of its own to bind {@link #opacity}/
     * {@link #backgroundShade}/{@link #borderOpacity}/{@link #borderShade} to. Read the values back
     * with {@link MarieModuleSettings#styledBackground}/{@link MarieModuleSettings#styledBorder},
     * which the module's own box-drawing code applies to its base fill/border color. Has no effect on
     * a section whose caller-bound variant ({@link #opacity}, {@link #backgroundShade}, {@link
     * #borderOpacity} or {@link #borderShade}) was already called — a module never gets two competing
     * sliders for the same concept.
     */
    public StandardPanelBuilder withOwnStyle() {
        this.ownStyle = true;
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
        // One consolidated "Reset This Module" button, on the Layout tab (the first/default tab a
        // module's panel opens to) in place of the three narrower, scattered buttons this used to
        // build (Layout's and Style's own "Reset This Tab", Behavior's "Reset Positions") — it covers
        // everything all three did, plus the module's own drag/resize position/size, in one action.
        MarieToolbox.PanelBuilder panel = MarieToolbox.panel(title).tab(label("layout"))
                .resetEverything(store, panelId, onReset);
        if (padding) {
            panel.padding(store, panelId);
        }
        if (layoutRows != null) {
            layoutRows.accept(panel);
            panel.endSection();
        }
        panel.tab(label("behavior"));
        if (behaviorRows != null) {
            behaviorRows.accept(panel);
            panel.endSection();
        }
        if (moveAndHide) {
            panel.moveToggles(store, panelId, bars, icons, header, moveText, hideText, hideHeader, iconInnerMove);
        }
        panel.tab(label("appearance"));
        if (sizes) {
            panel.section(text("config.marieslib.moduleoptions.section.sizes")).textAndIconSizes(store, panelId, textSizeLabelKey, textSize, iconSize, iconFollowsText);
            if (iconInnerSize) {
                panel.slider(text("config.marieslib.moduleoptions.iconInnerSize"),
                        () -> ModuleScales.iconInnerScale(store, panelId),
                        v -> ModuleScales.setIconInnerScale(store, panelId, v),
                        ContentScaleController.SCALE_STORAGE_MIN, ContentScaleController.SCALE_STORAGE_MAX, 0.05d, onCommit).defaultValue(1.0d);
            }
            if (bars) {
                panel.barSize(store, panelId);
            }
            if (headerSize) {
                panel.headerSize(store, panelId, headerSizeLabelKey);
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
        // Self-contained fallback only when the caller never bound its own background/border values —
        // a module never gets two competing sliders for the same concept.
        boolean selfBackground = ownStyle && opacity == null && backgroundShade == null;
        boolean selfBorder = ownStyle && borderOpacity == null && borderShade == null;
        if (opacity != null || backgroundShade != null || selfBackground) {
            panel.section(text("config.marieslib.moduleoptions.section.background"));
        }
        if (opacity != null) {
            panel.slider(text("config.marieslib.moduleoptions.backgroundOpacity"), opacity, setOpacity, 0.0d, 1.0d, 0.01d, onCommit);
            if (hasOpacityDefault) {
                panel.defaultValue(opacityDefault);
            }
        } else if (selfBackground) {
            panel.slider(text("config.marieslib.moduleoptions.backgroundOpacity"),
                    () -> ModuleStyle.backgroundOpacity(store, panelId), v -> ModuleStyle.setBackgroundOpacity(store, panelId, v),
                    0.0d, 1.0d, 0.01d, onCommit).defaultValue(1.0d);
        }
        if (backgroundShade != null) {
            panel.slider(text("config.marieslib.moduleoptions.backgroundShade"), backgroundShade, setBackgroundShade,
                    -1.0d, 1.0d, 0.01d, onCommit).defaultValue(0.0d);
        } else if (selfBackground) {
            panel.slider(text("config.marieslib.moduleoptions.backgroundShade"),
                    () -> ModuleStyle.backgroundShade(store, panelId), v -> ModuleStyle.setBackgroundShade(store, panelId, v),
                    -1.0d, 1.0d, 0.01d, onCommit).defaultValue(0.0d);
        }
        if (borderOpacity != null || borderShade != null || selfBorder) {
            panel.section(text("config.marieslib.moduleoptions.section.border"));
        }
        if (borderOpacity != null) {
            panel.slider(text("config.marieslib.moduleoptions.borderOpacity"), borderOpacity, setBorderOpacity,
                    0.0d, 1.0d, 0.01d, onCommit).defaultValue(1.0d);
        } else if (selfBorder) {
            panel.slider(text("config.marieslib.moduleoptions.borderOpacity"),
                    () -> ModuleStyle.borderOpacity(store, panelId), v -> ModuleStyle.setBorderOpacity(store, panelId, v),
                    0.0d, 1.0d, 0.01d, onCommit).defaultValue(1.0d);
        }
        if (borderShade != null) {
            panel.slider(text("config.marieslib.moduleoptions.borderShade"), borderShade, setBorderShade,
                    -1.0d, 1.0d, 0.01d, onCommit).defaultValue(0.0d);
        } else if (selfBorder) {
            panel.slider(text("config.marieslib.moduleoptions.borderShade"),
                    () -> ModuleStyle.borderShade(store, panelId), v -> ModuleStyle.setBorderShade(store, panelId, v),
                    -1.0d, 1.0d, 0.01d, onCommit).defaultValue(0.0d);
        }
        if (shadow) {
            panel.section(text("config.marieslib.moduleoptions.section.shadow"));
            panel.slider(text("config.marieslib.moduleoptions.textShadow"),
                    () -> ModuleGlow.textShadowStrength(store, panelId), v -> ModuleGlow.setTextShadowStrength(store, panelId, v),
                    0.0d, 1.0d, 0.01d, onCommit).defaultValue(0.0d);
            panel.slider(text("config.marieslib.moduleoptions.borderShadow"),
                    () -> ModuleGlow.borderShadowStrength(store, panelId), v -> ModuleGlow.setBorderShadowStrength(store, panelId, v),
                    0.0d, 1.0d, 0.01d, onCommit).defaultValue(0.0d);
        }
        panel.endSection();
        if (styleRows != null) {
            styleRows.accept(panel);
        }
        if (glow) {
            panel.tab(text("config.marieslib.moduleoptions.tab.glow"));
            panel.color(text("config.marieslib.moduleoptions.textGlow"),
                    () -> ModuleGlow.textGlowColor(store, panelId), rgb -> ModuleGlow.setTextGlowColor(store, panelId, rgb),
                    0xFFFFFF, onCommit);
            panel.slider(text("config.marieslib.moduleoptions.textGlowStrength"),
                    () -> ModuleGlow.textGlowStrength(store, panelId), v -> ModuleGlow.setTextGlowStrength(store, panelId, v),
                    0.0d, 1.0d, 0.01d, onCommit).defaultValue(0.0d);
            panel.color(text("config.marieslib.moduleoptions.borderGlow"),
                    () -> ModuleGlow.borderGlowColor(store, panelId), rgb -> ModuleGlow.setBorderGlowColor(store, panelId, rgb),
                    0xFFFFFF, onCommit);
            panel.slider(text("config.marieslib.moduleoptions.borderGlowStrength"),
                    () -> ModuleGlow.borderGlowStrength(store, panelId), v -> ModuleGlow.setBorderGlowStrength(store, panelId, v),
                    0.0d, 1.0d, 0.01d, onCommit).defaultValue(0.0d);
            if (bars) {
                panel.color(text("config.marieslib.moduleoptions.barGlow"),
                        () -> ModuleGlow.barGlowColor(store, panelId), rgb -> ModuleGlow.setBarGlowColor(store, panelId, rgb),
                        0xFFFFFF, onCommit);
                panel.slider(text("config.marieslib.moduleoptions.barGlowStrength"),
                        () -> ModuleGlow.barGlowStrength(store, panelId), v -> ModuleGlow.setBarGlowStrength(store, panelId, v),
                        0.0d, 1.0d, 0.01d, onCommit).defaultValue(0.0d);
            }
        }
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
