/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.vcl.metadata;

import dev.stolym.vyne.mappings.MappingRegistry;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MetadataClassWriter extends ClassWriter {
    private static final Header MISSING = new Header(null, List.of(), false);
    private static final Map<String, Header> HEADERS = new ConcurrentHashMap<>();

    record Header(String superName, List<String> interfaces, boolean isInterface) {}

    public MetadataClassWriter(int flags) {
        super(flags);
    }

    public MetadataClassWriter(ClassReader classReader, int flags) {
        super(classReader, flags);
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        if (type1.equals(type2)) return type1;
        if (type1.equals("java/lang/Object") || type2.equals("java/lang/Object")) return "java/lang/Object";
        if (isAssignable(type2, type1)) return type1;
        if (isAssignable(type1, type2)) return type2;

        Header h1 = header(type1);
        Header h2 = header(type2);
        if (h1 == MISSING || h1.isInterface() || h2 == MISSING || h2.isInterface()) return "java/lang/Object";

        String current = h1.superName();
        while (current != null && !current.equals("java/lang/Object")) {
            if (isAssignable(type2, current)) return current;
            current = header(current).superName();
        }
        return "java/lang/Object";
    }

    private boolean isAssignable(String source, String target) {
        if (source.equals(target) || target.equals("java/lang/Object")) return true;
        Header h = header(source);
        if (h.superName() != null && isAssignable(h.superName(), target)) return true;
        for (String itf : h.interfaces()) {
            if (isAssignable(itf, target)) return true;
        }
        return false;
    }

    private static final ThreadLocal<java.util.Set<String>> MISSING_TRACE = new ThreadLocal<>();

    public static void beginMissingTrace() {
        MISSING_TRACE.set(new java.util.HashSet<>());
    }

    public static java.util.Set<String> endMissingTrace() {
        java.util.Set<String> trace = MISSING_TRACE.get();
        MISSING_TRACE.remove();
        return trace != null ? trace : java.util.Set.of();
    }

    public static void forgetMissing() {
        HEADERS.values().removeIf(header -> header == MISSING);
    }

    static Header header(String internalName) {
        Header header = HEADERS.computeIfAbsent(internalName, MetadataClassWriter::resolveHeader);
        if (header == MISSING) {
            java.util.Set<String> trace = MISSING_TRACE.get();
            if (trace != null) trace.add(internalName);
        }
        return header;
    }

    private static Header resolveHeader(String internalName) {
        VyneMetadataIndex.ClassInfo info = VyneMetadataIndex.get(internalName);
        if (info != null) {
            List<String> interfaces = new ArrayList<>();
            if (info.interfaces() != null) for (String i : info.interfaces()) interfaces.add(runtimeName(i));
            return new Header(runtimeName(info.superName()), interfaces, info.isInterface());
        }
        byte[] bytes = readClassBytes(internalName);
        if (bytes == null) return MISSING;
        try {
            return parseHeader(bytes);
        } catch (RuntimeException e) {
            return MISSING;
        }
    }

    static Header parseHeader(byte[] b) {
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(b);
        if (buf.getInt() != 0xCAFEBABE) throw new IllegalArgumentException("not a class file");
        buf.getShort();
        buf.getShort();
        int count = buf.getShort() & 0xFFFF;
        String[] utf8 = new String[count];
        int[] classNameIndex = new int[count];
        for (int i = 1; i < count; i++) {
            int tag = buf.get() & 0xFF;
            switch (tag) {
                case 1 -> {
                    byte[] raw = new byte[buf.getShort() & 0xFFFF];
                    buf.get(raw);
                    utf8[i] = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
                }
                case 7 -> classNameIndex[i] = buf.getShort() & 0xFFFF;
                case 8, 16, 19, 20 -> buf.getShort();
                case 15 -> { buf.get(); buf.getShort(); }
                case 3, 4, 9, 10, 11, 12, 17, 18 -> buf.getInt();
                case 5, 6 -> { buf.getLong(); i++; }
                default -> throw new IllegalArgumentException("bad constant pool tag " + tag);
            }
        }
        int access = buf.getShort() & 0xFFFF;
        buf.getShort();
        int superIndex = buf.getShort() & 0xFFFF;
        List<String> interfaces = new ArrayList<>();
        int interfaceCount = buf.getShort() & 0xFFFF;
        for (int i = 0; i < interfaceCount; i++) interfaces.add(utf8[classNameIndex[buf.getShort() & 0xFFFF]]);
        String superName = superIndex == 0 ? null : utf8[classNameIndex[superIndex]];
        return new Header(superName, interfaces, (access & org.objectweb.asm.Opcodes.ACC_INTERFACE) != 0);
    }

    private static String runtimeName(String name) {
        if (name == null || !MappingRegistry.isReady()) return name;
        String mapped = MappingRegistry.get().mapClass(name);
        return mapped != null ? mapped : name;
    }

    private static byte[] readClassBytes(String internalName) {
        String resource = internalName + ".class";
        ClassLoader[] loaders = {
                Thread.currentThread().getContextClassLoader(),
                MetadataClassWriter.class.getClassLoader(),
                ClassLoader.getSystemClassLoader(),
        };
        for (ClassLoader loader : loaders) {
            if (loader == null) continue;
            try (InputStream in = loader.getResourceAsStream(resource)) {
                if (in != null) return in.readAllBytes();
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
