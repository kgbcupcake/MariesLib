package dev.marie.framework.network;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.core.MarieCore;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Server-to-client snapshot of {@link dev.marie.framework.runtime.SourceRegistry}'s external
 * classifications. Datapack {@code source_classifications/*.json} files only load server-side, so a
 * client connected to a dedicated server needs this to show those values in tooltips.
 */
@ApiStatus.Internal
public record SourceClassificationSyncPayload(Map<ResourceLocation, Map<String, Float>> classifications)
        implements CustomPacketPayload {

    public static final Type<SourceClassificationSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MarieCore.MOD_ID, "source_classification_sync"));

    public static final StreamCodec<ByteBuf, SourceClassificationSyncPayload> STREAM_CODEC =
            ByteBufCodecs.<ByteBuf, ResourceLocation, Map<String, Float>, Map<ResourceLocation, Map<String, Float>>>map(
                            HashMap::new,
                            ResourceLocation.STREAM_CODEC,
                            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.FLOAT))
                    .map(SourceClassificationSyncPayload::new, SourceClassificationSyncPayload::classifications);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
