package com.leclowndu93150.foxy.loader;

import net.neoforged.neoforgespi.transformation.ProcessorName;
import net.neoforged.neoforgespi.transformation.SimpleClassProcessor;
import net.neoforged.neoforgespi.transformation.SimpleTransformationContext;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class FoxyAccessWidener extends SimpleClassProcessor {
    private static final int VISIBILITY_MASK = Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_PUBLIC;

    private final AccessWidenerData data = AccessWidenerData.get();

    @Override
    public ProcessorName name() {
        return new ProcessorName("foxy", "access_widener");
    }

    @Override
    public Set<Target> targets() {
        return data.allTargets().stream().map(Target::new).collect(Collectors.toSet());
    }

    @Override
    public void transform(ClassNode node, SimpleTransformationContext context) {
        String className = node.name.replace('/', '.');

        Integer classAccess = data.classes.get(className);
        if (classAccess != null) {
            node.access = widenClass(node.access, classAccess);
            for (var inner : node.innerClasses) {
                if (inner.name.equals(node.name)) {
                    inner.access = widenClass(inner.access, classAccess);
                }
            }
        }

        Map<String, Integer> wideFields = data.fields.get(className);
        if (wideFields != null) {
            for (FieldNode field : node.fields) {
                Integer access = wideFields.get(field.name);
                if (access == null) continue;
                if ((access & AccessWidenerData.ACCESSIBLE) != 0) field.access = toPublic(field.access);
                if ((access & AccessWidenerData.MUTABLE) != 0) field.access &= ~Opcodes.ACC_FINAL;
            }
        }

        Map<String, Integer> wideMethods = data.methods.get(className);
        if (wideMethods != null) {
            for (MethodNode method : node.methods) {
                Integer access = wideMethods.get(method.name + method.desc);
                if (access == null) continue;
                if ((access & AccessWidenerData.ACCESSIBLE) != 0) {
                    method.access = toPublic(method.access);
                }
                if ((access & AccessWidenerData.EXTENDABLE) != 0) {
                    if ((method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) == 0) {
                        method.access = (method.access & ~VISIBILITY_MASK) | Opcodes.ACC_PROTECTED;
                    }
                    method.access &= ~Opcodes.ACC_FINAL;
                }
            }
        }
    }

    private static int widenClass(int access, int flags) {
        int widened = toPublic(access);
        return (flags & AccessWidenerData.EXTENDABLE) != 0 ? widened & ~Opcodes.ACC_FINAL : widened;
    }

    private static int toPublic(int access) {
        return (access & ~VISIBILITY_MASK) | Opcodes.ACC_PUBLIC;
    }
}
