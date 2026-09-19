package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.modulesettings.BrightnessRenderContext;
import dev.marie.framework.ui.modulesettings.ModuleOffsets;
import dev.marie.framework.ui.modulesettings.ModuleScales;
import dev.marie.framework.ui.modulesettings.MoveFlags;
import net.minecraft.network.chat.Component;

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

    /** Whether the "Move Text" toggle is on. */
    public static boolean isMoveTextEnabled(PersistenceProvider store, String panelId) {
        return MoveFlags.isOn(store, panelId);
    }

    /** The move mode currently switched on (at most one is), or {@code null} if none — for a host routing dragging to the matching offset. */
    public static MoveDrag.Mode activeMoveMode(PersistenceProvider store, String panelId) {
        if (isMoveTextEnabled(store, panelId)) {
            return MoveDrag.Mode.TEXT;
        }
        if (isMoveIconsEnabled(store, panelId)) {
            return MoveDrag.Mode.ICONS;
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
     * Starts the standard three-tab options panel: Layout (Padding), Behavior (Move Text, Move Icons,
     * Move Bars) and Appearance (Text size, Icon size, Bar size, plus whichever of text brightness, icon
     * brightness and background opacity you bind).
     */
    public static StandardPanelBuilder standardPanel(String title, PersistenceProvider store, String panelId) {
        return new StandardPanelBuilder(title, store, panelId);
    }

    /**
     * Drag state for a module's three move modes (text, icons, bars), so a host's mouse handlers don't each carry the same
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
        public enum Mode { TEXT, ICONS, BARS }

        private boolean active;
        private Mode mode = Mode.TEXT;
        private int grabX;
        private int grabY;

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

        /** The unclamped offset the pointer implies for the active drag. */
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
        private DoubleSupplier textBrightness;
        private DoubleConsumer setTextBrightness;
        private DoubleSupplier iconBrightness;
        private DoubleConsumer setIconBrightness;
        private Runnable onCommit = () -> {};

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

        /** Called after each finished edit of a value you bound (e.g. to save your config file). */
        public StandardPanelBuilder onCommit(Runnable onCommit) {
            this.onCommit = onCommit;
            return this;
        }

        public MarieComponent build() {
            MarieToolbox.PanelBuilder panel = MarieToolbox.panel(title)
                    .tab(label("layout")).padding(store, panelId)
                    .tab(label("behavior")).moveToggles(store, panelId)
                    .tab(label("appearance")).textAndIconSizes(store, panelId).barSize(store, panelId);
            if (textBrightness != null) {
                panel.slider(text("config.marieslib.moduleoptions.textBrightness"), textBrightness, setTextBrightness,
                        MIN_BRIGHTNESS, MAX_BRIGHTNESS, 0.01d, onCommit);
            }
            if (iconBrightness != null) {
                panel.slider(text("config.marieslib.moduleoptions.iconBrightness"), iconBrightness, setIconBrightness,
                        MIN_BRIGHTNESS, MAX_BRIGHTNESS, 0.01d, onCommit);
            }
            if (opacity != null) {
                panel.slider(text("config.marieslib.moduleoptions.backgroundOpacity"), opacity, setOpacity, 0.0d, 1.0d, 0.01d, onCommit);
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
