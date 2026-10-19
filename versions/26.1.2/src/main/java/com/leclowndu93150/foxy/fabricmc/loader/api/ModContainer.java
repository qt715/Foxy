package com.leclowndu93150.foxy.fabricmc.loader.api;

import com.leclowndu93150.foxy.fabricmc.loader.api.metadata.ModMetadata;

import java.nio.file.Path;
import java.util.List;

public interface ModContainer {
    ModMetadata getMetadata();
    List<Path> getRootPaths();
}
