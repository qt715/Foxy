package com.leclowndu93150.foxy.bridge;

import com.leclowndu93150.foxy.loader.FoxyFabricLoaderImpl;
import com.mojang.brigadier.CommandDispatcher;
import com.leclowndu93150.foxy.fabricmc.api.ClientModInitializer;
import com.leclowndu93150.foxy.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import com.leclowndu93150.foxy.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import com.leclowndu93150.foxy.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.leclowndu93150.foxy.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import com.leclowndu93150.foxy.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.leclowndu93150.foxy.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Runs Fabric "client" entrypoints and fires the bridged Fabric client-side events. Client dist only. */
public final class FoxyClientBridge {
    private FoxyClientBridge() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(() ->
                FoxyFabricLoaderImpl.invokeEntrypoints("client", ClientModInitializer.class, ClientModInitializer::onInitializeClient)));

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(RegisterClientCommandsEvent.class, FoxyClientBridge::registerCommands);
        bus.addListener(ClientTickEvent.Pre.class, e -> ClientTickEvents.START_CLIENT_TICK.invoker().onStartTick(Minecraft.getInstance()));
        bus.addListener(ClientTickEvent.Post.class, e -> ClientTickEvents.END_CLIENT_TICK.invoker().onEndTick(Minecraft.getInstance()));
        bus.addListener(ClientPlayerNetworkEvent.LoggingIn.class, e -> {
            ClientPacketListener listener = e.getPlayer().connection;
            ClientPlayConnectionEvents.JOIN.invoker().onPlayReady(listener, sender(listener), Minecraft.getInstance());
        });
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e ->
                ClientPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(listenerOf(e), Minecraft.getInstance()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerCommands(RegisterClientCommandsEvent event) {
        // NeoForge client command sources are CommandSourceStacks, which Foxy's mixin makes FabricClientCommandSources.
        CommandDispatcher<FabricClientCommandSource> dispatcher = (CommandDispatcher) event.getDispatcher();
        ClientCommandRegistrationCallback.EVENT.invoker().register(dispatcher, event.getBuildContext());
    }

    /** Fabric passes the disconnecting listener; NeoForge only exposes the connection, which is null when login never completed. */
    private static ClientPacketListener listenerOf(ClientPlayerNetworkEvent.LoggingOut event) {
        Connection connection = event.getConnection();
        if (connection != null && connection.getPacketListener() instanceof ClientPacketListener listener) {
            return listener;
        }
        return Minecraft.getInstance().getConnection();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void receive(CustomPacketPayload payload, IPayloadContext context) {
        ClientPlayNetworking.PlayPayloadHandler handler = FoxyNetworking.clientReceiver(payload.type().id());
        if (handler == null) return;
        Minecraft client = Minecraft.getInstance();
        ClientPacketListener listener = client.getConnection();
        handler.receive(payload, new ClientPlayNetworking.Context() {
            @Override
            public Minecraft client() {
                return client;
            }

            @Override
            public LocalPlayer player() {
                return client.player;
            }

            @Override
            public PacketSender responseSender() {
                return sender(listener);
            }
        });
    }

    private static PacketSender sender(ClientPacketListener listener) {
        return new PacketSender() {
            @Override
            public Packet<?> createPacket(CustomPacketPayload payload) {
                return new ServerboundCustomPayloadPacket(payload);
            }

            @Override
            public void sendPacket(Packet<?> packet) {
                listener.send(packet);
            }

            @Override
            public void disconnect(net.minecraft.network.chat.Component reason) {
                listener.getConnection().disconnect(reason);
            }
        };
    }
}
