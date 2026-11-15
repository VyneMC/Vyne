/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.convert;

import dev.stolym.vyne.core.DetectedMod;
import dev.stolym.vyne.mappings.MappingComposer;
import dev.stolym.vyne.mappings.MappingRegistry;
import dev.stolym.vyne.vcl.RemapCache;
import dev.stolym.vyne.vcl.VyneTransformationGate;
import dev.stolym.vyne.vcl.metadata.VyneMetadataIndex;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModConverterTest {
    private static final String MOJMAP_WIDGET = "com/example/mojmap/Widget";
    private static final String INTER_WIDGET = "com/example/inter/class_9999";

    @TempDir
    Path tmp;

    @BeforeEach
    void setUp() {
        ModConverter.reset();
        ModConverter.setCacheBase(tmp.resolve("converted"));
        RemapCache.setCacheRoot(tmp.resolve("remap"));
        MappingRegistry.initForTests(new MappingComposer.ComposedMappings(
                Map.of(MOJMAP_WIDGET, INTER_WIDGET), Map.of(), Map.of(), Map.of(), Map.of()));
        VyneMetadataIndex.register(INTER_WIDGET, "java/lang/Object", new String[0], false);
    }

    @AfterEach
    void tearDown() {
        ModConverter.reset();
        ModConverter.setCacheBase(null);
        VyneMetadataIndex.clear();
    }

    private static byte[] subclassOf(String internalName, String superName) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, superName, null);
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] mergesUnknownTypes(String internalName) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "pick", "(Z)Ljava/lang/Object;", null, null);
        Label other = new Label();
        Label end = new Label();
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ILOAD, 0);
        mv.visitJumpInsn(Opcodes.IFEQ, other);
        mv.visitTypeInsn(Opcodes.NEW, "absent/mod/Alpha");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "absent/mod/Alpha", "<init>", "()V", false);
        mv.visitJumpInsn(Opcodes.GOTO, end);
        mv.visitLabel(other);
        mv.visitTypeInsn(Opcodes.NEW, "absent/mod/Beta");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "absent/mod/Beta", "<init>", "()V", false);
        mv.visitLabel(end);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(2, 1);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static Path writeJar(Path jar, Map<String, byte[]> entries) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
        return jar;
    }

    private static byte[] read(Path jar, String name) throws IOException {
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            ZipEntry entry = zf.getEntry(name);
            if (entry == null) return null;
            try (InputStream in = zf.getInputStream(entry)) {
                return in.readAllBytes();
            }
        }
    }

    private static String superOf(byte[] classBytes) {
        String[] out = new String[1];
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                out[0] = superName;
            }
        }, ClassReader.SKIP_CODE);
        return out[0];
    }

    private Path sampleMod(String fileName) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8));
        entries.put("META-INF/SIGNER.SF", "signature".getBytes(StandardCharsets.UTF_8));
        entries.put("META-INF/neoforge.mods.toml", "modLoader=\"javafml\"\n".getBytes(StandardCharsets.UTF_8));
        entries.put("assets/sample/lang/en_us.json", "{}".getBytes(StandardCharsets.UTF_8));
        entries.put("com/example/neo/UsesWidget.class", subclassOf("com/example/neo/UsesWidget", MOJMAP_WIDGET));
        entries.put("com/example/neo/Risky.class", mergesUnknownTypes("com/example/neo/Risky"));
        return writeJar(tmp.resolve(fileName), entries);
    }

    private ModConverter.Summary convert(Path jar) {
        return ModConverter.convertAll(List.of(new ModConverter.Request(jar, "sample")), List.of(), "test");
    }

    @Test
    void convertsClassesOnceAndKeepsResources() throws IOException {
        Path mod = sampleMod("sample.jar");

        ModConverter.Summary first = convert(mod);
        assertEquals(1, first.converted());
        assertEquals(0, first.reused());

        Path runtime = ModConverter.runtimeJar(mod);
        assertNotEquals(mod, runtime);
        assertEquals(INTER_WIDGET, superOf(read(runtime, "com/example/neo/UsesWidget.class")));
        assertArrayEquals("{}".getBytes(StandardCharsets.UTF_8), read(runtime, "assets/sample/lang/en_us.json"));
        assertNotNull(read(runtime, "META-INF/neoforge.mods.toml"));
        assertNull(read(runtime, "META-INF/SIGNER.SF"));

        ModConverter.reset();
        ModConverter.Summary second = convert(mod);
        assertEquals(0, second.converted());
        assertEquals(1, second.reused());
        assertEquals(runtime, ModConverter.runtimeJar(mod));
    }

    @Test
    void classesWithUnresolvedHierarchyAreLeftForTheGate() throws IOException {
        Path mod = sampleMod("sample.jar");
        byte[] original = read(mod, "com/example/neo/Risky.class");

        ModConverter.Summary summary = convert(mod);
        assertEquals(1, summary.deferred());

        Path runtime = ModConverter.runtimeJar(mod);
        assertArrayEquals(original, read(runtime, "com/example/neo/Risky.class"));
        String source = mod.toAbsolutePath().toString();
        assertFalse(ModConverter.isPreConverted(source, "com/example/neo/Risky"));
        assertTrue(ModConverter.isPreConverted(source, "com/example/neo/UsesWidget"));

        ModConverter.reset();
        convert(mod);
        assertFalse(ModConverter.isPreConverted(source, "com/example/neo/Risky"));
    }

    @Test
    void changedJarIsConvertedAgainAndStaleOutputRemoved() throws IOException {
        Path mod = sampleMod("sample.jar");
        convert(mod);
        Path firstRuntime = ModConverter.runtimeJar(mod);

        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("com/example/neo/Other.class", subclassOf("com/example/neo/Other", MOJMAP_WIDGET));
        writeJar(mod, entries);
        Files.setLastModifiedTime(mod, FileTime.fromMillis(System.currentTimeMillis() + 5000));

        ModConverter.reset();
        ModConverter.Summary summary = convert(mod);
        assertEquals(1, summary.converted());
        Path secondRuntime = ModConverter.runtimeJar(mod);
        assertNotEquals(firstRuntime, secondRuntime);
        assertFalse(Files.exists(firstRuntime));
        assertEquals(INTER_WIDGET, superOf(read(secondRuntime, "com/example/neo/Other.class")));
    }

    @Test
    void gateReturnsPreConvertedClassesUntouched() throws IOException {
        Path mod = sampleMod("sample.jar");
        convert(mod);
        String source = mod.toAbsolutePath().toString();
        VyneMetadataIndex.register("com.example.neo.UsesWidget", MOJMAP_WIDGET, new String[0], false,
                DetectedMod.LoaderType.NEOFORGE, source);

        byte[] converted = read(ModConverter.runtimeJar(mod), "com/example/neo/UsesWidget.class");
        assertArrayEquals(converted, VyneTransformationGate.transform("com.example.neo.UsesWidget", converted));
    }

    @Test
    void disabledFlagKeepsOriginalJar() throws IOException {
        Path mod = sampleMod("sample.jar");
        System.setProperty("vyne.convert", "false");
        try {
            ModConverter.Summary summary = convert(mod);
            assertEquals(0, summary.converted());
            assertEquals(mod, ModConverter.runtimeJar(mod));
        } finally {
            System.clearProperty("vyne.convert");
        }
    }
}
