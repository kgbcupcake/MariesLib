package dev.marie.framework.ui.commandcenter;

import dev.marie.framework.api.ApiStatus;
import net.minecraft.network.chat.Component;
import java.util.function.IntSupplier;

import javax.annotation.Nullable;

/**
 * Standard templated card: title/subtitle/accent, optionally clickable. {@code onClick} null means the card is inert/display-only.
 *
 * <p>{@code accent} is read every time the card is drawn, so a caller can pass a supplier that resolves a
 * user-editable color instead of baking one in at registration. The {@code int} constructor keeps a fixed accent.
 */
@ApiStatus.Experimental
public record CommandCenterCard(String id, String categoryId, Component title, @Nullable Component subtitle,
                                 IntSupplier accent, @Nullable Runnable onClick) implements CommandCenterCardEntry {

    /** A card with a fixed accent color. */
    public CommandCenterCard(String id, String categoryId, Component title, @Nullable Component subtitle,
                             int accentColor, @Nullable Runnable onClick) {
        this(id, categoryId, title, subtitle, () -> accentColor, onClick);
    }

    /** The accent as {@code 0xAARRGGBB}, resolved now. */
    public int accentColor() {
        return accent.getAsInt();
    }
}
