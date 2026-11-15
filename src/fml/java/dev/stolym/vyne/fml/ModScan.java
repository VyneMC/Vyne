/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.fml;

import net.neoforged.fml.loading.modscan.ModAnnotation;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ModScan {
    private ModScan() {}

    public static ModFileScanData scan(Path jar) throws IOException {
        ModFileScanData data = new ModFileScanData();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                String name = entry.getName();
                if (entry.isDirectory() || !name.endsWith(".class") || name.startsWith("META-INF/")) continue;
                try (InputStream in = zip.getInputStream(entry)) {
                    new ClassReader(in).accept(new Visitor(data), ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                } catch (RuntimeException ignored) {
                }
            }
        }
        return data;
    }

    private static final class Visitor extends ClassVisitor {
        private final ModFileScanData data;
        private Type type;

        Visitor(ModFileScanData data) {
            super(Opcodes.ASM9);
            this.data = data;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            type = Type.getObjectType(name);
            Set<Type> itfs = new HashSet<>();
            if (interfaces != null) for (String i : interfaces) itfs.add(Type.getObjectType(i));
            data.getClasses().add(new ModFileScanData.ClassData(type, superName == null ? null : Type.getObjectType(superName), itfs));
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            return collect(descriptor, ElementType.TYPE, type.getClassName());
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            return new FieldVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                    return collect(annotation, ElementType.FIELD, name);
                }
            };
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                    return collect(annotation, ElementType.METHOD, name + descriptor);
                }
            };
        }

        private AnnotationVisitor collect(String descriptor, ElementType target, String member) {
            Map<String, Object> values = new LinkedHashMap<>();
            Type annotation = Type.getType(descriptor);
            return new ValueCollector(values, () -> data.getAnnotations()
                    .add(new ModFileScanData.AnnotationData(annotation, target, type, member, values)));
        }
    }

    private static final class ValueCollector extends AnnotationVisitor {
        private final Map<String, Object> values;
        private final Runnable onEnd;

        ValueCollector(Map<String, Object> values, Runnable onEnd) {
            super(Opcodes.ASM9);
            this.values = values;
            this.onEnd = onEnd;
        }

        @Override
        public void visit(String name, Object value) {
            values.put(name, value);
        }

        @Override
        public void visitEnum(String name, String descriptor, String value) {
            values.put(name, new ModAnnotation.EnumHolder(descriptor, value));
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            List<Object> list = new ArrayList<>();
            values.put(name, list);
            return new AnnotationVisitor(Opcodes.ASM9) {
                @Override
                public void visit(String ignored, Object value) {
                    list.add(value);
                }

                @Override
                public void visitEnum(String ignored, String descriptor, String value) {
                    list.add(new ModAnnotation.EnumHolder(descriptor, value));
                }
            };
        }

        @Override
        public void visitEnd() {
            if (onEnd != null) onEnd.run();
        }
    }
}
