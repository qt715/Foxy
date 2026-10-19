package com.leclowndu93150.foxy.loader;

import net.neoforged.fml.loading.FMLPaths;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Access widener entries of every Fabric-only mod jar, plus Foxy's own supplement. */
public final class AccessWidenerData {
    public static final int ACCESSIBLE = 1;
    public static final int EXTENDABLE = 2;
    public static final int MUTABLE = 4;

    /** Dotted class name to access flags. */
    public final Map<String, Integer> classes = new HashMap<>();
    /** Dotted owner to field name to access flags. */
    public final Map<String, Map<String, Integer>> fields = new HashMap<>();
    /** Dotted owner to name+descriptor to access flags. */
    public final Map<String, Map<String, Integer>> methods = new HashMap<>();

    private static AccessWidenerData instance;

    public static synchronized AccessWidenerData get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static AccessWidenerData load() {
        AccessWidenerData data = new AccessWidenerData();
        for (Path jar : candidateJars()) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                ZipEntry fmjEntry = zip.getEntry(FabricModJson.FILE);
                // Multi-loader jars ship a NeoForge build; their Fabric access widener is not for this loader.
                if (fmjEntry == null || zip.getEntry(FabricModJson.NEOFORGE_MODS_TOML) != null) continue;
                FabricModJson fmj = FabricModJson.parse(zip.getInputStream(fmjEntry));
                String awName = fmj == null ? null : fmj.accessWidener();
                if (awName == null) continue;
                ZipEntry awEntry = zip.getEntry(awName);
                if (awEntry == null) continue;
                try (InputStream is = zip.getInputStream(awEntry)) {
                    data.parse(is);
                }
            } catch (IOException ignored) {}
        }
        try (InputStream extra = Thread.currentThread().getContextClassLoader().getResourceAsStream("foxy-extra.accesswidener")) {
            if (extra != null) {
                data.parse(extra);
            }
        } catch (IOException ignored) {}
        return data;
    }

    private static List<Path> candidateJars() {
        List<Path> jars = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            if (entry.endsWith(".jar")) {
                jars.add(Path.of(entry));
            }
        }
        try {
            Path mods = FMLPaths.MODSDIR.get();
            if (mods != null && Files.isDirectory(mods)) {
                try (Stream<Path> walk = Files.list(mods)) {
                    walk.filter(p -> p.getFileName().toString().endsWith(".jar")).forEach(jars::add);
                }
            }
        } catch (Throwable ignored) {}
        return jars;
    }

    private void parse(InputStream is) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                int comment = line.indexOf('#');
                if (comment >= 0) line = line.substring(0, comment);
                line = line.strip();
                if (line.isEmpty()) continue;
                if (header) {
                    header = false;
                    continue;
                }
                String[] parts = line.split("\\s+");
                if (parts.length < 3) continue;
                String access = parts[0].startsWith("transitive-") ? parts[0].substring("transitive-".length()) : parts[0];
                int flag = switch (access) {
                    case "accessible" -> ACCESSIBLE;
                    case "extendable" -> EXTENDABLE;
                    case "mutable" -> MUTABLE;
                    default -> 0;
                };
                if (flag == 0) continue;
                String owner = parts[2].replace('/', '.');
                switch (parts[1]) {
                    case "class" -> classes.merge(owner, flag, (a, b) -> a | b);
                    case "field" -> {
                        if (parts.length >= 5) {
                            fields.computeIfAbsent(owner, k -> new HashMap<>()).merge(parts[3], flag, (a, b) -> a | b);
                        }
                    }
                    case "method" -> {
                        if (parts.length >= 5) {
                            methods.computeIfAbsent(owner, k -> new HashMap<>()).merge(parts[3] + parts[4], flag, (a, b) -> a | b);
                        }
                    }
                    default -> {}
                }
            }
        }
    }

    public Set<String> allTargets() {
        Set<String> all = new HashSet<>(classes.keySet());
        all.addAll(fields.keySet());
        all.addAll(methods.keySet());
        return all;
    }
}
