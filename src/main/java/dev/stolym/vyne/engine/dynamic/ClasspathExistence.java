/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.engine.dynamic;

import java.util.HashSet;
import java.util.Set;

public final class ClasspathExistence {
    private final ClassLoader probe;

    private static final String[] ALWAYS_EXISTS = {
        "java/", "javax/", "sun/", "jdk/", "com/sun/",
        "org/jetbrains/annotations/",
        "org/xml/sax/", "org/w3c/dom/",
        "com/mojang/",
        "com/google/gson/", "com/google/common/",
        "org/apache/commons/", "org/apache/logging/",
        "org/slf4j/", "io/netty/",
        "it/unimi/dsi/fastutil/",
        "com/ibm/icu/",
        "com/electronwill/nightconfig/",
        "net/jodah/typetools/",
        "org/lwjgl/",
        "com/llamalad7/mixinextras/",
        "org/spongepowered/asm/",
        "net/fabricmc/",
        "net/minecraft/", 
        "net/minecraftforge/vyne/",
        "dev/stolym/vyne/",
    };

    private final Set<String> negativeCache = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<String> positiveCache = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public ClasspathExistence(ClassLoader probe) {
        this.probe = probe != null ? probe : ClassLoader.getSystemClassLoader();
    }

    public ClassLoader probeLoader() { return probe; }

    public void markProvided(Iterable<String> internalNames) {
        if (internalNames == null) return;
        for (String n : internalNames) positiveCache.add(n);
    }

    public boolean exists(String internalName) {
        if (internalName == null || internalName.isEmpty()) return true;
        if (positiveCache.contains(internalName)) return true;
        if (negativeCache.contains(internalName)) return false;

        for (String p : ALWAYS_EXISTS) {
            if (internalName.startsWith(p)) {
                positiveCache.add(internalName);
                return true;
            }
        }

        String dotted = internalName.replace('/', '.');
        try {
            Class.forName(dotted, false, probe);
            positiveCache.add(internalName);
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            negativeCache.add(internalName);
            return false;
        } catch (Throwable unexpected) {
            positiveCache.add(internalName);
            return true;
        }
    }

    public Set<String> filterMissing(Iterable<String> candidates) {
        Set<String> out = new HashSet<>();
        for (String c : candidates) {
            if (!exists(c)) out.add(c);
        }
        return out;
    }
}
