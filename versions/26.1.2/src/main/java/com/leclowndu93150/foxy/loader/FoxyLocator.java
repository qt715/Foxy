package com.leclowndu93150.foxy.loader;

import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

/**
 * Re-adds Foxy itself as a mod and picks up Fabric jars that sit on the class path (development
 * runs); Fabric jars in the mods folder are found by FML's own mods-folder locator.
 */
public class FoxyLocator implements IModFileCandidateLocator {

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        readdSelf(pipeline);

        for (Path jar : classPathFabricJars()) {
            if (!context.isLocated(jar)) {
                pipeline.addPath(jar, ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.WARN_ON_KNOWN_INCOMPATIBILITY);
            }
        }
    }

    private static void readdSelf(IDiscoveryPipeline pipeline) {
        List<Path> ownPaths = ownModPaths();
        if (ownPaths.isEmpty()) {
            return;
        }
        try {
            pipeline.addJarContent(JarContents.ofPaths(ownPaths), ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.ERROR);
        } catch (IOException ignored) {}
    }

    private static List<Path> ownModPaths() {
        String modFolders = System.getenv("MOD_CLASSES");
        if (modFolders == null) {
            modFolders = System.getProperty("fml.modFolders", "");
        }
        List<Path> paths = new ArrayList<>();
        for (String entry : modFolders.split(File.pathSeparator)) {
            int split = entry.indexOf("%%");
            String id = split != -1 ? entry.substring(0, split) : "";
            String path = split != -1 ? entry.substring(split + 2) : entry;
            if (id.equals("foxy") && !path.isEmpty()) {
                paths.add(new File(path).toPath());
            }
        }
        if (paths.isEmpty()) {
            try {
                URI self = FoxyLocator.class.getProtectionDomain().getCodeSource().getLocation().toURI();
                paths.add(Path.of(self));
            } catch (Exception ignored) {}
        }
        return paths;
    }

    /** Class path jars that are Fabric-only mods (same criterion as {@link FoxyFabricModReader}). */
    private static List<Path> classPathFabricJars() {
        List<Path> jars = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            if (!entry.endsWith(".jar")) continue;
            Path path = Path.of(entry);
            if (!Files.isRegularFile(path)) continue;
            try (ZipFile zip = new ZipFile(path.toFile())) {
                if (zip.getEntry(FabricModJson.FILE) != null && zip.getEntry(FabricModJson.NEOFORGE_MODS_TOML) == null) {
                    jars.add(path);
                }
            } catch (IOException ignored) {}
        }
        return jars;
    }

    @Override
    public int getPriority() {
        return LOWEST_SYSTEM_PRIORITY + 1;
    }
}
