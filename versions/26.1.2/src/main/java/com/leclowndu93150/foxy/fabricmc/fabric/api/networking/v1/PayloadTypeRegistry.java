package com.leclowndu93150.foxy.fabricmc.fabric.api.networking.v1;

import com.leclowndu93150.foxy.bridge.FoxyNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Payload types registered here are handed to NeoForge's {@code RegisterPayloadHandlersEvent} as
 * optional play channels.
 */
public interface PayloadTypeRegistry<B extends FriendlyByteBuf> {
    <T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super B, T> register(CustomPacketPayload.Type<T> type, StreamCodec<? super B, T> codec);

    static PayloadTypeRegistry<RegistryFriendlyByteBuf> serverboundPlay() {
        return FoxyNetworking.SERVERBOUND_PLAY;
    }

    static PayloadTypeRegistry<RegistryFriendlyByteBuf> clientboundPlay() {
        return FoxyNetworking.CLIENTBOUND_PLAY;
    }
}
