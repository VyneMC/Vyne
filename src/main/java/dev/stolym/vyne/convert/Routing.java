/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Routing {
    public static final String RESOURCE = "/vyne/routing/neoforge-21.1.txt";

    private static volatile Map<String, String> classes;

    private Routing() {}

    public static Map<String, String> classes() {
        Map<String, String> current = classes;
        if (current == null) {
            Map<String, String> loaded = new LinkedHashMap<>();
            try (InputStream in = Routing.class.getResourceAsStream(RESOURCE)) {
                if (in != null) {
                    for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                        String[] parts = trimmed.split("\\s+");
                        if (parts.length == 2) loaded.put(parts[0], parts[1]);
                    }
                }
            } catch (IOException ignored) {
            }
            current = Collections.unmodifiableMap(loaded);
            classes = current;
        }
        return current;
    }

    public static String fingerprint() {
        return Integer.toHexString(classes().hashCode());
    }

    public static boolean isRouted(String internalName) {
        return classes().containsKey(internalName);
    }

    public static byte[] apply(byte[] classBytes) {
        if (classes().isEmpty()) return classBytes;
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new ClassRemapper(writer, new SimpleRemapper(classes())), 0);
        return writer.toByteArray();
    }
}
