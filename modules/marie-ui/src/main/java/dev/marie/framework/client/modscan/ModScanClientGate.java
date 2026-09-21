package dev.marie.framework.client.modscan;

import dev.marie.framework.core.MarieCore;
import dev.marie.framework.modscan.ModScan;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Tells the mod-file scan (marie-core, no client classes) that the client has finished loading: the
 * first tick with no loading overlay. Until then the scan stays armed but does not start.
 */
@EventBusSubscriber(modid = MarieCore.MOD_ID, value = Dist.CLIENT)
public final class ModScanClientGate {

    private static boolean signalled;

    private ModScanClientGate() {}

    @net.neoforged.bus.api.SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!signalled && Minecraft.getInstance().getOverlay() == null) {
            signalled = true;
            ModScan.onClientReady();
        }
    }
}
