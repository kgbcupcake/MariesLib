package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.modulesettings.BrightnessRenderContext;
import dev.marie.framework.ui.modulesettings.HideFlags;
import dev.marie.framework.ui.modulesettings.ModuleOffsets;
import dev.marie.framework.ui.modulesettings.ModuleRenderContext;
import dev.marie.framework.ui.modulesettings.ModuleScales;
import dev.marie.framework.ui.modulesettings.MoveFlags;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * Public facade for the display settings a HUD-style module box exposes — independent text and icon
 * size, padding, separately movable bars, and independent text/icon brightness — covering both halves:
 * the read side a module's render code calls each frame, and a ready-made options panel for the edit UI.
 *
 * <p>UI-state settings (sizes, padding, move modes, bar offset) live in the {@link PersistenceProvider}
 * the caller passes, under its own {@code panelId}; nothing here owns storage. Brightness and
 * background opacity are the caller's own values (typically config fields), bound through getters and
 * setters, so they stay in the caller's config file. Brightness is expected to accept
 * {@link #MIN_BRIGHTNESS}..{@link #MAX_BRIGHTNESS}, opacity 0..1.
 *
 * <pre>{@code
 * // render side
 * float textScale = (float) MarieModuleSettings.textScale(store, PANEL_ID);
 * float iconScale = (float) MarieModuleSettings.iconScale(store, PANEL_ID);
 * int barDx = MarieModuleSettings.barOffsetX(store, PANEL_ID);
 * drawPanel(MarieModuleSettings.withBrightness(context, cfg.textBrightness(), cfg.iconBrightness()));
 *
 * // edit UI
 * MarieComponent box = MarieModuleSettings.standardPanel("My HUD", store, PANEL_ID)
 *         .opacity(cfg::opacity, cfg::setOpacity)
 *         .textBrightness(cfg::textBrightness, cfg::setTextBrightness)
 *         .iconBrightness(cfg::iconBrightness, cfg::setIconBrightness)
 *         .onCommit(cfg::save)
 *         .build();
 * }</pre>
 */
@ApiStatus.Experimental
public final class MarieModuleSettings {

    /** Lowest and highest brightness the options panel offers (1.0 = unchanged). */
    public static final double MIN_BRIGHTNESS = 0.2d;
    public static final double MAX_BRIGHTNESS = 2.0d;

    private MarieModuleSettings() {}

    /** Text size multiplier for {@code panelId} (the module's persisted {@code contentScale}). */
    public static double textScale(PersistenceProvider store, String panelId) {
        return ModuleScales.textScale(store, panelId);
    }

    /** Icon size multiplier; equal to {@link #textScale} until an icon size has been set, so modules that predate the split look unchanged. */
    public static double iconScale(PersistenceProvider store, String panelId) {
        return ModuleScales.iconScale(store, panelId);
    }

    /** Bar size multiplier for {@code panelId}: bar length and thickness, and the value text at the bar's end. 1.0 until set. */
    public static double barScale(PersistenceProvider store, String panelId) {
        return ModuleScales.barScale(store, panelId);
    }

    /** Text brightness a module keeps in its own store (1.0 until set) — see {@link MarieToolbox.PanelBuilder#storedBrightness}. */
    public static double textBrightness(PersistenceProvider store, String panelId) {
        return ModuleScales.textBrightness(store, panelId);
    }

    public static double iconBrightness(PersistenceProvider store, String panelId) {
        return ModuleScales.iconBrightness(store, panelId);
    }

    /** Where the module's text sits relative to its default place, for a module that keeps that offset here instead of in its own storage. */
    public static int textOffsetX(PersistenceProvider store, String panelId) {
        return ModuleOffsets.textX(store, panelId);
    }

    public static int textOffsetY(PersistenceProvider store, String panelId) {
        return ModuleOffsets.textY(store, panelId);
    }

    /** Live drag preview of the text offset; call {@link #commitTextOffset} when the drag ends. */
    public static void setTextOffset(PersistenceProvider store, String panelId, int x, int y) {
        ModuleOffsets.setText(store, panelId, x, y);
    }

    public static void commitTextOffset(PersistenceProvider store, String panelId) {
        ModuleOffsets.commitText(store, panelId);
    }

    /**
     * {@code context} with everything {@code panelId} keeps in {@code store} applied to what a module draws
     * through it: its text and icon offsets, its icon size relative to its text size, and its stored text
     * and icon brightness. For a module whose renderer scales text and icons together by its text size and
     * draws them through a {@link RenderContext}; {@code context} itself when every setting is default.
     */
    public static RenderContext withDisplaySettings(RenderContext context, PersistenceProvider store, String panelId) {
        return ModuleRenderContext.wrap(context, store, panelId);
    }

    /** Where the module's icons sit relative to their default place. In memory; cheap to call every frame. */
    public static int iconOffsetX(PersistenceProvider store, String panelId) {
        return ModuleOffsets.iconX(store, panelId);
    }

    public static int iconOffsetY(PersistenceProvider store, String panelId) {
        return ModuleOffsets.iconY(store, panelId);
    }

    /** Live drag preview of the icon offset; call {@link #commitIconOffset} when the drag ends. */
    public static void setIconOffset(PersistenceProvider store, String panelId, int x, int y) {
        ModuleOffsets.setIcon(store, panelId, x, y);
    }

    public static void commitIconOffset(PersistenceProvider store, String panelId) {
        ModuleOffsets.commitIcon(store, panelId);
    }

    /** Whether the "Move All" toggle is on (one drag moves text, icons and bars together). */
    public static boolean isMoveAllEnabled(PersistenceProvider store, String panelId) {
        return MoveFlags.isOn(store, ModuleOffsets.moveAllFlagId(panelId));
    }

    /** Whether the module's "Hide Icons" toggle is on. {@link #withDisplaySettings} already skips icon draws; this is for a host that lays out or draws icons some other way. */
    public static boolean isIconsHidden(PersistenceProvider store, String panelId) {
        return HideFlags.iconsHidden(store, panelId);
    }

    /** Whether the "Move Icons" toggle is on. */
    public static boolean isMoveIconsEnabled(PersistenceProvider store, String panelId) {
        return MoveFlags.isOn(store, ModuleOffsets.moveIconsFlagId(panelId));
    }

    /** Where the module's bars (and their value text) sit relative to their default place. In memory; cheap to call every frame. */
    public static int barOffsetX(PersistenceProvider store, String panelId) {
        return ModuleOffsets.barX(store, panelId);
    }

    public static int barOffsetY(PersistenceProvider store, String panelId) {
        return ModuleOffsets.barY(store, panelId);
    }

    /** Live drag preview of the bar offset; call {@link #commitBarOffset} when the drag ends. */
    public static void setBarOffset(PersistenceProvider store, String panelId, int x, int y) {
        ModuleOffsets.setBar(store, panelId, x, y);
    }

    public static void commitBarOffset(PersistenceProvider store, String panelId) {
        ModuleOffsets.commitBar(store, panelId);
    }

    /** Whether the "Move Bars" toggle is on — a host polls this to route dragging to the bar offset. */
    public static boolean isMoveBarsEnabled(PersistenceProvider store, String panelId) {
        return MoveFlags.isOn(store, ModuleOffsets.moveBarsFlagId(panelId));
    }

    /** Where the module's header sits relative to its default place. In memory; cheap to call every frame. */
    public static int headerOffsetX(PersistenceProvider store, String panelId) {
        return ModuleOffsets.headerX(store, panelId);
    }

    public static int headerOffsetY(PersistenceProvider store, String panelId) {
        return ModuleOffsets.headerY(store, panelId);
    }

    /** Live drag preview of the header offset; call {@link #commitHeaderOffset} when the drag ends. */
    public static void setHeaderOffset(PersistenceProvider store, String panelId, int x, int y) {
        ModuleOffsets.setHeader(store, panelId, x, y);
    }

    public static void commitHeaderOffset(PersistenceProvider store, String panelId) {
        ModuleOffsets.commitHeader(store, panelId);
    }

    /** Whether the "Move Header" toggle is on. */
    public static boolean isMoveHeaderEnabled(PersistenceProvider store, String panelId) {
        return MoveFlags.isOn(store, ModuleOffsets.moveHeaderFlagId(panelId));
    }

    /** Whether the "Move Text" toggle is on. */
    public static boolean isMoveTextEnabled(PersistenceProvider store, String panelId) {
        return MoveFlags.isOn(store, panelId);
    }

    /** The move mode currently switched on (at most one is), or {@code null} if none — for a host routing dragging to the matching offset. */
    public static MoveDrag.Mode activeMoveMode(PersistenceProvider store, String panelId) {
        if (isMoveAllEnabled(store, panelId)) {
            return MoveDrag.Mode.ALL;
        }
        if (isMoveTextEnabled(store, panelId)) {
            return MoveDrag.Mode.TEXT;
        }
        if (isMoveIconsEnabled(store, panelId)) {
            return MoveDrag.Mode.ICONS;
        }
        if (isMoveHeaderEnabled(store, panelId)) {
            return MoveDrag.Mode.HEADER;
        }
        return isMoveBarsEnabled(store, panelId) ? MoveDrag.Mode.BARS : null;
    }

    /** {@code context} whose text and item icons are drawn at the given brightness multipliers; {@code context} itself when both are 1.0. */
    public static RenderContext withBrightness(RenderContext context, double textBrightness, double iconBrightness) {
        return BrightnessRenderContext.wrap(context, textBrightness, iconBrightness);
    }

    /** {@code argb} with RGB scaled by {@code brightness} (alpha untouched, capped at 255). */
    public static int scaleBrightness(int argb, double brightness) {
        return BrightnessRenderContext.scale(argb, brightness);
    }

    /**
     * Starts the standard options panel every module window shares: Layout (Padding), Behavior (collapsible Move group:
     * Move Text/Icons/Bars/All and Reset Positions; collapsible Hide group: Hide Icons) and Style, whose collapsible
     * groups are Sizes (Text/Icon/Bar size), Brightness, Background (opacity, shade) and Border (opacity, shade) — a
     * group appears only when you bind something for it. Consumer-specific rows go in through {@code layoutRows},
     * {@code behaviorRows}, {@code styleRows} and {@code extraTabs}, so no module hand-builds its own layout.
     */
    public static StandardPanelBuilder standardPanel(String title, PersistenceProvider store, String panelId) {
        return new StandardPanelBuilder(title, store, panelId);
    }

    /**
     * Drag state for a module's move modes (text, icons, bars, or all three at once), so a host's mouse handlers don't each carry the same
     * grab-offset bookkeeping. One instance per module; the host still owns the offsets themselves and
     * their clamping (the bar offset through {@link #setBarOffset}, its text-and-icons offset however
     * it likes):
     *
     * <pre>{@code
     * // press:   moveDrag.start(mode, mouseX, mouseY, currentOffsetX, currentOffsetY);
     * // drag:    if (moveDrag.isActive()) { apply(moveDrag.mode(), clamp(moveDrag.offsetX(mouseX)), clamp(moveDrag.offsetY(mouseY))); }
     * // release: if (moveDrag.isActive()) { Mode mode = moveDrag.mode(); moveDrag.stop(); commit(mode); }
     * }</pre>
     */
    public static final class MoveDrag {

        /** What a move drag is repositioning. */
        public enum Mode {
            TEXT, ICONS, BARS,
            /** A separate title's offset (see {@link MarieModuleSettings#headerOffsetX}); only for modules that ask for a header toggle. */
            HEADER,
            /** Text, icons and bars together: one drag shifts all three offsets by the same amount (see {@link #startAll}). */
            ALL
        }

        private boolean active;
        private Mode mode = Mode.TEXT;
        private int grabX;
        private int grabY;
        /** Each single mode's offset at the press, indexed TEXT, ICONS, BARS — what {@link Mode#ALL} adds its delta to. */
        private final int[] baseX = new int[4];
        private final int[] baseY = new int[4];

        /** Begins a {@link Mode#ALL} drag from the three offsets' current values; {@link #offsetX}/{@link #offsetY} then return the pointer's movement since the press, to add to {@link #baseX}/{@link #baseY} of each mode. */
        public void startAll(double mouseX, double mouseY, int textX, int textY, int iconX, int iconY, int barX, int barY) {
            this.active = true;
            this.mode = Mode.ALL;
            this.grabX = (int) mouseX;
            this.grabY = (int) mouseY;
            baseX[0] = textX;
            baseY[0] = textY;
            baseX[1] = iconX;
            baseY[1] = iconY;
            baseX[2] = barX;
            baseY[2] = barY;
        }

        /** As {@link #startAll(double, double, int, int, int, int, int, int)}, also carrying the header offset. */
        public void startAll(double mouseX, double mouseY, int textX, int textY, int iconX, int iconY, int barX, int barY, int headerX, int headerY) {
            startAll(mouseX, mouseY, textX, textY, iconX, iconY, barX, barY);
            baseX[3] = headerX;
            baseY[3] = headerY;
        }

        /** {@code single}'s offset when the {@link Mode#ALL} drag started ({@code single} must be TEXT, ICONS or BARS). */
        public int baseX(Mode single) {
            return baseX[single.ordinal()];
        }

        public int baseY(Mode single) {
            return baseY[single.ordinal()];
        }

        /** Begins dragging the offset of {@code mode} from its current value. */
        public void start(Mode mode, double mouseX, double mouseY, int currentOffsetX, int currentOffsetY) {
            this.active = true;
            this.mode = mode;
            this.grabX = (int) mouseX - currentOffsetX;
            this.grabY = (int) mouseY - currentOffsetY;
        }

        public boolean isActive() {
            return active;
        }

        /** What the active drag is repositioning. */
        public Mode mode() {
            return mode;
        }

        /** The unclamped offset the pointer implies for the active drag (for {@link Mode#ALL}, the movement since the press). */
        public int offsetX(double mouseX) {
            return (int) mouseX - grabX;
        }

        public int offsetY(double mouseY) {
            return (int) mouseY - grabY;
        }

        public void stop() {
            active = false;
        }
    }

    /** Builder returned by {@link #standardPanel}. */
    public static final class StandardPanelBuilder {

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
        private boolean storedBrightness;
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

        private StandardPanelBuilder(String title, PersistenceProvider store, String panelId) {
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
         * receives the builder once, when {@link #build} runs. A group the caller opens is closed for it afterwards.
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

        /** Adds a Move Header toggle, for a module with a title separate from its body text (Move Text then moves the body only). */
        public StandardPanelBuilder withHeader() {
            this.header = true;
            return this;
        }

        /** Adds text/icon brightness sliders over values kept in the panel's own store (see {@link #withDisplaySettings}) instead of caller-bound getters and setters. */
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
         * {@link #build} runs, positioned on the Style tab.
         */
        public StandardPanelBuilder styleRows(Consumer<MarieToolbox.PanelBuilder> rows) {
            this.styleRows = rows;
            return this;
        }

        /**
         * Lets the caller append tabs of its own after the standard ones (e.g. a {@code colorTab}); {@code more}
         * receives the builder once, when {@link #build} runs, positioned after the last standard tab.
         */
        public StandardPanelBuilder extraTabs(Consumer<MarieToolbox.PanelBuilder> more) {
            this.extraTabs = more;
            return this;
        }

        public MarieComponent build() {
            MarieToolbox.PanelBuilder panel = MarieToolbox.panel(title).tab(label("layout")).padding(store, panelId);
            if (layoutRows != null) {
                layoutRows.accept(panel);
                panel.endSection();
            }
            panel.resetTab().tab(label("behavior"));
            if (behaviorRows != null) {
                behaviorRows.accept(panel);
                panel.endSection();
            }
            panel.moveToggles(store, panelId, bars, icons, header).resetPositions(store, panelId, onReset)
                    .tab(label("appearance"))
                    .section(text("config.marieslib.moduleoptions.section.sizes")).textAndIconSizes(store, panelId);
            if (bars) {
                panel.barSize(store, panelId);
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
}
