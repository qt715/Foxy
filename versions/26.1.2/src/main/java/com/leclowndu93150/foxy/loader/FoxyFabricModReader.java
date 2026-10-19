package com.leclowndu93150.foxy.loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileReader;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Claims Fabric-only jars and hands FML a temporary copy with a {@code neoforge.mods.toml}
 * generated from {@code fabric.mod.json} and its {@code net.fabricmc} references pointed at Foxy's
 * shim ({@link FabricApiRelocation}). The jar in the mods folder is never modified.
 */
public class FoxyFabricModReader implements IModFileReader {
    private static final Logger LOGGER = LoggerFactory.getLogger("Foxy");

    /** Fabric entrypoints Foxy invokes itself; every other key is exposed as a mod property. */
    private static final Set<String> LIFECYCLE_ENTRYPOINTS = Set.of("main", "client", "server");
    /** Dependencies provided by the platform itself (Foxy stands in for Fabric Loader and the Fabric API surface it bridges). */
    private static final Set<String> PLATFORM_DEPENDENCIES = Set.of("minecraft", "java", "fabricloader", "fabric", "fabric-api", "quilt_loader");
    /**
     * Older Voxy builds read {@code custom.commit} unconditionally; development jars ship the
     * unexpanded {@code ${commit}} placeholder.
     */
    private static final String FALLBACK_COMMIT = "foxycompat0000000000000000000000000000000";

    @Override
    public @Nullable IModFile read(JarContents jar, ModFileDiscoveryAttributes attributes) {
        if (!jar.containsFile(FabricModJson.FILE) || jar.containsFile(FabricModJson.NEOFORGE_MODS_TOML)) {
            return null;
        }
        if (!Files.isRegularFile(jar.getPrimaryPath())) {
            return null;
        }
        // Jars nested in another mod (NeoForge jar-in-jar) are that mod's libraries, even when they
        // carry Fabric metadata, e.g. multi-loader libraries bundled by LambDynamicLights.
        if (attributes.parent() != null) {
            return null;
        }

        FabricModJson fmj;
        try {
            fmj = FabricModJson.parse(jar.openFile(FabricModJson.FILE));
        } catch (IOException e) {
            return null;
        }
        if (fmj == null) {
            return null;
        }

        try {
            Path patched = patchJar(jar.getPrimaryPath(), fmj);
            LOGGER.info("Foxy: loading Fabric mod {} {} from {}", fmj.id(), fmj.version(), jar.getPrimaryPath().getFileName());
            return JarModsDotTomlModFileReader.createModFile(JarContents.ofPath(patched), attributes);
        } catch (IOException e) {
            throw new RuntimeException("Foxy: failed to prepare Fabric mod " + fmj.id() + " from " + jar.getPrimaryPath(), e);
        }
    }

