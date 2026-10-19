package com.leclowndu93150.foxy.loader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/**
 * Points the bytecode of converted Fabric mods at Foxy's Fabric shim
 * ({@code com.leclowndu93150.foxy.fabricmc}) instead of {@code net.fabricmc}.
 *
 * <p>The shim is deliberately not published under {@code net.fabricmc}: multi-loader libraries
 * bundled by NeoForge mods (MixinConstraints, AsyncParticles, ...) decide they run on Fabric when
 * {@code net.fabricmc.loader.api.FabricLoader} exists and then call Fabric Loader API Foxy does not
 * implement, crashing the game during mixin setup. Real Fabric API classes (Sinytra Connector,
 * Forgified Fabric API) would also clash with an unrelocated shim's packages.
 */
final class FabricApiRelocation {
    private static final String FROM = "net/fabricmc/";
    private static final String TO = "com/leclowndu93150/foxy/fabricmc/";
    private static final String FROM_DOTTED = "net.fabricmc.";
    private static final String TO_DOTTED = "com.leclowndu93150.foxy.fabricmc.";

    private static final Remapper REMAPPER = new Remapper() {
        @Override
        public String map(String internalName) {
            return internalName.startsWith(FROM) ? TO + internalName.substring(FROM.length()) : internalName;
        }

        /** String constants: reflective class names and Mixin target descriptors ({@code @At(target = "Lnet/fabricmc/...")}). */
        @Override
        public Object mapValue(Object value) {
            if (value instanceof String s && (s.contains(FROM) || s.startsWith(FROM_DOTTED))) {
                return s.replace(FROM, TO).replace(FROM_DOTTED, TO_DOTTED);
            }
            return super.mapValue(value);
        }
    };

    private FabricApiRelocation() {}

    static byte[] relocate(byte[] classFile) {
        ClassReader reader = new ClassReader(classFile);
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassRemapper(writer, REMAPPER), 0);
        return writer.toByteArray();
    }
}
