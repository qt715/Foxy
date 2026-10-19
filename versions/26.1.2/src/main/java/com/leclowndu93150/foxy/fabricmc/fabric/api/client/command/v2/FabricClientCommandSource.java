package com.leclowndu93150.foxy.fabricmc.fabric.api.client.command.v2;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/**
 * NeoForge client commands run with a {@link CommandSourceStack}; Foxy mixes this interface into
 * it so Fabric command lambdas can cast their source.
 */
public interface FabricClientCommandSource extends SharedSuggestionProvider {
    default void sendFeedback(Component message) {
        ((CommandSourceStack) this).sendSystemMessage(message);
    }

    default void sendError(Component message) {
        ((CommandSourceStack) this).sendFailure(message);
    }

    default Minecraft getClient() {
        return Minecraft.getInstance();
    }

    default LocalPlayer getPlayer() {
        return Minecraft.getInstance().player;
    }
}
