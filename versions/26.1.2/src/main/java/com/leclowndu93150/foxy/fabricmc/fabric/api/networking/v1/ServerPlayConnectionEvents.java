package com.leclowndu93150.foxy.fabricmc.fabric.api.networking.v1;

import com.leclowndu93150.foxy.fabricmc.fabric.api.event.Event;
import com.leclowndu93150.foxy.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

public final class ServerPlayConnectionEvents {
    private ServerPlayConnectionEvents() {}

    public static final Event<Join> JOIN = EventFactory.createArrayBacked(Join.class, listeners -> (handler, sender, server) -> {
        for (Join listener : listeners) listener.onPlayReady(handler, sender, server);
    });

    public static final Event<Disconnect> DISCONNECT = EventFactory.createArrayBacked(Disconnect.class, listeners -> (handler, server) -> {
        for (Disconnect listener : listeners) listener.onPlayDisconnect(handler, server);
    });

    @FunctionalInterface
    public interface Join {
        void onPlayReady(ServerGamePacketListenerImpl handler, PacketSender sender, MinecraftServer server);
    }

    @FunctionalInterface
    public interface Disconnect {
        void onPlayDisconnect(ServerGamePacketListenerImpl handler, MinecraftServer server);
    }
}
