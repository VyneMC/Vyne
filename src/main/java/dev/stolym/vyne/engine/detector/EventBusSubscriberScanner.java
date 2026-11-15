/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.engine.detector;

import dev.stolym.vyne.util.VyneLogger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class EventBusSubscriberScanner {
    private EventBusSubscriberScanner() {}

    public static int scanAndRegister(String modId, Path jarPath, ClassLoader modLoader,
                                      Object modBus, boolean isClient) {
        System.out.println("[VYNE-SCAN] begin modId=" + modId + " jar=" + (jarPath != null ? jarPath.getFileName() : null)
                + " busClass=" + (modBus != null ? modBus.getClass().getName() : "null"));

        if (modId == null || jarPath == null || modLoader == null || modBus == null) {
            System.out.println("[VYNE-SCAN] skip: null argument (modId=" + modId
                    + ", jar=" + jarPath + ", loader=" + modLoader + ", bus=" + modBus + ")");
            return 0;
        }

        Method registerMethod = resolveRegisterMethod(modBus);
        if (registerMethod == null) {
            VyneLogger.warn("[EventBusScanner] " + modId + ": mod bus " + modBus.getClass().getName()
                    + " has no register(Class|Object) method — scan skipped.");
            System.out.println("[VYNE-SCAN] " + modId + ": no register(..) method on bus — abort.");
            return 0;
        }

        List<String> candidates;
        try {
            candidates = collectAnnotatedClassNames(jarPath);
        } catch (Throwable t) {
            VyneLogger.warn("[EventBusScanner] " + modId + ": jar scan failed (" +
                    t.getClass().getSimpleName() + "): " + t.getMessage());
            System.out.println("[VYNE-SCAN] " + modId + ": jar scan threw "
                    + t.getClass().getSimpleName() + " (" + t.getMessage() + ")");
            return 0;
        }
        System.out.println("[VYNE-SCAN] " + modId + ": found " + candidates.size()
                + " @EventBusSubscriber candidate class(es) in jar.");
        if (candidates.isEmpty()) return 0;

        String currentDist = isClient ? "CLIENT" : "DEDICATED_SERVER";
        int registered = 0;
        int registeredOnModBus = 0;
        int registeredOnGameBus = 0;
        int filteredBySide = 0;
        int filteredByModid = 0;
        int failedLoad = 0;

        Object gameBus = resolveGlobalGameBus(modLoader, modBus);
        Method gameRegister = (gameBus == modBus) ? registerMethod : resolveRegisterMethod(gameBus);

        for (ClassCandidate cand : parseCandidates(candidates, jarPath)) {
            if (cand.modid != null && !cand.modid.isEmpty() && !cand.modid.equals(modId)) {
                filteredByModid++;
                continue;
            }
            if (!cand.dists.isEmpty() && !cand.dists.contains(currentDist)) {
                filteredBySide++;
                continue;
            }

            Class<?> cls;
            try {
                cls = Class.forName(cand.className, true, modLoader);
            } catch (Throwable t) {
                failedLoad++;
                continue;
            }

            boolean forgeBus = cand.bus == null || !cand.bus.equalsIgnoreCase("MOD");
            Object targetBus = forgeBus ? gameBus : modBus;
            Method targetRegister = forgeBus ? gameRegister : registerMethod;
            if (targetBus == null || targetRegister == null) {
                targetBus = modBus;
                targetRegister = registerMethod;
                forgeBus = false;
            }

            try {
                targetRegister.invoke(targetBus, cls);
                registered++;
                if (forgeBus) registeredOnGameBus++;
                else          registeredOnModBus++;
            } catch (Throwable t) {
                VyneLogger.warn("[EventBusScanner] " + modId + ": register failed for "
                        + cand.className + " (" + t.getClass().getSimpleName() + ")");
            }
        }

        VyneLogger.info("[EventBusScanner] " + modId + ": registered " + registered
                + " @EventBusSubscriber class(es) ("
                + registeredOnModBus + " mod-bus, " + registeredOnGameBus + " game-bus)"
                + (filteredBySide > 0 ? ", " + filteredBySide + " filtered by @Dist" : "")
                + (filteredByModid > 0 ? ", " + filteredByModid + " filtered by modid" : "")
                + (failedLoad > 0 ? ", " + failedLoad + " failed to load" : ""));
        System.out.println("[VYNE-SCAN] " + modId + ": done. registered=" + registered
                + " (modBus=" + registeredOnModBus + ", gameBus=" + registeredOnGameBus
                + "), filteredDist=" + filteredBySide + ", filteredModid=" + filteredByModid
                + ", failedLoad=" + failedLoad);

        if (!candidates.isEmpty()
                && registeredOnModBus == 0 && registeredOnGameBus == 0
                && failedLoad < candidates.size()) {
            VyneLogger.warn("[EventBusScanner] " + modId + ": found " + candidates.size()
                    + " @EventBusSubscriber class(es) but NONE produced handlers on either bus. "
                    + "This almost always means a @SubscribeEvent (or equivalent) annotation "
                    + "class failed to resolve at class-load time — check the per-class "
                    + "[Vyne-Bus] lines above for the missing shim.");
        }
        return registered;
    }

    private static Object resolveGlobalGameBus(ClassLoader modLoader, Object modBus) {
        String[] candidates = {
                "net.neoforged.bus.api.VyneEventBus",
                "net.minecraftforge.common.MinecraftForge",
                "net.neoforged.neoforge.common.NeoForge"
        };
        for (String name : candidates) {
            try {
                Class<?> cls = Class.forName(name, true, modLoader);
                if (name.endsWith("VyneEventBus")) {
                    return cls.getMethod("global").invoke(null);
                }
                try {
                    java.lang.reflect.Field f = cls.getField("EVENT_BUS");
                    Object v = f.get(null);
                    if (v != null) return v;
                } catch (NoSuchFieldException ignored) {}
            } catch (Throwable ignored) {}
        }
        return modBus;
    }

    private static List<String> collectAnnotatedClassNames(Path jarPath) throws Exception {
        List<String> out = new ArrayList<>();
        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String name = e.getName();
                if (!name.endsWith(".class")) continue;
                if (name.startsWith("META-INF/")) continue;
                try (InputStream is = zip.getInputStream(e)) {
                    ClassReader cr = new ClassReader(is);
                    ClassNode cn = new ClassNode();
                    cr.accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    if (cn.visibleAnnotations == null) continue;
                    for (AnnotationNode ann : cn.visibleAnnotations) {
                        if (ann.desc != null && ann.desc.endsWith("EventBusSubscriber;")) {
                            out.add(cn.name.replace('/', '.'));
                            break;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return out;
    }

    private static List<ClassCandidate> parseCandidates(List<String> classNames, Path jarPath) {
        List<ClassCandidate> out = new ArrayList<>(classNames.size());
        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            for (String className : classNames) {
                String entryName = className.replace('.', '/') + ".class";
                ZipEntry entry = zip.getEntry(entryName);
                if (entry == null) continue;
                try (InputStream is = zip.getInputStream(entry)) {
                    ClassReader cr = new ClassReader(is);
                    ClassNode cn = new ClassNode();
                    cr.accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    ClassCandidate cand = new ClassCandidate(className);
                    if (cn.visibleAnnotations != null) {
                        for (AnnotationNode ann : cn.visibleAnnotations) {
                            if (ann.desc == null || !ann.desc.endsWith("EventBusSubscriber;")) continue;
                            readAnnotationAttributes(ann, cand);
                            break;
                        }
                    }
                    out.add(cand);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void readAnnotationAttributes(AnnotationNode ann, ClassCandidate cand) {
        if (ann.values == null) return;
        for (int i = 0; i < ann.values.size() - 1; i += 2) {
            String key = (String) ann.values.get(i);
            Object val = ann.values.get(i + 1);
            switch (key) {
                case "modid" -> cand.modid = val == null ? null : val.toString();
                case "value" -> {
                    if (val instanceof List<?> list) {
                        for (Object o : list) {
                            if (o instanceof String[] pair && pair.length == 2) cand.dists.add(pair[1]);
                        }
                    } else if (val instanceof String[] pair && pair.length == 2) {
                        cand.dists.add(pair[1]);
                    }
                }
                case "bus" -> {
                    if (val instanceof String[] pair && pair.length == 2) cand.bus = pair[1];
                }
                default -> {  }
            }
        }
    }

    private static Method resolveRegisterMethod(Object modBus) {
        for (Method m : modBus.getClass().getMethods()) {
            if (!"register".equals(m.getName())) continue;
            if (m.getParameterCount() != 1) continue;
            Class<?> p = m.getParameterTypes()[0];
            if (p == Object.class || p == Class.class) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    private static final class ClassCandidate {
        final String className;
        String modid;
        String bus;
        final List<String> dists = new ArrayList<>(2);

        ClassCandidate(String className) { this.className = className; }
    }
}
