package dev.marie.framework.network;

import dev.marie.framework.api.ApiStatus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A ready-made client-to-server request / server-to-client text response channel, so a consuming
 * mod does not have to define payloads, codecs and a registrar for the common "ask the server to
 * run an action, show me the text it returns" pattern (diagnostics, admin tools, reports).
 *
 * <p>Each consumer owns one channel instance (its own payload ids, in its own namespace):
 * <pre>{@code
 * CHANNEL = new MarieRequestChannel("mymod", "leakwatch", "1",
 *         player -> player.hasPermissions(2),
 *         (player, action, arg) -> new MarieRequestChannel.Response("dedicated server", runAction(action, arg)));
 * CHANNEL.register(modEventBus);            // in the mod constructor, common code
 * CHANNEL.onResponse(r -> screen.show(r));  // client code only
 * CHANNEL.sendRequest("status", "");        // client code only
 * }</pre>
 *
 * <p>Request text is untrusted client input: it is length-capped on decode, the {@code allowed}
 * predicate runs before the handler, and handlers must validate {@code action} and {@code argument}
 * themselves. Responses are clamped to {@link #MAX_RESPONSE_BYTES} of UTF-8 so they stay well
 * under vanilla's server-to-client payload limit; oversized output is truncated with a marker
 * line. Handlers run on the main thread.
 *
 * <p><b>Required or optional.</b> By default the payloads are <i>required</i>: NeoForge refuses the connection when the
 * other side does not have the channel (a vanilla client, a client without the mod, or a server without it). Call
 * {@link #optional()} before {@link #register} to register them through the registrar's optional path
 * ({@code PayloadRegistrar#optional()}): a side without the channel is then simply skipped during negotiation and the
 * connection goes ahead. Both sides must register it as optional for that to work. Sending on an optional channel the
 * other side does not have throws {@link UnsupportedOperationException} (NeoForge's {@code checkPacket}), so a client
 * must ask {@link #isAvailable} first. The protocol {@code version} must still match when both sides have the channel.
 */
@ApiStatus.Experimental
public final class MarieRequestChannel {

    public static final int MAX_ACTION_CHARS = 64;
    public static final int MAX_ARGUMENT_CHARS = 1024;
    public static final int MAX_LABEL_CHARS = 128;
    public static final int MAX_LINE_CHARS = 1024;
    public static final int MAX_RESPONSE_BYTES = 512 * 1024;
    private static final int MAX_LINES_ON_WIRE = 100_000;

    /** What the server computes for one request. {@code label} says which side/JVM produced it. */
    public record Response(String label, List<String> lines) {}

    @FunctionalInterface
    public interface ServerHandler {
        Response handle(ServerPlayer player, String action, String argument);
    }

    private record RequestPayload(Type<RequestPayload> type, String action, String argument) implements CustomPacketPayload {}

    private record ResponsePayload(Type<ResponsePayload> type, String label, List<String> lines) implements CustomPacketPayload {}

    private final String version;
    private final Predicate<ServerPlayer> allowed;
    private final ServerHandler handler;
    private final CustomPacketPayload.Type<RequestPayload> requestType;
    private final CustomPacketPayload.Type<ResponsePayload> responseType;
    private final StreamCodec<RegistryFriendlyByteBuf, RequestPayload> requestCodec;
    private final StreamCodec<RegistryFriendlyByteBuf, ResponsePayload> responseCodec;
    private volatile Consumer<Response> responseListener = r -> { };
    private volatile boolean optional;

    /**
     * @param namespace the consuming mod's id (payload ids are {@code namespace:name_request/_response})
     * @param version   protocol version; client and server must match
     * @param allowed   checked on the server before the handler; a denied request gets a one-line denial
     */
    public MarieRequestChannel(String namespace, String name, String version,
                               Predicate<ServerPlayer> allowed, ServerHandler handler) {
        this.version = version;
        this.allowed = allowed;
        this.handler = handler;
        this.requestType = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(namespace, name + "_request"));
        this.responseType = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(namespace, name + "_response"));
        this.requestCodec = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(MAX_ACTION_CHARS), RequestPayload::action,
                ByteBufCodecs.stringUtf8(MAX_ARGUMENT_CHARS), RequestPayload::argument,
                (a, arg) -> new RequestPayload(requestType, a, arg));
        this.responseCodec = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(MAX_LABEL_CHARS), ResponsePayload::label,
                ByteBufCodecs.stringUtf8(MAX_LINE_CHARS).apply(ByteBufCodecs.list(MAX_LINES_ON_WIRE)), ResponsePayload::lines,
                (label, lines) -> new ResponsePayload(responseType, label, lines));
    }

    /**
     * Registers the payloads as optional (see the class comment). Must be called before the mod event bus fires the
     * registration event, i.e. right after construction, in common code so both sides do it. Returns this for chaining.
     */
    public MarieRequestChannel optional() {
        this.optional = true;
        return this;
    }

    public boolean isOptional() {
        return optional;
    }

    /**
     * Client-side: whether the given connection negotiated this channel, i.e. the server has it. For an optional
     * channel this is how to tell "server without the mod" apart from a working connection. False for a null listener
     * (not connected).
     *
     * @param listener normally {@code Minecraft.getInstance().getConnection()}
     */
    public boolean isAvailable(ICommonPacketListener listener) {
        return listener != null && listener.hasChannel(requestType);
    }

    /** Hooks payload registration; call once from common mod-constructor code. */
    public void register(IEventBus modEventBus) {
        modEventBus.addListener(RegisterPayloadHandlersEvent.class, event -> {
            var registrar = event.registrar(version);
            if (optional) {
                registrar = registrar.optional();
            }
            registrar.playToServer(requestType, requestCodec, this::handleRequest);
            registrar.playToClient(responseType, responseCodec, this::handleResponse);
        });
    }

    /** Client-side: where responses land. Replaces any previous listener; runs on the main thread. */
    public void onResponse(Consumer<Response> listener) {
        this.responseListener = listener;
    }

    /**
     * Client-side: sends a request to the server (or the integrated server). Throws
     * {@link UnsupportedOperationException} if the connection did not negotiate this channel; check
     * {@link #isAvailable} first when the channel is optional.
     */
    public void sendRequest(String action, String argument) {
        PacketDistributor.sendToServer(new RequestPayload(requestType, action, argument));
    }

    private void handleRequest(RequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        Response response = allowed.test(player)
                ? handler.handle(player, payload.action(), payload.argument())
                : new Response("denied", List.of("You are not permitted to do that."));
        List<String> clamped = clampLines(response.lines(), MAX_RESPONSE_BYTES);
        PacketDistributor.sendToPlayer(player, new ResponsePayload(responseType,
                truncate(response.label(), MAX_LABEL_CHARS), clamped));
    }

    private void handleResponse(ResponsePayload payload, IPayloadContext context) {
        responseListener.accept(new Response(payload.label(), payload.lines()));
    }

    /**
     * Cuts each line to {@link #MAX_LINE_CHARS} and stops once the total UTF-8 size would exceed
     * {@code byteBudget}, ending with a marker line saying how many lines were dropped.
     */
    static List<String> clampLines(List<String> lines, int byteBudget) {
        List<String> out = new ArrayList<>();
        int used = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = truncate(lines.get(i), MAX_LINE_CHARS);
            int size = line.getBytes(StandardCharsets.UTF_8).length + 4;
            if (used + size > byteBudget || out.size() >= MAX_LINES_ON_WIRE - 1) {
                out.add("... truncated, " + (lines.size() - i) + " more line(s)");
                return out;
            }
            out.add(line);
            used += size;
        }
        return out;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
