package com.leclowndu93150.foxy.loader;

import com.leclowndu93150.foxy.fabricmc.api.EnvType;
import com.leclowndu93150.foxy.fabricmc.loader.api.FabricLoader;
import com.leclowndu93150.foxy.fabricmc.loader.api.ModContainer;
import com.leclowndu93150.foxy.fabricmc.loader.api.Version;
import com.leclowndu93150.foxy.fabricmc.loader.api.metadata.CustomValue;
import com.leclowndu93150.foxy.fabricmc.loader.api.metadata.ModMetadata;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class FoxyFabricLoaderImpl implements FabricLoader {
    public static final FoxyFabricLoaderImpl INSTANCE = new FoxyFabricLoaderImpl();
    private static final Logger LOGGER = LoggerFactory.getLogger("Foxy");

    /** Fabric creates one instance per entrypoint class and reuses it across entrypoint keys. */
    private final Map<String, Object> entrypointInstances = new ConcurrentHashMap<>();
    private final Map<String, List<Path>> rootPaths = new ConcurrentHashMap<>();
    private volatile List<FabricMod> fabricMods;

    /** A Fabric mod loaded through Foxy: its NeoForge mod id and parsed fabric.mod.json. */
    private record FabricMod(String modId, FabricModJson metadata) {}

    /** Runs every loaded Fabric mod's entrypoints for {@code key} (Fabric's {@code invokeEntrypoints}). */
    public static <T> void invokeEntrypoints(String key, Class<T> type, Consumer<? super T> invoker) {
        for (FabricMod mod : INSTANCE.fabricMods()) {
            for (String className : mod.metadata.entrypoints(key)) {
                T entrypoint = INSTANCE.instantiate(mod, className, type);
                LOGGER.info("Foxy: running Fabric '{}' entrypoint {} of {}", key, className, mod.modId);
                try {
                    invoker.accept(entrypoint);
                } catch (Throwable t) {
                    throw new RuntimeException("Foxy: Fabric '" + key + "' entrypoint " + className + " of mod " + mod.modId + " failed", t);
                }
            }
        }
    }

    private List<FabricMod> fabricMods() {
        List<FabricMod> mods = fabricMods;
        if (mods == null) {
            List<FabricMod> found = new ArrayList<>();
            for (net.neoforged.fml.ModContainer container : ModList.get().getSortedMods()) {
                IModInfo info = container.getModInfo();
                if (!info.getModProperties().containsKey(FabricModJson.FOXY_MARKER_PROPERTY)) continue;
                try {
                    FabricModJson metadata = FabricModJson.parse(info.getOwningFile().getFile().getContents().openFile(FabricModJson.FILE));
                    if (metadata != null) found.add(new FabricMod(info.getModId(), metadata));
                } catch (IOException e) {
                    LOGGER.error("Foxy: unable to read fabric.mod.json of {}", info.getModId(), e);
                }
            }
            fabricMods = mods = List.copyOf(found);
        }
        return mods;
    }

    private <T> T instantiate(FabricMod mod, String className, Class<T> type) {
        Object instance = entrypointInstances.get(className);
        if (instance == null) {
            // Not computeIfAbsent: entrypoint class initialisers may call back into FabricLoader.
            try {
                Class<?> clazz = Class.forName(className, true, FMLLoader.getCurrent().getCurrentClassLoader());
                Object created = clazz.getDeclaredConstructor().newInstance();
                Object existing = entrypointInstances.putIfAbsent(className, created);
                instance = existing != null ? existing : created;
            } catch (ReflectiveOperationException | LinkageError e) {
                throw new RuntimeException("Foxy: cannot create Fabric entrypoint " + className + " of mod " + mod.modId, e);
            }
        }
        if (!type.isInstance(instance)) {
            throw new IllegalStateException("Foxy: Fabric entrypoint " + className + " of mod " + mod.modId + " does not implement " + type.getName());
        }
        return type.cast(instance);
    }

    @Override
    public boolean isModLoaded(String modId) {
        String id = FabricModJson.toNeoForgeId(modId);
        ModList modList = ModList.get();
        if (modList != null) {
            return modList.isLoaded(id);
        }
        // Mixins may query this before the ModList exists.
        FMLLoader loader = FMLLoader.getCurrentOrNull();
        return loader != null && loader.getLoadingModList().getModFileById(id) != null;
    }

    @Override
    public EnvType getEnvironmentType() {
        return FMLEnvironment.getDist().isClient() ? EnvType.CLIENT : EnvType.SERVER;
    }

    @Override
    public Optional<ModContainer> getModContainer(String modId) {
        return ModList.get().getModContainerById(FabricModJson.toNeoForgeId(modId)).map(NeoModContainer::new);
    }

    @Override
    public <T> List<T> getEntrypoints(String key, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (FabricMod mod : fabricMods()) {
            for (String className : mod.metadata.entrypoints(key)) {
                result.add(instantiate(mod, className, type));
            }
        }
        return result;
    }

    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    private record NeoModContainer(net.neoforged.fml.ModContainer delegate) implements ModContainer {
        @Override
        public ModMetadata getMetadata() {
            return new NeoModMetadata(delegate.getModInfo());
        }

        @Override
        public List<Path> getRootPaths() {
            return INSTANCE.rootPaths.computeIfAbsent(delegate.getModId(), id -> {
                Path file = delegate.getModInfo().getOwningFile().getFile().getFilePath();
                if (Files.isRegularFile(file)) {
                    try {
                        return List.of(FileSystems.newFileSystem(file).getRootDirectories().iterator().next());
                    } catch (IOException e) {
                        LOGGER.warn("Foxy: cannot open {} as a file system", file, e);
                    }
                }
                return List.of(file);
            });
        }
    }

    private record NeoModMetadata(IModInfo info) implements ModMetadata {
        @Override
        public Version getVersion() {
            String v = info.getVersion().toString();
            return () -> v;
        }

        @Override
        public CustomValue getCustomValue(String key) {
            Object val = info.getModProperties().get(key);
            if (val == null) return null;
            String s = val.toString();
            return () -> s;
        }
    }
}
