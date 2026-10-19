package com.leclowndu93150.foxy.fabricmc.fabric.api.networking.v1;

import com.leclowndu93150.foxy.bridge.FoxyNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ServerPlayNetworking {
    private ServerPlayNetworking() {}

    public static <T extends CustomPacketPayload> boolean registerGlobalReceiver(CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler) {
        return FoxyNetworking.registerServerReceiver(type, handler);
    }

    public static boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return player.connection.hasChannel(type);
    }

    /**
     * Payloads for channels the client did not negotiate are dropped: NeoForge would reject them,
     * while on Fabric the client silently ignores unknown payloads.
     */
    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection.hasChannel(payload)) {
            player.connection.send(payload);
        }
    }

    @FunctionalInterface
    public interface PlayPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context context);
    }

    public interface Context {
        MinecraftServer server();

        ServerPlayer player();

        PacketSender responseSender();
    }
}
