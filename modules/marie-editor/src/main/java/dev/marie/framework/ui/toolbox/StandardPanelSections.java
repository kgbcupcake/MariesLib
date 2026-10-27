package dev.marie.framework.ui.toolbox;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.api.MariePulse;
import dev.marie.framework.ui.api.MarieToolbox;
import dev.marie.framework.ui.modulesettings.ModuleGlow;
import dev.marie.framework.ui.modulesettings.ModuleStyle;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/** The Background/Border groups and the Glow/Pulse tabs of {@code StandardPanelBuilder}'s standard panel. */
@ApiStatus.Internal
public final class StandardPanelSections {

    private StandardPanelSections() {}

    /** A caller-bound slider value; {@code null} getter means "not bound". */
    public record Binding(DoubleSupplier getter, DoubleConsumer setter) {}

    /**
     * Background then Border groups. Each slider uses the caller's binding when there is one, else the
     * module's own stored value when {@code ownStyle} is on, so a module never gets two sliders for one concept.
     */
    public static void backgroundAndBorder(MarieToolbox.PanelBuilder panel, PersistenceProvider store, String panelId,
                                           boolean ownStyle, Binding opacity, Double opacityDefault, Binding backgroundShade,
                                           Binding borderOpacity, Binding borderShade, Runnable onCommit) {
        boolean selfOpacity = ownStyle && opacity == null;
        boolean selfBackgroundShade = ownStyle && backgroundShade == null;
        boolean selfBorderOpacity = ownStyle && borderOpacity == null;
        boolean selfBorderShade = ownStyle && borderShade == null;
        if (opacity != null || backgroundShade != null || selfOpacity || selfBackgroundShade) {
            panel.section(text("config.marieslib.moduleoptions.section.background"));
        }
        if (opacity != null) {
            panel.slider(text("config.marieslib.moduleoptions.backgroundOpacity"), opacity.getter(), opacity.setter(), 0.0d, 1.0d, 0.01d, onCommit);
            if (opacityDefault != null) {
                panel.defaultValue(opacityDefault);
            }
        } else if (selfOpacity) {
            panel.slider(text("config.marieslib.moduleoptions.backgroundOpacity"),
                    () -> ModuleStyle.backgroundOpacity(store, panelId), v -> ModuleStyle.setBackgroundOpacity(store, panelId, v),
                    0.0d, 1.0d, 0.01d, onCommit).defaultValue(1.0d);
        }
        shadeSlider(panel, "backgroundShade", backgroundShade, selfBackgroundShade,
                () -> ModuleStyle.backgroundShade(store, panelId), v -> ModuleStyle.setBackgroundShade(store, panelId, v), onCommit);
        if (borderOpacity != null || borderShade != null || selfBorderOpacity || selfBorderShade) {
            panel.section(text("config.marieslib.moduleoptions.section.border"));
        }
        if (borderOpacity != null) {
            panel.slider(text("config.marieslib.moduleoptions.borderOpacity"), borderOpacity.getter(), borderOpacity.setter(),
                    0.0d, 1.0d, 0.01d, onCommit).defaultValue(1.0d);
        } else if (selfBorderOpacity) {
            panel.slider(text("config.marieslib.moduleoptions.borderOpacity"),
                    () -> ModuleStyle.borderOpacity(store, panelId), v -> ModuleStyle.setBorderOpacity(store, panelId, v),
                    0.0d, 1.0d, 0.01d, onCommit).defaultValue(1.0d);
        }
        shadeSlider(panel, "borderShade", borderShade, selfBorderShade,
                () -> ModuleStyle.borderShade(store, panelId), v -> ModuleStyle.setBorderShade(store, panelId, v), onCommit);
    }

    private static void shadeSlider(MarieToolbox.PanelBuilder panel, String key, Binding bound, boolean self,
                                    DoubleSupplier selfGetter, DoubleConsumer selfSetter, Runnable onCommit) {
        if (bound != null) {
            panel.slider(text("config.marieslib.moduleoptions." + key), bound.getter(), bound.setter(),
                    -1.0d, 1.0d, 0.01d, onCommit).defaultValue(0.0d);
        } else if (self) {
            panel.slider(text("config.marieslib.moduleoptions." + key), selfGetter, selfSetter,
                    -1.0d, 1.0d, 0.01d, onCommit).defaultValue(0.0d);
        }
    }

