/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.vcl;

import dev.stolym.vyne.core.DetectedMod;
import dev.stolym.vyne.vcl.metadata.VyneMetadataIndex;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.assertSame;

class VyneTransformationGateTest {
    @AfterAll
    static void tearDown() {
        VyneMetadataIndex.clear();
    }

    private static byte[] simpleClass(String internalName) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void index(String internalName, DetectedMod.LoaderType type) {
        VyneMetadataIndex.register(internalName.replace('/', '.'), "java/lang/Object", new String[0], false, type, null);
    }

    @Test
    void fabricModClassesPassThroughUnchanged() {
        index("com/example/fabric/Native", DetectedMod.LoaderType.FABRIC);
        byte[] original = simpleClass("com/example/fabric/Native");
        assertSame(original, VyneTransformationGate.transform("com.example.fabric.Native", original));
    }

    @Test
    void neoForgeModClassesArriveAlreadyRemappedAndPassThrough() {
        index("com/example/neo/Mod", DetectedMod.LoaderType.NEOFORGE);
        byte[] original = simpleClass("com/example/neo/Mod");
        assertSame(original, VyneTransformationGate.transform("com.example.neo.Mod", original));
    }

    @Test
    void classesOutsideTheIndexPassThrough() {
        byte[] original = simpleClass("com/example/unknown/Mystery");
        assertSame(original, VyneTransformationGate.transform("com.example.unknown.Mystery", original));
    }

    @Test
    void vyneAndMinecraftClassesPassThrough() {
        byte[] vyne = simpleClass("dev/stolym/vyne/internal/Thing");
        assertSame(vyne, VyneTransformationGate.transform("dev.stolym.vyne.internal.Thing", vyne));
        byte[] minecraft = simpleClass("net/minecraft/class_2378");
        assertSame(minecraft, VyneTransformationGate.transform("net.minecraft.class_2378", minecraft));
    }
}
