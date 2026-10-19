package com.leclowndu93150.foxy.fabricmc.fabric.api.client.networking.v1;

import com.leclowndu93150.foxy.fabricmc.fabric.api.event.Event;
import com.leclowndu93150.foxy.fabricmc.fabric.api.event.EventFactory;
import com.leclowndu93150.foxy.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

public final class ClientPlayConnectionEvents {
    private ClientPlayConnectionEvents() {}

    public static final Event<Join> JOIN = EventFactory.createArrayBacked(Join.class, listeners -> (handler, sender, client) -> {
        for (Join listener : listeners) listener.onPlayReady(handler, sender, client);
    });

    public static final Event<Disconnect> DISCONNECT = EventFactory.createArrayBacked(Disconnect.class, listeners -> (handler, client) -> {
        for (Disconnect listener : listeners) listener.onPlayDisconnect(handler, client);
    });

    @FunctionalInterface
    public interface Join {
        void onPlayReady(ClientPacketListener handler, PacketSender sender, Minecraft client);
    }

    @FunctionalInterface
    public interface Disconnect {
        void onPlayDisconnect(ClientPacketListener handler, Minecraft client);
    }
}
