package com.leclowndu93150.foxy.fabricmc.fabric.api.event.lifecycle.v1;

import com.leclowndu93150.foxy.fabricmc.fabric.api.event.Event;
import com.leclowndu93150.foxy.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.MinecraftServer;

public final class ServerLifecycleEvents {
    private ServerLifecycleEvents() {}

    public static final Event<ServerStarting> SERVER_STARTING = EventFactory.createArrayBacked(ServerStarting.class, listeners -> server -> {
        for (ServerStarting listener : listeners) listener.onServerStarting(server);
    });

    public static final Event<ServerStarted> SERVER_STARTED = EventFactory.createArrayBacked(ServerStarted.class, listeners -> server -> {
        for (ServerStarted listener : listeners) listener.onServerStarted(server);
    });

    public static final Event<ServerStopping> SERVER_STOPPING = EventFactory.createArrayBacked(ServerStopping.class, listeners -> server -> {
        for (ServerStopping listener : listeners) listener.onServerStopping(server);
    });

    public static final Event<ServerStopped> SERVER_STOPPED = EventFactory.createArrayBacked(ServerStopped.class, listeners -> server -> {
        for (ServerStopped listener : listeners) listener.onServerStopped(server);
    });

    @FunctionalInterface
    public interface ServerStarting {
        void onServerStarting(MinecraftServer server);
    }

    @FunctionalInterface
    public interface ServerStarted {
        void onServerStarted(MinecraftServer server);
    }

    @FunctionalInterface
    public interface ServerStopping {
        void onServerStopping(MinecraftServer server);
    }

    @FunctionalInterface
    public interface ServerStopped {
        void onServerStopped(MinecraftServer server);
    }
}
