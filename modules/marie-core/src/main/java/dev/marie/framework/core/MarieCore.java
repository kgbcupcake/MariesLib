package dev.marie.framework.core;

import java.util.UUID;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.marie.framework.config.MariesLibConfigIO;
import dev.marie.framework.network.MarieNetworking;
import dev.marie.framework.tracking.tracker.network.TrackerNetworking;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(MarieCore.MOD_ID)
public final class MarieCore {

    public static final String MOD_ID = "marieslib";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Shared launch identifier stamped into WatchDb telemetry by LogWatch and LeakWatch. */
    public static final UUID SESSION_ID = UUID.randomUUID();

    public MarieCore(IEventBus modEventBus, ModContainer modContainer) {
        MariesLibConfigIO.load();
        MarieNetworking.register(modEventBus);
        TrackerNetworking.register(modEventBus);
        LOGGER.info("MarieCore initialized");
    }
}
