package dev.marie.framework.compat.rei;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.tooltips.MarieTooltipHelper;
import dev.marie.framework.core.MarieContext;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.entry.renderer.EntryRendererRegistry;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

@ApiStatus.Internal
public final class MarieReiPlugin implements REIClientPlugin {

    @Override
    public void registerEntryRenderers(EntryRendererRegistry registry) {
        if (!ModList.get().isLoaded("roughlyenoughitems")) {
            return;
        }
        registry.transformTooltip(VanillaEntryTypes.ITEM, (stack, point, tooltip) -> {
            ItemStack itemStack = stack.getValue();
            if (itemStack.isEmpty()) {
                return tooltip;
            }
            if (!MarieContext.isRegistered() || !MarieContext.isSourceItemAllowed(itemStack)) {
                return tooltip;
            }
            tooltip.addAllTexts(MarieTooltipHelper.getTooltipLines(itemStack));
            return tooltip;
        });
    }
}
