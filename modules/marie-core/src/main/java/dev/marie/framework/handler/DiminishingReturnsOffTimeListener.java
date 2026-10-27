package dev.marie.framework.handler;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.config.FeatureFlagCache;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Keeps {@link FeatureFlagCache#diminishingReturnsLastOffMillis()} with the world, so a player whose
 * source memory was saved before diminishing returns was last switched off starts fresh even after a
 * server restart. Tracking data compares it to its own {@code memory_saved_at} when it loads.
 */
@ApiStatus.Internal
public final class DiminishingReturnsOffTimeListener {

    private static final String DATA_NAME = "marieslib_diminishing_returns";

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        OffTime saved = event.getServer().overworld().getDataStorage().computeIfAbsent(OffTime.FACTORY, DATA_NAME);
        // Already off as the world starts: no switch will fire, but memory saved before now must not come back once it's turned on.
        if (!FeatureFlagCache.enableDiminishingReturns()) {
            saved.record(System.currentTimeMillis());
        }
        FeatureFlagCache.setDiminishingReturnsLastOffMillis(saved.lastOffMillis());
        FeatureFlagCache.setDiminishingReturnsOffListener(saved::record);
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        FeatureFlagCache.setDiminishingReturnsOffListener(null);
        FeatureFlagCache.setDiminishingReturnsLastOffMillis(0L);
    }

    /** The world's latest diminishing-returns switch-off time, in {@link System#currentTimeMillis()} terms. */
    static final class OffTime extends SavedData {

        static final SavedData.Factory<OffTime> FACTORY = new SavedData.Factory<>(OffTime::new, OffTime::load, null);

        private static final String KEY = "last_off_millis";

        private long lastOffMillis;

        long lastOffMillis() {
            return lastOffMillis;
        }

        void record(long millis) {
            if (millis > lastOffMillis) {
                lastOffMillis = millis;
                setDirty();
            }
        }

        static OffTime load(CompoundTag tag, HolderLookup.Provider registries) {
            OffTime offTime = new OffTime();
            offTime.lastOffMillis = tag.getLong(KEY);
            return offTime;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putLong(KEY, lastOffMillis);
            return tag;
        }
    }
}
