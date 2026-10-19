package com.leclowndu93150.foxy.fabricmc.loader.api.metadata;

import com.leclowndu93150.foxy.fabricmc.loader.api.Version;

public interface ModMetadata {
    Version getVersion();
    CustomValue getCustomValue(String key);
}
