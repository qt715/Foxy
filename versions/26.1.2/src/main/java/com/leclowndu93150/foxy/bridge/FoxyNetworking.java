package com.leclowndu93150.foxy.bridge;

import com.leclowndu93150.foxy.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.leclowndu93150.foxy.fabricmc.fabric.api.networking.v1.PacketSender;
import com.leclowndu93150.foxy.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import com.leclowndu93150.foxy.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fabric play networking on top of NeoForge payload registration. Fabric registers payload types
 * and receivers from mod initializers; Foxy records them and replays the types into
 * {@link RegisterPayloadHandlersEvent}, dispatching received payloads to whichever Fabric receiver
 * is registered at that moment.
 */
public final class FoxyNetworking {
    private static final Logger LOGGER = LoggerFactory.getLogger("Foxy");

    public static final TypeRegistry SERVERBOUND_PLAY = new TypeRegistry("serverbound play");
    public static final TypeRegistry CLIENTBOUND_PLAY = new TypeRegistry("clientbound play");

    private static final Map<Identifier, ServerPlayNetworking.PlayPayloadHandler<?>> SERVER_RECEIVERS = new ConcurrentHashMap<>();
    private static final Map<Identifier, ClientPlayNetworking.PlayPayloadHandler<?>> CLIENT_RECEIVERS = new ConcurrentHashMap<>();

    private static volatile boolean registered;

    private FoxyNetworking() {}

    public static final class TypeRegistry implements PayloadTypeRegistry<RegistryFriendlyByteBuf> {
        private final String name;
        private final Map<Identifier, CustomPacketPayload.TypeAndCodec<? super RegistryFriendlyByteBuf, ?>> types = new LinkedHashMap<>();

        private TypeRegistry(String name) {
            this.name = name;
        }

        @Override
        public synchronized <T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super RegistryFriendlyByteBuf, T> register(
                CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
            if (registered) {
                throw new IllegalStateException("Foxy: " + name + " payload " + type.id() + " registered after NeoForge's payload registration");
            }
            if (types.containsKey(type.id())) {
                throw new IllegalArgumentException("Foxy: " + name + " payload " + type.id() + " is already registered");
            }
            @SuppressWarnings({"unchecked", "rawtypes"})
            CustomPacketPayload.TypeAndCodec<? super RegistryFriendlyByteBuf, T> entry = new CustomPacketPayload.TypeAndCodec(type, codec);
            types.put(type.id(), entry);
            return entry;
        }

        private synchronized Map<Identifier, CustomPacketPayload.TypeAndCodec<? super RegistryFriendlyByteBuf, ?>> snapshot() {
            return new LinkedHashMap<>(types);
        }
    }

    public static boolean registerServerReceiver(CustomPacketPayload.Type<?> type, ServerPlayNetworking.PlayPayloadHandler<?> handler) {
        return SERVER_RECEIVERS.putIfAbsent(type.id(), handler) == null;
    }

    public static boolean registerClientReceiver(CustomPacketPayload.Type<?> type, ClientPlayNetworking.PlayPayloadHandler<?> handler) {
        return CLIENT_RECEIVERS.putIfAbsent(type.id(), handler) == null;
    }

    static ClientPlayNetworking.PlayPayloadHandler<?> clientReceiver(Identifier id) {
        return CLIENT_RECEIVERS.get(id);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        var clientbound = CLIENTBOUND_PLAY.snapshot();
        var serverbound = SERVERBOUND_PLAY.snapshot();
        registered = true;
        if (clientbound.isEmpty() && serverbound.isEmpty()) return;

        // Fabric channels are optional: peers without the mod simply never receive them.
        PayloadRegistrar registrar = event.registrar("1").optional();
        IPayloadHandler toClient = (payload, context) -> FoxyClientBridge.receive((CustomPacketPayload) payload, context);
        IPayloadHandler toServer = (payload, context) -> receiveOnServer((CustomPacketPayload) payload, context);

        Set<Identifier> ids = new LinkedHashSet<>(clientbound.keySet());
        ids.addAll(serverbound.keySet());
        for (Identifier id : ids) {
            var s2c = clientbound.get(id);
            var c2s = serverbound.get(id);
            if (s2c != null && c2s != null) {
                if (s2c.codec() != c2s.codec()) {
                    LOGGER.warn("Foxy: payload {} uses different codecs per direction; NeoForge supports one, using the clientbound codec", id);
                }
                registrar.playBidirectional((CustomPacketPayload.Type) s2c.type(), (StreamCodec) s2c.codec(), toClient, toServer);
            } else if (s2c != null) {
                registrar.playToClient((CustomPacketPayload.Type) s2c.type(), (StreamCodec) s2c.codec(), toClient);
            } else {
                registrar.playToServer((CustomPacketPayload.Type) c2s.type(), (StreamCodec) c2s.codec(), toServer);
            }
        }
        LOGGER.info("Foxy: registered {} Fabric play payload type(s): {}", ids.size(), ids);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void receiveOnServer(CustomPacketPayload payload, IPayloadContext context) {
        ServerPlayNetworking.PlayPayloadHandler handler = SERVER_RECEIVERS.get(payload.type().id());
        if (handler == null || !(context.player() instanceof ServerPlayer player)) return;
        handler.receive(payload, new ServerPlayNetworking.Context() {
            @Override
            public net.minecraft.server.MinecraftServer server() {
                return player.level().getServer();
            }

            @Override
            public ServerPlayer player() {
                return player;
            }

            @Override
            public PacketSender responseSender() {
                return sender(player.connection);
            }
        });
    }

    public static PacketSender sender(ServerGamePacketListenerImpl listener) {
        return new PacketSender() {
            @Override
            public Packet<?> createPacket(CustomPacketPayload payload) {
                return new ClientboundCustomPayloadPacket(payload);
            }

            @Override
            public void sendPacket(Packet<?> packet) {
                listener.send(packet);
            }

            @Override
            public void disconnect(net.minecraft.network.chat.Component reason) {
                listener.disconnect(reason);
            }
        };
    }
}
