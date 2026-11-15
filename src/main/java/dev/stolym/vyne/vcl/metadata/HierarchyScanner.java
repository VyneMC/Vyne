/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.vcl.metadata;

import dev.stolym.vyne.core.DetectedMod;
import dev.stolym.vyne.util.VyneLogger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class HierarchyScanner {
    public static void scan(Path path) {
        scan(path, null);
    }

    public static void scan(Path path, DetectedMod.LoaderType loaderType) {
        if (!Files.exists(path)) return;

        if (Files.isDirectory(path)) {
            scanDirectory(path, loaderType, null);
        } else if (path.toString().endsWith(".jar") || path.toString().endsWith(".zip")) {
            scanJar(path, loaderType);
        }
    }

    private static void scanDirectory(Path root, DetectedMod.LoaderType loaderType, String sourceJar) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.toString().endsWith(".class")) {
                        try (InputStream is = Files.newInputStream(file)) {
                            processClass(is, loaderType, sourceJar);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            VyneLogger.warn("[HierarchyScanner] Failed to scan directory " + root + ": " + e.getMessage());
        }
    }

    private static void scanJar(Path jarPath, DetectedMod.LoaderType loaderType) {
        String sourceJar = jarPath.toAbsolutePath().toString();
        try (ZipFile zf = new ZipFile(jarPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    try (InputStream is = zf.getInputStream(entry)) {
                        processClass(is, loaderType, sourceJar);
                    }
                }
            }
        } catch (IOException e) {
            VyneLogger.warn("[HierarchyScanner] Failed to scan JAR " + jarPath + ": " + e.getMessage());
        }
    }

    private static void processClass(InputStream is, DetectedMod.LoaderType loaderType, String sourceJar) throws IOException {
        ClassReader cr = new ClassReader(is);
        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                boolean isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
                VyneMetadataIndex.register(name, superName, interfaces, isInterface, loaderType, sourceJar);
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }
}
