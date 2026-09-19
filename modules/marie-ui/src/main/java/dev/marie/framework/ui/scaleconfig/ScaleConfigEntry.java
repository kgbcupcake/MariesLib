package dev.marie.framework.ui.scaleconfig;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.component.MarieComponent;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * One card of {@link ScaleConfigPanel}: a component id, its display label, an optional accent color
 * — null falls back to the panel's cycling palette — and optional hosted content. With content
 * (see {@link #withContent}) the entry's open window shows that component instead of the built-in
 * Text Scale/Padding/Move Text and Icons rows, and sizes itself to its preferred height.
 */
@ApiStatus.Experimental
public record ScaleConfigEntry(String componentId, Component label, @Nullable Integer accentColor, @Nullable MarieComponent content) {

    public ScaleConfigEntry(String componentId, Component label, @Nullable Integer accentColor) {
        this(componentId, label, accentColor, null);
    }

    public ScaleConfigEntry(String componentId, Component label) {
        this(componentId, label, null, null);
    }

    /** A copy of this entry whose open window hosts {@code content} in place of the built-in rows. */
    public ScaleConfigEntry withContent(MarieComponent content) {
        return new ScaleConfigEntry(componentId, label, accentColor, content);
    }
}