    /** The Glow tab (Border glow, Bar glow, then {@code glowRows}) and the Pulse tab. */
    public static void glowAndPulse(MarieToolbox.PanelBuilder panel, PersistenceProvider store, String panelId, boolean bars,
                                    boolean barGlow, Consumer<MarieToolbox.PanelBuilder> glowRows, Runnable onCommit) {
        panel.tab(text("config.marieslib.moduleoptions.tab.glow"));
        // Text glow and Header glow (glyph-shaped, drawn as offset copies of the letters) are
        // deliberately not offered: at Minecraft's small UI font size the offsets cover the whole
        // glyph, so it reads as the text's color being replaced by the glow color rather than an
        // outline framing it — not fixable by tuning, a real soft blur would need an offscreen
        // render target, and a first attempt at that corrupted the game's rendering outright.
        // Border glow and Bar glow (plain rectangle outlines, not glyph-shaped) don't have this
        // problem and stay.
        panel.color(text("config.marieslib.moduleoptions.borderGlow"),
                () -> ModuleGlow.borderGlowColor(store, panelId), rgb -> ModuleGlow.setBorderGlowColor(store, panelId, rgb),
                0xFFFFFF, onCommit);
        panel.slider(text("config.marieslib.moduleoptions.borderGlowStrength"),
                () -> ModuleGlow.borderGlowStrength(store, panelId), v -> ModuleGlow.setBorderGlowStrength(store, panelId, v),
                0.0d, 1.0d, 0.01d, onCommit).defaultValue(0.0d);
        if (bars && barGlow) {
            panel.color(text("config.marieslib.moduleoptions.barGlow"),
                    () -> ModuleGlow.barGlowColor(store, panelId), rgb -> ModuleGlow.setBarGlowColor(store, panelId, rgb),
                    0xFFFFFF, onCommit);
            panel.slider(text("config.marieslib.moduleoptions.barGlowStrength"),
                    () -> ModuleGlow.barGlowStrength(store, panelId), v -> ModuleGlow.setBarGlowStrength(store, panelId, v),
                    0.0d, 1.0d, 0.01d, onCommit).defaultValue(0.0d);
        }
        if (glowRows != null) {
            glowRows.accept(panel);
        }
        // Pulse tab: makes the module's own border line, Border glow and Bar glow breathe in their own
        // colors (see MariePulse) — no color of its own, nothing extra drawn — on one shared clock so
        // every module stays in sync.
        // Strength 0 (the default) is off.
        panel.tab(text("config.marieslib.moduleoptions.tab.pulse"));
        panel.slider(text("config.marieslib.moduleoptions.pulseStrength"),
                () -> ModuleGlow.pulseStrength(store, panelId), v -> ModuleGlow.setPulseStrength(store, panelId, v),
                0.0d, 1.0d, 0.01d, onCommit).defaultValue(0.0d);
        panel.cycle(text("config.marieslib.moduleoptions.pulseSpeed"),
                new String[]{text("config.marieslib.moduleoptions.pulseSpeed.slow"), text("config.marieslib.moduleoptions.pulseSpeed.normal"),
                        text("config.marieslib.moduleoptions.pulseSpeed.fast")},
                () -> ModuleGlow.pulseSpeed(store, panelId), i -> ModuleGlow.setPulseSpeed(store, panelId, i), onCommit)
                .defaultValue(MariePulse.Speed.NORMAL.ordinal());
        panel.cycle(text("config.marieslib.moduleoptions.pulseStyle"),
                new String[]{text("config.marieslib.moduleoptions.pulseStyle.smooth"), text("config.marieslib.moduleoptions.pulseStyle.heartbeat"),
                        text("config.marieslib.moduleoptions.pulseStyle.flash")},
                () -> ModuleGlow.pulseStyle(store, panelId), i -> ModuleGlow.setPulseStyle(store, panelId, i), onCommit)
                .defaultValue(MariePulse.Style.SMOOTH.ordinal());
        panel.section(text("config.marieslib.moduleoptions.section.pulseTargets"));
        panel.toggle(text("config.marieslib.moduleoptions.pulseBorder"),
                () -> ModuleGlow.pulseBorder(store, panelId), v -> ModuleGlow.setPulseBorder(store, panelId, v), onCommit)
                .defaultValue(true);
        panel.toggle(text("config.marieslib.moduleoptions.pulseBorderGlow"),
                () -> ModuleGlow.pulseBorderGlow(store, panelId), v -> ModuleGlow.setPulseBorderGlow(store, panelId, v), onCommit)
                .defaultValue(true);
        if (bars) {
            panel.toggle(text("config.marieslib.moduleoptions.pulseBarGlow"),
                    () -> ModuleGlow.pulseBarGlow(store, panelId), v -> ModuleGlow.setPulseBarGlow(store, panelId, v), onCommit)
                    .defaultValue(true);
        }
        panel.endSection();
        panel.resetTab();
    }

    private static String text(String key) {
        return Component.translatable(key).getString();
    }
}
