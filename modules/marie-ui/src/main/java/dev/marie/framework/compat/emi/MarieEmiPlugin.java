package dev.marie.framework.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
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

/**
 * On NeoForge, EMI discovers a plugin only via {@link EmiEntrypoint}, scanned from mod file class
 * annotations ({@code EmiAgnosNeoForge#getPluginsAgnos}) - never via {@code ServiceLoader}/{@code
 * META-INF/services}, which this class (and the {@code META-INF/services/dev.emi.emi.api.EmiPlugin}
 * file alongside it) relied on instead until now, meaning {@link #register} was never actually
 * called and every registration below has been dead code.
 */
@ApiStatus.Internal
@EmiEntrypoint
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
