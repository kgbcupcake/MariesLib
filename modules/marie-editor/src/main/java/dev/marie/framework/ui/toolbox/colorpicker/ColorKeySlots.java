package dev.marie.framework.ui.toolbox.colorpicker;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.color.ColorKey;
import dev.marie.framework.color.ColorPreviewOverrides;
import dev.marie.framework.color.ColorRegistry;
import dev.marie.framework.color.ColorScope;
import dev.marie.framework.color.MarieColors;

/**
 * Builds a {@link ColorSlot} bound to a registered {@link ColorKey} under a {@link ColorScope}: the
 * picker previews through {@link ColorPreviewOverrides} and commits to {@link ColorRegistry}, writing
 * the scope's own key so a second profile never touches the base color.
 */
@ApiStatus.Internal
public final class ColorKeySlots {

    private ColorKeySlots() {}

    public static ColorSlot slot(String label, ColorKey key, ColorScope scope) {
        ColorScope active = scope == null ? ColorScope.NONE : scope;
        ColorKey target = active.scoped(key);
        int defaultRgb = MarieColors.defaultArgb(key) & 0xFFFFFF;
        return new ColorSlot(label,
                () -> MarieColors.resolveColor(key, active),
                rgb -> ColorPreviewOverrides.setOverride(target, 0xFF000000 | rgb),
                defaultRgb,
                () -> commit(target, defaultRgb),
                () -> ColorPreviewOverrides.setOverride(target, null));
    }

    private static void commit(ColorKey target, int defaultRgb) {
        Integer preview = ColorPreviewOverrides.getOverride(target);
        if (preview != null) {
            String registryKey = target.id().toString();
            if ((preview & 0xFFFFFF) == defaultRgb) {
                ColorRegistry.remove(registryKey);
            } else {
                ColorRegistry.setArgb(registryKey, 0xFF000000 | (preview & 0xFFFFFF));
            }
            ColorRegistry.save();
        }
        ColorPreviewOverrides.setOverride(target, null);
    }
}
