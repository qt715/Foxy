package com.leclowndu93150.foxy.fabricmc.fabric.api.client.event.lifecycle.v1;

import com.leclowndu93150.foxy.fabricmc.fabric.api.event.Event;
import com.leclowndu93150.foxy.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.Minecraft;

public final class ClientTickEvents {
    private ClientTickEvents() {}

    public static final Event<StartTick> START_CLIENT_TICK = EventFactory.createArrayBacked(StartTick.class, listeners -> client -> {
        for (StartTick listener : listeners) listener.onStartTick(client);
    });

    public static final Event<EndTick> END_CLIENT_TICK = EventFactory.createArrayBacked(EndTick.class, listeners -> client -> {
        for (EndTick listener : listeners) listener.onEndTick(client);
    });

    @FunctionalInterface
    public interface StartTick {
        void onStartTick(Minecraft client);
    }

    @FunctionalInterface
    public interface EndTick {
        void onEndTick(Minecraft client);
    }
}
