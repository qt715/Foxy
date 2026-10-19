package com.leclowndu93150.foxy.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fixes a server deadlock during chunk generation.
 *
 * <p>Structure templates (igloos) create zombie villagers on a worldgen worker with the
 * {@link ServerLevel} as their level, and {@code initializeVillagerData} looks up the biome
 * through the live chunk cache. Off the server thread that lookup is handed to the server thread
 * and joined; if the looked-up chunk is itself mid-generation (the entity is still at the origin
 * while it is being constructed, so this is chunk 0,0), the server thread waits for generation
 * that the blocked worker holds up. Voxy World Gen V2 keeps chunks generating everywhere, which
 * makes this hang reachable in normal play.
 *
 * <p>Off the server thread the biome is taken straight from the generator instead; no chunk is
 * touched. On the server thread vanilla behaviour is unchanged.
 */
@Mixin(ZombieVillager.class)
public abstract class MixinZombieVillager {
    @WrapOperation(method = "initializeVillagerData", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"))
    private Holder<Biome> foxy$biomeWithoutChunkLoad(Level level, BlockPos pos, Operation<Holder<Biome>> original) {
        if (level instanceof ServerLevel serverLevel && !serverLevel.getServer().isSameThread()) {
            return serverLevel.getUncachedNoiseBiome(
                    QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ()));
        }
        return original.call(level, pos);
    }
}
