package com.leclowndu93150.foxy.bridge;

import com.leclowndu93150.foxy.loader.FoxyFabricLoaderImpl;
import com.leclowndu93150.foxy.fabricmc.api.ModInitializer;
import com.leclowndu93150.foxy.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import com.leclowndu93150.foxy.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import com.leclowndu93150.foxy.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.leclowndu93150.foxy.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Runs Fabric "main" entrypoints and fires the bridged Fabric server-side events from NeoForge events. */
public final class FoxyCommonBridge {
    private FoxyCommonBridge() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(() ->
                FoxyFabricLoaderImpl.invokeEntrypoints("main", ModInitializer.class, ModInitializer::onInitialize)));
        modBus.addListener(FoxyNetworking::onRegisterPayloadHandlers);

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(ServerStartingEvent.class, e -> ServerLifecycleEvents.SERVER_STARTING.invoker().onServerStarting(e.getServer()));
        bus.addListener(ServerStartedEvent.class, e -> ServerLifecycleEvents.SERVER_STARTED.invoker().onServerStarted(e.getServer()));
        bus.addListener(ServerStoppingEvent.class, e -> ServerLifecycleEvents.SERVER_STOPPING.invoker().onServerStopping(e.getServer()));
        bus.addListener(ServerStoppedEvent.class, e -> ServerLifecycleEvents.SERVER_STOPPED.invoker().onServerStopped(e.getServer()));
        bus.addListener(ServerTickEvent.Pre.class, e -> ServerTickEvents.START_SERVER_TICK.invoker().onStartTick(e.getServer()));
        bus.addListener(ServerTickEvent.Post.class, e -> ServerTickEvents.END_SERVER_TICK.invoker().onEndTick(e.getServer()));
        bus.addListener(ChunkEvent.Load.class, e -> {
            if (e.getLevel() instanceof ServerLevel level) {
                ServerChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(level, e.getChunk(), e.isNewChunk());
            }
        });
        bus.addListener(ChunkEvent.Unload.class, e -> {
            if (e.getLevel() instanceof ServerLevel level) {
                ServerChunkEvents.CHUNK_UNLOAD.invoker().onChunkUnload(level, e.getChunk());
            }
        });
        bus.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer player) {
                ServerPlayConnectionEvents.JOIN.invoker().onPlayReady(player.connection, FoxyNetworking.sender(player.connection), player.level().getServer());
            }
        });
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer player) {
                ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(player.connection, player.level().getServer());
            }
        });
    }
}
