/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.vcl;

import dev.stolym.vyne.core.DetectedMod;
import dev.stolym.vyne.mappings.MappingRegistry;
import dev.stolym.vyne.vcl.metadata.VyneMetadataIndex;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class VyneTransformationGateTest {
    @TempDir
    static Path cacheDir;

    @BeforeAll
    static void setUp() {
        RemapCache.setCacheRoot(cacheDir);
        RemapCache.setMappingsFingerprint("test-fp");
        MappingRegistry.initForTests(new MappingRegistryInit().mappings());
    }

    @AfterAll
    static void tearDown() {
        VyneMetadataIndex.clear();
    }

    private static final class MappingRegistryInit {
        dev.stolym.vyne.mappings.MappingComposer.ComposedMappings mappings() {
            return new dev.stolym.vyne.mappings.MappingComposer.ComposedMappings(
                Map.of("com/example/mojmap/Widget", "com/example/inter/class_9999"),
                Map.of(), Map.of(), Map.of(), Map.of());
        }
    }

    private static byte[] simpleClass(String internalName) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void index(String internalName, DetectedMod.LoaderType type) {
        VyneMetadataIndex.register(internalName.replace('/', '.'),
                "java/lang/Object", new String[0], false, type, null);
    }

    @Test
    void fabricModClassesPassThroughUnchanged() {
        index("com/example/fabric/Native", DetectedMod.LoaderType.FABRIC);
        byte[] original = simpleClass("com/example/fabric/Native");
        assertArrayEquals(original, VyneTransformationGate.transform("com.example.fabric.Native", original));
    }

    @Test
    void vyneShimClassesAreStillTransformed() {
        index("com/example/vyne/Shim", DetectedMod.LoaderType.VYNE);
        byte[] original = simpleClass("com/example/vyne/Shim");
        byte[] out = VyneTransformationGate.transform("com.example.vyne.Shim", original);
        assertNotNull(out);
        assertNotEquals(0, out.length);
    }

    @Test
    void classesOutsideIndexPassThrough() {
        byte[] original = simpleClass("com/example/unknown/Mystery");
        assertArrayEquals(original, VyneTransformationGate.transform("com.example.unknown.Mystery", original));
    }

    @Test
    void internalVyneClassesPassThrough() {
        byte[] original = simpleClass("dev/stolym/vyne/internal/Thing");
        assertArrayEquals(original, VyneTransformationGate.transform("dev.stolym.vyne.internal.Thing", original));
    }

    @Test
    void neoforgeModClassesAreTransformed() {
        index("com/example/neo/Forgey", DetectedMod.LoaderType.NEOFORGE);
        byte[] original = simpleClass("com/example/neo/Forgey");
        byte[] out = VyneTransformationGate.transform("com.example.neo.Forgey", original);
        assertNotNull(out);
        assertNotEquals(0, out.length);
    }

    @Test
    void neoforgeClassReferencesAreRemapped() {
        String mojmapSuper = "com/example/mojmap/Widget";
        index("com/example/neo/UsesWidget", DetectedMod.LoaderType.NEOFORGE);
        index(mojmapSuper, DetectedMod.LoaderType.NEOFORGE);
        VyneMetadataIndex.register("com.example.inter.class_9999", "java/lang/Object", new String[0], false);
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/neo/UsesWidget", null, mojmapSuper, null);
        cw.visitEnd();
        byte[] original = cw.toByteArray();
        byte[] out = VyneTransformationGate.transform("com.example.neo.UsesWidget", original);
        assertNotNull(out);
        org.objectweb.asm.ClassReader outReader = new org.objectweb.asm.ClassReader(out);
        String[] header = new String[2];
        outReader.accept(new org.objectweb.asm.ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                header[0] = name;
                header[1] = superName;
            }
        }, 0);
        assertEquals("com/example/neo/UsesWidget", header[0]);
        assertEquals("com/example/inter/class_9999", header[1],
                "superclass must be remapped to the intermediary name");
    }
}
