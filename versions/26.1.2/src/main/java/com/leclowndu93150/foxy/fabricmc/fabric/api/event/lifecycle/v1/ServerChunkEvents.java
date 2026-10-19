package com.leclowndu93150.foxy.fabricmc.fabric.api.event.lifecycle.v1;

import com.leclowndu93150.foxy.fabricmc.fabric.api.event.Event;
import com.leclowndu93150.foxy.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

public final class ServerChunkEvents {
    private ServerChunkEvents() {}

    public static final Event<Load> CHUNK_LOAD = EventFactory.createArrayBacked(Load.class, listeners -> (level, chunk, generated) -> {
        for (Load listener : listeners) listener.onChunkLoad(level, chunk, generated);
    });

    public static final Event<Unload> CHUNK_UNLOAD = EventFactory.createArrayBacked(Unload.class, listeners -> (level, chunk) -> {
        for (Unload listener : listeners) listener.onChunkUnload(level, chunk);
    });

    @FunctionalInterface
    public interface Load {
        void onChunkLoad(ServerLevel level, LevelChunk chunk, boolean generated);
    }

    @FunctionalInterface
    public interface Unload {
        void onChunkUnload(ServerLevel level, LevelChunk chunk);
    }
}
