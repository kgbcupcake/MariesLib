package dev.marie.framework.compat.emi;

import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.Comparison;
import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.tooltips.MarieTooltipHelper;
import dev.marie.framework.core.MarieContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

@ApiStatus.Internal
public final class MarieEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        if (!ModList.get().isLoaded("emi")) {
            return;
        }
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (!MarieContext.isRegistered() || !MarieContext.isSourceItemAllowed(stack)) {
                continue;
            }
            int tooltipHash = MarieTooltipHelper.getTooltipLines(stack).hashCode();
            if (tooltipHash != 0) {
                registry.setDefaultComparison(item, previous -> Comparison.compareComponents());
            }
        }
    }
}