    private static Path patchJar(Path original, FabricModJson fmj) throws IOException {
        Path patched = Files.createTempFile("foxy-patched-" + fmj.neoForgeId() + "-", ".jar");
        patched.toFile().deleteOnExit();

        try (ZipFile in = new ZipFile(original.toFile());
             ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(patched))) {
            var entries = in.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().equals(FabricModJson.NEOFORGE_MODS_TOML)) continue;
                out.putNextEntry(new ZipEntry(entry.getName()));
                if (!entry.isDirectory()) {
                    try (InputStream is = in.getInputStream(entry)) {
                        if (entry.getName().endsWith(".class")) {
                            out.write(FabricApiRelocation.relocate(is.readAllBytes()));
                        } else {
                            is.transferTo(out);
                        }
                    }
                }
                out.closeEntry();
            }
            out.putNextEntry(new ZipEntry(FabricModJson.NEOFORGE_MODS_TOML));
            out.write(buildModsToml(fmj).getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return patched;
    }

    static String buildModsToml(FabricModJson fmj) {
        JsonObject json = fmj.json();
        String modId = fmj.neoForgeId();
        String name = FabricModJson.string(json, "name", fmj.id());
        String description = FabricModJson.string(json, "description", "");
        String icon = icon(json);
        String authors = joinAuthors(json);

        StringBuilder sb = new StringBuilder();
        sb.append("modLoader = \"javafml\"\n");
        sb.append("loaderVersion = \"[1,)\"\n");
        sb.append("license = ").append(str(FabricModJson.string(json, "license", "All-Rights-Reserved"))).append('\n');
        sb.append("[[mods]]\n");
        sb.append("modId = ").append(str(modId)).append('\n');
        sb.append("version = ").append(str(fmj.version())).append('\n');
        sb.append("displayName = ").append(str(name)).append('\n');
        if (!description.isEmpty()) sb.append("description = ").append(str(description)).append('\n');
        if (!authors.isEmpty()) sb.append("authors = ").append(str(authors)).append('\n');
        if (icon != null) sb.append("logoFile = ").append(str(icon)).append('\n');

        sb.append("[modproperties.").append(modId).append("]\n");
        for (Map.Entry<String, String> property : modProperties(fmj).entrySet()) {
            sb.append(str(property.getKey())).append(" = ").append(str(property.getValue())).append('\n');
        }

        dependency(sb, modId, "neoforge", "required", "[1,)", "BOTH");
        JsonObject depends = FabricModJson.object(json, "depends");
        if (depends != null) {
            for (Map.Entry<String, JsonElement> dep : depends.entrySet()) {
                if (isPlatformDependency(dep.getKey())) continue;
                // Fabric dependencies carry no side, but client-only mods (Sodium, Iris, ...) are
                // absent on dedicated servers, where Fabric jars satisfy them with "provides"
                // stubs Foxy does not load. A BOTH-side requirement would make such mods
                // unloadable on servers.
                dependency(sb, modId, FabricModJson.toNeoForgeId(dep.getKey()), "required",
                        FabricVersionRanges.lowestLowerBound(dep.getValue()), "CLIENT");
            }
        }
        JsonObject breaks = FabricModJson.object(json, "breaks");
        if (breaks != null) {
            for (Map.Entry<String, JsonElement> dep : breaks.entrySet()) {
                if (isPlatformDependency(dep.getKey())) continue;
                for (String range : FabricVersionRanges.exactRanges(dep.getValue())) {
                    dependency(sb, modId, FabricModJson.toNeoForgeId(dep.getKey()), "incompatible", range, "BOTH");
                    sb.append("reason = ").append(str(fmj.id() + " declares itself incompatible with " + dep.getKey() + " " + range)).append('\n');
                }
            }
        }

        for (String mixin : fmj.mixinConfigs()) {
            sb.append("[[mixins]]\nconfig = ").append(str(mixin)).append('\n');
        }
        return sb.toString();
    }

    /**
     * Mod properties: Fabric {@code custom} values, then one property per non-lifecycle entrypoint
     * (NeoForge integrations such as Sodium's {@code sodium:config_api_user} read class names from
     * mod properties), then Foxy's marker.
     */
    private static Map<String, String> modProperties(FabricModJson fmj) {
        Map<String, String> properties = new LinkedHashMap<>();
        JsonObject custom = FabricModJson.object(fmj.json(), "custom");
        if (custom != null) {
            for (Map.Entry<String, JsonElement> entry : custom.entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    String value = entry.getValue().getAsString();
                    if (!value.startsWith("$")) properties.put(entry.getKey(), value);
                }
            }
        }
        properties.putIfAbsent("commit", FALLBACK_COMMIT);
        for (String key : fmj.entrypointKeys()) {
            if (LIFECYCLE_ENTRYPOINTS.contains(key)) continue;
            var classes = fmj.entrypoints(key);
            if (!classes.isEmpty()) properties.putIfAbsent(key, classes.getFirst());
        }
        properties.put(FabricModJson.FOXY_MARKER_PROPERTY, fmj.id());
        return properties;
    }

    private static void dependency(StringBuilder sb, String owner, String modId, String type, String range, String side) {
        sb.append("[[dependencies.").append(owner).append("]]\n");
        sb.append("modId = ").append(str(modId)).append('\n');
        sb.append("type = ").append(str(type)).append('\n');
        sb.append("versionRange = ").append(str(range)).append('\n');
        sb.append("ordering = \"NONE\"\n");
        sb.append("side = ").append(str(side)).append('\n');
    }

    private static boolean isPlatformDependency(String id) {
        return PLATFORM_DEPENDENCIES.contains(id) || id.startsWith("fabric-") || id.startsWith("fabric_");
    }

    @Nullable
    private static String icon(JsonObject json) {
        JsonElement icon = json.get("icon");
        if (icon == null) return null;
        if (icon.isJsonPrimitive()) return FabricModJson.string(json, "icon", null);
        if (icon.isJsonObject()) {
            // Size-keyed icon map: pick the largest.
            String best = null;
            int bestSize = -1;
            for (Map.Entry<String, JsonElement> entry : icon.getAsJsonObject().entrySet()) {
                try {
                    int size = Integer.parseInt(entry.getKey());
                    if (size > bestSize && entry.getValue().isJsonPrimitive()) {
                        bestSize = size;
                        best = entry.getValue().getAsString();
                    }
                } catch (NumberFormatException ignored) {}
            }
            return best;
        }
        return null;
    }

    private static String joinAuthors(JsonObject json) {
        JsonElement authors = json.get("authors");
        if (authors == null || !authors.isJsonArray()) return "";
        StringJoiner sj = new StringJoiner(", ");
        for (JsonElement el : authors.getAsJsonArray()) {
            if (el.isJsonPrimitive()) {
                sj.add(el.getAsString());
            } else if (el.isJsonObject() && el.getAsJsonObject().has("name")) {
                sj.add(el.getAsJsonObject().get("name").getAsString());
            }
        }
        return sj.toString();
    }

    /** TOML basic string. */
    private static String str(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    @Override
    public int getPriority() {
        return HIGHEST_SYSTEM_PRIORITY;
    }
}
