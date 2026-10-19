package com.leclowndu93150.foxy.loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only view of the parts of a {@code fabric.mod.json} that Foxy translates.
 */
public final class FabricModJson {
    public static final String FILE = "fabric.mod.json";
    public static final String NEOFORGE_MODS_TOML = "META-INF/neoforge.mods.toml";
    /** Mod property Foxy writes into every generated mods.toml so the runtime can find converted mods. */
    public static final String FOXY_MARKER_PROPERTY = "foxy:fabric";

    private final JsonObject json;

    private FabricModJson(JsonObject json) {
        this.json = json;
    }

    @Nullable
    public static FabricModJson parse(@Nullable InputStream input) {
        if (input == null) return null;
        try (InputStream in = input) {
            JsonElement root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            return root.isJsonObject() ? new FabricModJson(root.getAsJsonObject()) : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public JsonObject json() {
        return json;
    }

    public String id() {
        return string(json, "id", "fabricmod");
    }

    /** NeoForge mod ids may not contain '-', Fabric ids may. */
    public String neoForgeId() {
        return toNeoForgeId(id());
    }

    public static String toNeoForgeId(String fabricId) {
        return fabricId.replace('-', '_');
    }

    public String version() {
        return string(json, "version", "0.0.0");
    }

    /** Fully qualified class names declared for an entrypoint key, in declaration order. */
    public List<String> entrypoints(String key) {
        List<String> names = new ArrayList<>();
        JsonObject entrypoints = object(json, "entrypoints");
        if (entrypoints == null) return names;
        JsonElement value = entrypoints.get(key);
        if (value == null) return names;
        if (value.isJsonPrimitive()) {
            names.add(value.getAsString());
        } else if (value.isJsonArray()) {
            for (JsonElement entry : value.getAsJsonArray()) {
                if (entry.isJsonPrimitive()) {
                    names.add(entry.getAsString());
                } else if (entry.isJsonObject()) {
                    String className = string(entry.getAsJsonObject(), "value", null);
                    if (className != null) names.add(className);
                }
            }
        }
        return names;
    }

    public List<String> entrypointKeys() {
        JsonObject entrypoints = object(json, "entrypoints");
        return entrypoints == null ? List.of() : List.copyOf(entrypoints.keySet());
    }

    /** Mixin configs that apply to the running distribution (Fabric's per-config "environment"). */
    public List<String> mixinConfigs() {
        List<String> result = new ArrayList<>();
        JsonElement mixins = json.get("mixins");
        if (mixins == null || !mixins.isJsonArray()) return result;
        for (JsonElement element : mixins.getAsJsonArray()) {
            if (element.isJsonPrimitive()) {
                result.add(element.getAsString());
            } else if (element.isJsonObject()) {
                JsonObject entry = element.getAsJsonObject();
                String config = string(entry, "config", null);
                if (config != null && matchesEnvironment(string(entry, "environment", "*"))) {
                    result.add(config);
                }
            }
        }
        return result;
    }

    /** Paths of bundled jar-in-jar libraries. */
    public List<String> nestedJars() {
        List<String> result = new ArrayList<>();
        JsonElement jars = json.get("jars");
        if (jars == null || !jars.isJsonArray()) return result;
        for (JsonElement entry : jars.getAsJsonArray()) {
            if (entry.isJsonObject()) {
                String file = string(entry.getAsJsonObject(), "file", null);
                if (file != null) result.add(file);
            }
        }
        return result;
    }

    @Nullable
    public String accessWidener() {
        return string(json, "accessWidener", null);
    }

    private static boolean matchesEnvironment(String environment) {
        if (environment.equals("*")) return true;
        FMLLoader loader = FMLLoader.getCurrentOrNull();
        Dist dist = loader != null ? loader.getDist() : Dist.CLIENT;
        return environment.equals("client") ? dist.isClient() : !environment.equals("server") || dist.isDedicatedServer();
    }

    @Nullable
    public static JsonObject object(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    /** Primitive value of {@code key}; unexpanded build placeholders ({@code ${...}}) count as missing. */
    public static String string(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) return fallback;
        String value = element.getAsString();
        return value.startsWith("$") ? fallback : value;
    }
}
