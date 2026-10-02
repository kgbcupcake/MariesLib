package dev.marie.framework.tracking;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.core.IMarieConfig;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * NeoForge data attachment for {@link TrackerMilestoneProgressData}. Its own attachment
 * namespace, entirely separate from {@link MilestoneProgressAttachment} — no shared storage
 * with the value-based milestone system.
 */
@ApiStatus.Internal
public final class TrackerMilestoneProgressAttachment {

    public static final String ATTACHMENT_ID = "tracker_milestone_progress";

    public static Supplier<AttachmentType<TrackerMilestoneProgressData>> TRACKER_MILESTONE_PROGRESS;

    private static boolean registered;

    private TrackerMilestoneProgressAttachment() {}

    public static void register(IEventBus modEventBus) {
        if (registered) {
            return;
        }
        registered = true;
        String modId = IMarieConfig.get().modId();
        DeferredRegister<AttachmentType<?>> attachmentTypes =
                DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, modId);
        TRACKER_MILESTONE_PROGRESS = attachmentTypes.register(ATTACHMENT_ID, () ->
                AttachmentType.builder(TrackerMilestoneProgressData::createNew)
                        .serialize(TrackerMilestoneProgressData.CODEC)
                        .copyOnDeath()
                        .build()
        );
        attachmentTypes.register(modEventBus);
    }

    public static boolean isRegistered() {
        return TRACKER_MILESTONE_PROGRESS != null;
    }

    @Nullable
    public static AttachmentType<TrackerMilestoneProgressData> attachmentType() {
        Supplier<AttachmentType<TrackerMilestoneProgressData>> supplier = TRACKER_MILESTONE_PROGRESS;
        return supplier != null ? supplier.get() : null;
    }

    /**
     * Returns tracker milestone progress for the player, lazily initializing on first access.
     * Returns {@code null} when the attachment is not registered.
     */
    @Nullable
    public static TrackerMilestoneProgressData getData(Player player) {
        AttachmentType<TrackerMilestoneProgressData> type = attachmentType();
        if (type == null) {
            return null;
        }
        return player.getData(type);
    }

    public static void setData(Player player, TrackerMilestoneProgressData data) {
        AttachmentType<TrackerMilestoneProgressData> type = attachmentType();
        if (type != null) {
            player.setData(type, data);
        }
    }
}
