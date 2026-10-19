package com.leclowndu93150.foxy.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public interface PacketSender {
    Packet<?> createPacket(CustomPacketPayload payload);

    void sendPacket(Packet<?> packet);

    default void sendPacket(CustomPacketPayload payload) {
        sendPacket(createPacket(payload));
    }

    void disconnect(Component reason);
}
