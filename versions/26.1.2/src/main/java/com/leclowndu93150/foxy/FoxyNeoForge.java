package com.leclowndu93150.foxy;

import com.leclowndu93150.foxy.bridge.FoxyClientBridge;
import com.leclowndu93150.foxy.bridge.FoxyCommonBridge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod("foxy")
public class FoxyNeoForge {

    public FoxyNeoForge(IEventBus modBus, Dist dist) {
        FoxyCommonBridge.register(modBus);
        if (dist.isClient()) {
            FoxyClientBridge.register(modBus);
        }
    }
}
