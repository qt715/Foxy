package com.leclowndu93150.foxy.loader;

import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.neoforgespi.locating.IDependencyLocator;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Adds the jar-in-jar libraries of Fabric mods as game libraries. */
public class FoxyDependencyLocator implements IDependencyLocator {
    private static final Logger LOGGER = LoggerFactory.getLogger("Foxy");

    @Override
    public void scanMods(List<IModFile> loadedMods, IDiscoveryPipeline pipeline) {
        for (IModFile mod : loadedMods) {
            // Multi-loader jars also list their Fabric jar-in-jar in fabric.mod.json, but their
            // NeoForge build ships its own; only mods Foxy converted need extraction.
            if (!isFoxyConverted(mod)) {
                continue;
            }
            JarContents contents = mod.getContents();
            FabricModJson fmj;
            try {
                fmj = FabricModJson.parse(contents.openFile(FabricModJson.FILE));
            } catch (IOException e) {
                continue;
            }
            if (fmj == null) continue;
            for (String innerPath : fmj.nestedJars()) {
                addNestedJar(contents, innerPath, pipeline);
            }
        }
    }

    private static boolean isFoxyConverted(IModFile mod) {
        for (var info : mod.getModInfos()) {
            if (info.getModProperties().containsKey(FabricModJson.FOXY_MARKER_PROPERTY)) return true;
        }
        return false;
    }

    private static void addNestedJar(JarContents contents, String innerPath, IDiscoveryPipeline pipeline) {
        String normalized = innerPath.replace('\\', '/');
        if (!NativePlatform.isCurrentPlatformJar(normalized)) {
            return;
        }
        try {
            Path extracted = extract(contents, normalized);
            if (extracted == null) return;
            String provider = providedBy(extracted);
            if (provider != null) {
                LOGGER.info("Foxy: skipping bundled {}; its classes are already provided by {}", normalized, provider);
                return;
            }
            pipeline.addModFile(IModFile.create(
                    JarContents.ofPath(extracted),
                    JarModsDotTomlModFileReader::manifestParser,
                    IModFile.Type.GAMELIBRARY,
                    ModFileDiscoveryAttributes.DEFAULT));
        } catch (IOException e) {
            LOGGER.warn("Foxy: unable to extract bundled library {}", normalized, e);
        }
    }

    /**
     * Returns where the game already gets this library from, if it does. Fabric mods bundle
     * libraries Minecraft ships too (Voxy bundles lz4-java 1.8.0, Minecraft 26.1 ships 1.10.x);
     * loading both would put two copies of the same packages on the game class path.
     */
    private static String providedBy(Path jar) throws IOException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) loader = ClassLoader.getSystemClassLoader();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.endsWith(".class") || name.startsWith("META-INF/") || name.endsWith("module-info.class") || name.endsWith("package-info.class")) {
                    continue;
                }
                var existing = loader.getResource(name);
                return existing == null ? null : existing.toString();
            }
        }
        return null;
    }

    private static Path extract(JarContents contents, String innerPath) throws IOException {
        if (!contents.containsFile(innerPath)) {
            return null;
        }
        String name = innerPath.substring(innerPath.lastIndexOf('/') + 1);
        Path out = Files.createTempFile("foxy-jij-", "-" + name);
        out.toFile().deleteOnExit();
        try (InputStream is = contents.openFile(innerPath)) {
            Files.copy(is, out, StandardCopyOption.REPLACE_EXISTING);
        }
        return out;
    }

    @Override
    public int getPriority() {
        return LOWEST_SYSTEM_PRIORITY;
    }

    /** Native classifier filtering, so e.g. Windows natives are not loaded (and picked over Linux ones) on Linux. */
    static final class NativePlatform {
        private NativePlatform() {}

        static boolean isCurrentPlatformJar(String path) {
            return isCurrentPlatformJar(path, System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
        }

        static boolean isCurrentPlatformJar(String path, String osName, String osArch) {
            String normalized = path.toLowerCase(Locale.ROOT);
            if (!normalized.contains("-natives-")) return true;

            String os = osName.toLowerCase(Locale.ROOT);
            String arch = architecture(osArch);
            if (os.contains("mac") || os.contains("darwin")) {
                return matches(normalized, "macos", arch) || matches(normalized, "osx", arch);
            }
            if (os.contains("win")) return matches(normalized, "windows", arch);
            if (os.contains("linux") || os.contains("unix")) return matches(normalized, "linux", arch);
            return true;
        }

        private static String architecture(String arch) {
            return switch (arch.toLowerCase(Locale.ROOT)) {
                case "aarch64", "arm64" -> "arm64";
                case "arm", "arm32", "armv7l" -> "arm32";
                case "amd64", "x86_64", "x64" -> "x86_64";
                case "x86", "i386", "i486", "i586", "i686" -> "x86";
                default -> "other";
            };
        }

        private static boolean matches(String path, String platform, String arch) {
            String marker = "-natives-" + platform;
            int index = path.indexOf(marker);
            if (index < 0) return false;
            String classifier = path.substring(index + marker.length());
            String nativeArch = switch (classifier) {
                case "-arm64.jar", "-aarch64.jar" -> "arm64";
                case "-arm32.jar", "-arm.jar" -> "arm32";
                case "-x86_64.jar", "-x64.jar" -> "x86_64";
                case "-x86.jar" -> "x86";
                // An unqualified classifier (natives-linux) is the platform's x86-64 build.
                case ".jar" -> "x86_64";
                default -> null;
            };
            return arch.equals(nativeArch);
        }
    }
}
