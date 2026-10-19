package com.leclowndu93150.foxy.fabricmc.fabric.api.client.command.v2;

import com.mojang.brigadier.CommandDispatcher;
import com.leclowndu93150.foxy.fabricmc.fabric.api.event.Event;
import com.leclowndu93150.foxy.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.commands.CommandBuildContext;

/** Fired from NeoForge's {@code RegisterClientCommandsEvent}. */
@FunctionalInterface
public interface ClientCommandRegistrationCallback {
    Event<ClientCommandRegistrationCallback> EVENT = EventFactory.createArrayBacked(ClientCommandRegistrationCallback.class,
            listeners -> (dispatcher, registryAccess) -> {
                for (ClientCommandRegistrationCallback listener : listeners) {
                    listener.register(dispatcher, registryAccess);
                }
            });

    void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext registryAccess);
}
