/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.vcl.metadata;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetadataClassWriterTest {
    private static String common(String a, String b) {
        return new MetadataClassWriter(ClassWriter.COMPUTE_FRAMES) {
            String expose(String x, String y) {
                return getCommonSuperClass(x, y);
            }
        }.expose(a, b);
    }

    @Test
    void jdkExceptionsMergeToTheirRealCommonSupertype() {
        assertEquals("java/lang/ReflectiveOperationException",
                common("java/lang/ClassNotFoundException", "java/lang/NoSuchMethodException"));
        assertEquals("java/lang/RuntimeException",
                common("java/lang/IllegalStateException", "java/lang/IllegalArgumentException"));
    }

    @Test
    void subtypeMergesToSupertype() {
        assertEquals("java/lang/Exception", common("java/io/IOException", "java/lang/Exception"));
    }

    @Test
    void indexedModClassesUseTheIndex() {
        VyneMetadataIndex.register("test/ModBlockA", "test/ModBlockBase", new String[0], false);
        VyneMetadataIndex.register("test/ModBlockB", "test/ModBlockBase", new String[0], false);
        VyneMetadataIndex.register("test/ModBlockBase", "java/lang/Object", new String[0], false);
        assertEquals("test/ModBlockBase", common("test/ModBlockA", "test/ModBlockB"));
    }
}
