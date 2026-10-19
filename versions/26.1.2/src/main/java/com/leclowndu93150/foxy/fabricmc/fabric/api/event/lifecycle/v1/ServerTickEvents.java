package com.leclowndu93150.foxy.fabricmc.fabric.api.event.lifecycle.v1;

import com.leclowndu93150.foxy.fabricmc.fabric.api.event.Event;
import com.leclowndu93150.foxy.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.MinecraftServer;

public final class ServerTickEvents {
    private ServerTickEvents() {}

    public static final Event<StartTick> START_SERVER_TICK = EventFactory.createArrayBacked(StartTick.class, listeners -> server -> {
        for (StartTick listener : listeners) listener.onStartTick(server);
    });

    public static final Event<EndTick> END_SERVER_TICK = EventFactory.createArrayBacked(EndTick.class, listeners -> server -> {
        for (EndTick listener : listeners) listener.onEndTick(server);
    });

    @FunctionalInterface
    public interface StartTick {
        void onStartTick(MinecraftServer server);
    }

    @FunctionalInterface
    public interface EndTick {
        void onEndTick(MinecraftServer server);
    }
}
