/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.registry;

import dev.stolym.vyne.core.DetectedMod;
import dev.stolym.vyne.core.VyneLauncher;
import dev.stolym.vyne.util.VyneLogger;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class ModBlockTableReflect {
    private static final ConcurrentHashMap<String, List<String>> DISCOVERED_BLOCK_TABLE_CLASSES =
        new ConcurrentHashMap<>();

    private static final String[] RELATIVE_BLOCK_TABLE_DIRS = {
        "api/block/",
        "registry/",
        "common/block/",
        "init/",
        "forge/",
        "neoforge/",
    };

    private static final String[] GENERIC_BLOCK_TABLE_FQCN_SUFFIXES = {
        "ModBlocks",
        "Blocks",
        "CommonBlocks",
        "BlockRegistration",
    };

    private ModBlockTableReflect() {}

    public static void preflightDiscoverBlockHolderClasses(ClassLoader cl) {
        if (cl == null) {
            return;
        }
        try {
            DISCOVERED_BLOCK_TABLE_CLASSES.clear();
            Set<String> namespaces = new LinkedHashSet<>();
            for (URL u : collectClasspathUrls(cl)) {
                discoverNamespacesFromJarUrl(u, namespaces);
            }
            for (String ns : namespaces) {
                DISCOVERED_BLOCK_TABLE_CLASSES.put(ns, discoverFqcnListForNamespace(cl, ns));
            }
        } catch (Throwable t) {
            VyneLogger.debug("[ModBlockTableReflect] preflight discover failed: "
                + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    public static Object tryResolveBlockFromModStaticTables(ClassLoader cl, String fullBlockId) {
        if (cl == null || fullBlockId == null || fullBlockId.indexOf(':') < 0) {
            return null;
        }
        int c = fullBlockId.indexOf(':');
        String ns = fullBlockId.substring(0, c);
        String path = fullBlockId.substring(c + 1);
        ensureDiscoveredForNamespace(cl, ns);
        List<String> classes = DISCOVERED_BLOCK_TABLE_CLASSES.getOrDefault(ns, List.of());
        String fullId = fullBlockId;
        for (String field : fieldNameCandidates(path)) {
            for (String fqcn : classes) {
                Object b = readStaticBlockLike(cl, fqcn, field);
                if (b != null) {
                    return b;
                }
            }
        }
        for (String fqcn : classes) {
            Object b = scanStaticBlockFieldsMatchingId(cl, fqcn, fullId);
            if (b != null) {
                return b;
            }
        }
        return null;
    }

    private static Object scanStaticBlockFieldsMatchingId(ClassLoader cl, String fqcn, String fullId) {
        if (cl == null || fqcn == null || fullId == null || fullId.indexOf(':') < 0) {
            return null;
        }
        try {
            Class<?> c = Class.forName(fqcn, true, cl);
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                if (!looksLikeBlock(f.getType(), cl)) {
                    continue;
                }
                f.setAccessible(true);
                Object b = f.get(null);
                if (b == null) {
                    continue;
                }
                String s = String.valueOf(b);
                if (s.contains(fullId)) {
                    return b;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void ensureDiscoveredForNamespace(ClassLoader cl, String ns) {
        if (cl == null || ns == null || ns.isEmpty()) {
            return;
        }
        if (DISCOVERED_BLOCK_TABLE_CLASSES.containsKey(ns)) {
            return;
        }
        DISCOVERED_BLOCK_TABLE_CLASSES.put(ns, discoverFqcnListForNamespace(cl, ns));
    }

    private static List<String> discoverFqcnListForNamespace(ClassLoader cl, String ns) {
        LinkedHashSet<String> fqcn = new LinkedHashSet<>();
        fqcn.addAll(genericFqcnAttempts(ns));
        for (URL u : collectClasspathUrls(cl)) {
            addBlockTableFqcnFromJarUrl(u, ns, fqcn);
        }
        return new ArrayList<>(fqcn);
    }

    private static List<String> genericFqcnAttempts(String ns) {
        List<String> out = new ArrayList<>();
        for (String suffix : GENERIC_BLOCK_TABLE_FQCN_SUFFIXES) {
            out.add(ns + "." + suffix);
        }
        out.add(ns + ".common.block.ModBlocks");
        out.add(ns + ".registry.ModBlocks");
        out.add(ns + ".init.ModBlocks");
        out.add(ns + ".forge.ModBlocks");
        out.add(ns + ".neoforge.ModBlocks");
        out.add(ns + ".api.block.ModBlocks");
        out.add(ns + ".api.block.Blocks");
        return out;
    }

    private static void discoverNamespacesFromJarUrl(URL url, Set<String> namespaces) {
        Path path = urlToExistingPath(url);
        if (path == null || !Files.isRegularFile(path) || !path.toString().endsWith(".jar")) {
            return;
        }
        try (JarFile jf = new JarFile(path.toFile())) {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                String name = e.getName();
                if (!name.endsWith("Blocks.class") || e.isDirectory()) {
                    continue;
                }
                for (String rel : RELATIVE_BLOCK_TABLE_DIRS) {
                    int i = name.indexOf('/' + rel);
                    if (i < 0) {
                        continue;
                    }
                    String ns = name.substring(0, i);
                    if (ns.indexOf('/') >= 0) {
                        continue;
                    }
                    String after = name.substring(i + 1 + rel.length());
                    if (after.indexOf('/') >= 0) {
                        continue;
                    }
                    namespaces.add(ns);
                    break;
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void addBlockTableFqcnFromJarUrl(URL url, String ns, LinkedHashSet<String> out) {
        Path path = urlToExistingPath(url);
        if (path == null || !Files.isRegularFile(path) || !path.toString().endsWith(".jar")) {
            return;
        }
        String nsSlash = ns + "/";
        try (JarFile jf = new JarFile(path.toFile())) {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                String name = e.getName();
                if (!name.startsWith(nsSlash) || !name.endsWith("Blocks.class") || e.isDirectory()) {
                    continue;
                }
                String tail = name.substring(nsSlash.length());
                boolean match = false;
                for (String rel : RELATIVE_BLOCK_TABLE_DIRS) {
                    if (tail.startsWith(rel)) {
                        String after = tail.substring(rel.length());
                        if (after.indexOf('/') < 0) {
                            match = true;
                            break;
                        }
                    }
                }
                if (!match) {
                    continue;
                }
                String fq = name.substring(0, name.length() - 6).replace('/', '.');
                out.add(fq);
            }
        } catch (IOException ignored) {
        }
    }

    private static Path urlToExistingPath(URL url) {
        if (url == null) {
            return null;
        }
        try {
            if ("file".equalsIgnoreCase(url.getProtocol())) {
                Path p = Path.of(url.toURI());
                return Files.exists(p) ? p : null;
            }
            if ("jar".equalsIgnoreCase(url.getProtocol())) {
                URI uri = url.toURI();
                String ssp = uri.getSchemeSpecificPart();
                int bang = ssp.indexOf('!');
                if (bang > 0) {
                    String inner = ssp.substring(0, bang);
                    if (inner.startsWith("file:")) {
                        Path p = Path.of(URI.create(inner));
                        return Files.exists(p) ? p : null;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static List<URL> collectClasspathUrls(ClassLoader cl) {
        LinkedHashSet<URL> seen = new LinkedHashSet<>();
        List<URL> out = new ArrayList<>();
        for (ClassLoader c = cl; c != null; c = c.getParent()) {
            addUrlClassLoaderUrls(c, seen, out);
        }
        addFabricLauncherClasspathUrls(seen, out);
        addVyneDetectedModJarUrls(seen, out);
        addSystemClasspathUrls(seen, out);
        return out;
    }

    private static void addVyneDetectedModJarUrls(LinkedHashSet<URL> seen, List<URL> out) {
        try {
            List<DetectedMod> mods = VyneLauncher.getDetectedMods();
            if (mods == null) {
                return;
            }
            for (DetectedMod m : mods) {
                if (m == null) {
                    continue;
                }
                Path p = m.getJarPath();
                if (p == null || !Files.isRegularFile(p)) {
                    continue;
                }
                String s = p.toString();
                if (!s.endsWith(".jar") && !s.endsWith(".JAR")) {
                    continue;
                }
                addUniqueUrl(p.toUri().toURL(), seen, out);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void addFabricLauncherClasspathUrls(LinkedHashSet<URL> seen, List<URL> out) {
        try {
            Class<?> flb = Class.forName("net.fabricmc.loader.impl.launch.FabricLauncherBase");
            Object launcher = flb.getMethod("getLauncher").invoke(null);
            if (launcher == null) {
                return;
            }
            Object rawCp = launcher.getClass().getMethod("getClassPath").invoke(launcher);
            if (!(rawCp instanceof List<?> paths)) {
                return;
            }
            for (Object o : paths) {
                if (!(o instanceof Path p)) {
                    continue;
                }
                if (!Files.isRegularFile(p)) {
                    continue;
                }
                String s = p.toString();
                if (!s.endsWith(".jar") && !s.endsWith(".JAR")) {
                    continue;
                }
                addUniqueUrl(p.toUri().toURL(), seen, out);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void addUrlClassLoaderUrls(ClassLoader c, LinkedHashSet<URL> seen, List<URL> out) {
        if (c instanceof URLClassLoader ucl) {
            for (URL u : ucl.getURLs()) {
                addUniqueUrl(u, seen, out);
            }
            return;
        }
        for (Class<?> k = c.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (!URLClassLoader.class.isAssignableFrom(f.getType())) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    Object v = f.get(c);
                    if (v instanceof URLClassLoader u2) {
                        for (URL u : u2.getURLs()) {
                            addUniqueUrl(u, seen, out);
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void addSystemClasspathUrls(LinkedHashSet<URL> seen, List<URL> out) {
        String cp = System.getProperty("java.class.path");
        if (cp == null || cp.isEmpty()) {
            return;
        }
        String sep = File.pathSeparator;
        for (String piece : cp.split(sep)) {
            if (piece == null || piece.isEmpty()) {
                continue;
            }
            try {
                Path p = Path.of(piece);
                if (Files.isRegularFile(p) && piece.endsWith(".jar")) {
                    addUniqueUrl(p.toUri().toURL(), seen, out);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static void addUniqueUrl(URL u, LinkedHashSet<URL> seen, List<URL> out) {
        if (u == null) {
            return;
        }
        if (seen.add(u)) {
            out.add(u);
        }
    }

    private static List<String> fieldNameCandidates(String pathLower) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (pathLower == null || pathLower.isEmpty()) {
            return List.of();
        }
        set.add(toScreamingSnake(pathLower));
        String compact = pathLower.replace("_", "");
        if (!compact.equals(pathLower)) {
            set.add(toScreamingSnake(compact));
        }
        set.add(snakeToLowerCamel(pathLower));
        return new ArrayList<>(set);
    }

    private static String toScreamingSnake(String s) {
        return s.toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static String snakeToLowerCamel(String snake) {
        String[] p = snake.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < p.length; i++) {
            if (p[i].isEmpty()) {
                continue;
            }
            if (i == 0) {
                sb.append(p[i]);
            } else {
                sb.append(Character.toUpperCase(p[i].charAt(0)));
                if (p[i].length() > 1) {
                    sb.append(p[i].substring(1));
                }
            }
        }
        return sb.toString();
    }

    private static Object readStaticBlockLike(ClassLoader cl, String className, String fieldName) {
        Object v = readStaticAny(cl, className, fieldName);
        if (v == null) {
            return null;
        }
        if (looksLikeBlock(v.getClass(), cl)) {
            return v;
        }
        return null;
    }

    private static Object readStaticAny(ClassLoader cl, String className, String fieldName) {
        try {
            Class<?> c = Class.forName(className, true, cl);
            Field f = c.getField(fieldName);
            if (!Modifier.isStatic(f.getModifiers())) {
                return null;
            }
            f.setAccessible(true);
            return f.get(null);
        } catch (Throwable t) {
            try {
                Class<?> c = Class.forName(className, true, cl);
                Field f = c.getDeclaredField(fieldName);
                if (!Modifier.isStatic(f.getModifiers())) {
                    return null;
                }
                f.setAccessible(true);
                return f.get(null);
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    private static boolean looksLikeBlock(Class<?> t, ClassLoader cl) {
        if (t == null) {
            return false;
        }
        int guard = 0;
        for (Class<?> k = t; k != null && k != Object.class && guard++ < 32; k = k.getSuperclass()) {
            String n = k.getName();
            if (n.endsWith("Block") || n.contains("class_2248")) {
                return true;
            }
        }
        try {
            Class<?> block = Class.forName("net.minecraft.class_2248", false, cl);
            return block.isAssignableFrom(t);
        } catch (Throwable ignored) {
        }
        return false;
    }
}
