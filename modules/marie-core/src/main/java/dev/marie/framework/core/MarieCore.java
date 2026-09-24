package dev.marie.framework.core;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.marie.framework.config.MariesLibConfigIO;
import dev.marie.framework.network.MarieNetworking;
import dev.marie.framework.tracking.tracker.network.TrackerNetworking;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;

@Mod(MarieCore.MOD_ID)
public final class MarieCore {

    public static final String MOD_ID = "marieslib";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Shared launch identifier stamped into WatchDb telemetry by LogWatch and LeakWatch. */
    public static final UUID SESSION_ID = UUID.randomUUID();

    /** SHA-256 hex digest of the sorted "modid@version" list, computed lazily on first access. */
    public static String MODLIST_HASH;

    public static String getModlistHash() {
        if (MODLIST_HASH == null) {
            MODLIST_HASH = computeModlistHash();
        }
        return MODLIST_HASH;
    }

    private static String computeModlistHash() {
        String joined = ModList.get().getMods().stream()
                .map(mod -> mod.getModId() + "@" + mod.getVersion())
                .sorted()
                .collect(Collectors.joining("\n"));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest(joined.getBytes())) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public MarieCore(IEventBus modEventBus, ModContainer modContainer) {
        MariesLibConfigIO.load();
        MarieNetworking.register(modEventBus);
        TrackerNetworking.register(modEventBus);
        LOGGER.info("MarieCore initialized");
    }
}
