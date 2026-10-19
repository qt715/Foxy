package com.leclowndu93150.foxy.mixin.fabric;

import com.leclowndu93150.foxy.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CommandSourceStack.class)
public abstract class MixinCommandSourceStack implements FabricClientCommandSource {
}
