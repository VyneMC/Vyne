/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.bus.api;

import dev.stolym.vyne.util.VyneLogLevel;
import dev.stolym.vyne.util.VyneLogger;
import dev.stolym.vyne.util.ReflectionHelper;
import dev.stolym.vyne.mappings.MappingRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class VyneEventBus implements net.neoforged.bus.api.IEventBus, net.neoforged.neoforge.eventbus.api.IEventBus, net.minecraftforge.eventbus.api.IEventBus {
    private static final VyneEventBus INSTANCE = new VyneEventBus();
    public static VyneEventBus global() { return INSTANCE; }

    private final List<Consumer<Object>> listeners = new ArrayList<>();
    private final List<Object> registryKeys = java.util.Collections.synchronizedList(new ArrayList<>());

    private static final java.util.Set<String> LOGGED_HANDLER_FAILURES =
        java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    static boolean shouldLogHandlerFailure(String handlerKey, Throwable cause) {
        String sig = cause == null
            ? "null"
            : cause.getClass().getName() + ":" + String.valueOf(cause.getMessage());
        return LOGGED_HANDLER_FAILURES.add(handlerKey + "::" + sig);
    }

    private static void invokeSubscribeHandler(java.lang.reflect.Method method, Object instance,
            Object event, String keyF, String declaringSimpleName) {
        try {
            method.invoke(instance, event);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ClassCircularityError) {
                retrySubscribeAfterClassCircularity(method, instance, event, keyF, declaringSimpleName);
                return;
            }
            if (shouldLogHandlerFailure(keyF, cause)) {
                VyneLogger.warn("[Vyne-Bus] @SubscribeEvent " + method.getName()
                        + " in " + declaringSimpleName
                        + " threw: " + cause
                        + "  (suppressing further identical failures for this handler)");
            }
            if (event.getClass().getName().contains("RegisterEvent")) {
                throw new RuntimeException("Critical registration failure in @SubscribeEvent " + method.getName(), cause);
            }
        } catch (Throwable e) {
            if (shouldLogHandlerFailure(keyF, e)) {
                VyneLogger.warn("[Vyne-Bus] @SubscribeEvent " + method.getName()
                        + " in " + declaringSimpleName
                        + " error: " + e
                        + "  (suppressing further identical failures for this handler)");
            }
        }
    }

    private static void retrySubscribeAfterClassCircularity(java.lang.reflect.Method method, Object instance,
            Object event, String keyF, String declaringSimpleName) {
        int extraAttempts = Math.max(0, parseBusIntProp("vyne.bus.classCircularityRetries", 6));
        long waitNanos = 1_000_000L;
        Throwable lastCirc = null;
        for (int i = 0; i < extraAttempts; i++) {
            try {
                Thread.sleep(0, (int) Math.min(waitNanos, 25_000_000L));
                waitNanos = Math.min(waitNanos * 2, 25_000_000L);
                method.invoke(instance, event);
                return;
            } catch (java.lang.reflect.InvocationTargetException e2) {
                Throwable c2 = e2.getCause();
                if (c2 instanceof ClassCircularityError) {
                    lastCirc = c2;
                    continue;
                }
                if (shouldLogHandlerFailure(keyF, c2)) {
                    VyneLogger.warn("[Vyne-Bus] @SubscribeEvent " + method.getName()
                            + " in " + declaringSimpleName
                            + " threw: " + c2
                            + "  (suppressing further identical failures for this handler)");
                }
                if (event.getClass().getName().contains("RegisterEvent")) {
                    throw new RuntimeException("Critical registration failure (retry) in @SubscribeEvent " + method.getName(), c2);
                }
                return;
            } catch (Throwable t) {
                if (shouldLogHandlerFailure(keyF, t)) {
                    VyneLogger.warn("[Vyne-Bus] @SubscribeEvent " + method.getName()
                            + " in " + declaringSimpleName
                            + " error: " + t
                            + "  (suppressing further identical failures for this handler)");
                }
                return;
            }
        }
        if (lastCirc != null && shouldLogHandlerFailure(keyF, lastCirc)) {
            VyneLogger.warn("[Vyne-Bus] @SubscribeEvent " + method.getName()
                    + " in " + declaringSimpleName
                    + " threw: " + lastCirc
                    + "  (after " + extraAttempts + " ClassCircularityError retries)"
                    + "  (suppressing further identical failures for this handler)");
        }
    }

    private static int parseBusIntProp(String key, int def) {
        try {
            String s = System.getProperty(key);
            if (s == null || s.isBlank()) {
                return def;
            }
            return Integer.parseInt(s.trim(), 10);
        } catch (Throwable t) {
            return def;
        }
    }

    public void trackRegistryKey(Object key) {
        if (key != null) registryKeys.add(key);
    }

    @Override
    public void register(Object target) {
        if (VyneLogLevel.isVerbose()) VyneLogger.debug("[Vyne-Bus] register called for: " + target);
        if (target == null) {
            VyneLogger.warn("[Vyne-Bus] register called with null target");
            return;
        }
        Class<?> cls = (target instanceof Class<?>) ? (Class<?>) target : target.getClass();
        Object instance = (target instanceof Class<?>) ? null : target;

        System.out.println("[Vyne-Bus] Subscribing listener: " + cls.getName()
            + " (instance=" + (instance != null) + ")");
        VyneLogger.info("[Vyne-Bus] Subscribing listener: " + cls.getName() + " (instance=" + (instance != null) + ")");

        int registered = 0;
        int[] skippedMissingRef = new int[]{0};
        int failedInspect = 0;
        int[] annotationStrippedRef = new int[]{0};

        int methodResolutionFailure = 0;
        java.util.List<String> skippedMissingNames = new java.util.ArrayList<>();
        java.util.List<String> failedInspectNames = new java.util.ArrayList<>();
        java.util.List<String> annotationStrippedNames = new java.util.ArrayList<>();
        java.util.List<String> methodResolutionFailureDetails = new java.util.ArrayList<>();
        java.util.List<String> registeredDetails = new java.util.ArrayList<>();

        int totalBytecodeSubscribeCount = 0;
        try {
            java.util.List<AsmMethodEntry> pre = readSubscribeEventMethodsViaAsm(cls);
            if (pre != null) totalBytecodeSubscribeCount = pre.size();
        } catch (Throwable ignored) {}

        Class<?> current = cls;
        while (current != null && current != Object.class) {
            try {
                java.util.List<AsmMethodEntry> asmMethods;
                try {
                    asmMethods = readSubscribeEventMethodsViaAsm(current);
                } catch (Throwable t) {
                    failedInspect++;
                    failedInspectNames.add(current.getSimpleName() + " (ASM read failed: "
                            + t.getClass().getSimpleName() + ")");
                    asmMethods = null;
                }

                if (asmMethods == null || asmMethods.isEmpty()) {
                    if (asmMethods != null && VyneLogLevel.isVerbose()) {
                        VyneLogger.debug("[Vyne-Bus] ASM saw 0 @SubscribeEvent methods on "
                                + current.getName() + " — trying reflection fallback.");
                    }
                    try {
                        registered += reflectiveRegisterFallback(current, instance, cls,
                                skippedMissingNames, skippedMissingRef);
                    } catch (Throwable t) {
                        failedInspect++;
                        failedInspectNames.add(current.getSimpleName() + " (reflective fallback failed: "
                                + t.getClass().getSimpleName() + ")");
                    }
                } else {
                    for (AsmMethodEntry am : asmMethods) {
                        try {
                            Class<?> eventType = null;
                            Throwable resolveBacktrack = null;

                            try {
                                eventType = Class.forName(am.paramClassName, false, current.getClassLoader());
                            } catch (Throwable t) {
                                resolveBacktrack = t;
                            }

                            if (eventType == null
                                    && !am.paramClassName.equals(am.rawParamClassName)) {
                                try {
                                    eventType = Class.forName(am.rawParamClassName, false, current.getClassLoader());
                                } catch (Throwable ignored) {
                                }
                            }
                            if (eventType == null) {
                                skippedMissingRef[0]++;
                                String shortName = am.paramClassName.substring(
                                        am.paramClassName.lastIndexOf('.') + 1);
                                skippedMissingNames.add(current.getSimpleName() + "#" + am.methodName
                                        + "(" + shortName + ")");
                                String causeDetail;
                                if (resolveBacktrack == null) {
                                    causeDetail = "null";
                                } else {
                                    String m = resolveBacktrack.getMessage();
                                    causeDetail = resolveBacktrack.getClass().getSimpleName()
                                            + (m == null || m.isEmpty() ? "" : ": " + m);
                                }
                                String warnMsg = "[Vyne-Bus] " + current.getSimpleName()
                                        + "#" + am.methodName + ": event type unresolved"
                                        + " (raw=" + am.rawParamClassName
                                        + ", remapped=" + am.paramClassName
                                        + ", loader=" + safeLoaderName(current.getClassLoader())
                                        + ", cause=" + causeDetail
                                        + ") — Vyne needs a shim for this event class.";
                                try { System.out.println(warnMsg); } catch (Throwable ignored) {}
                                VyneLogger.warn(warnMsg);
                                continue;
                            }
                            java.lang.reflect.Method method = null;
                            Throwable getDeclaredThrowable = null;
                            try {
                                method = current.getDeclaredMethod(am.methodName, eventType);
                            } catch (Throwable t) {
                                getDeclaredThrowable = t;
                            }
                            if (method == null) {
                                method = findDeclaredMethodLoose(current, am.methodName, eventType);
                            }
                            if (method == null) {
                                method = findDeclaredMethodByNameAndAnnotation(current, am.methodName);
                            }

                            java.lang.invoke.MethodHandle mh = null;
                            boolean mhIsStatic = false;
                            Throwable mhFindThrowable = null;
                            if (method == null) {
                                try {
                                    java.lang.invoke.MethodHandles.Lookup lk;
                                    try {
                                        lk = java.lang.invoke.MethodHandles.privateLookupIn(
                                                current, java.lang.invoke.MethodHandles.lookup());
                                    } catch (Throwable tLk) {
                                        lk = java.lang.invoke.MethodHandles.publicLookup().in(current);
                                    }
                                    java.lang.invoke.MethodType mt =
                                            java.lang.invoke.MethodType.methodType(void.class, eventType);
                                    try {
                                        mh = lk.findStatic(current, am.methodName, mt);
                                        mhIsStatic = true;
                                    } catch (Throwable tStatic) {
                                        try {
                                            mh = lk.findVirtual(current, am.methodName, mt);
                                            mhIsStatic = false;
                                        } catch (Throwable tVirt) {
                                            mhFindThrowable = tVirt;
                                        }
                                    }
                                } catch (Throwable tAll) {
                                    mhFindThrowable = tAll;
                                }
                            }

                            if (method == null && mh == null) {
                                methodResolutionFailure++;
                                methodResolutionFailureDetails.add(
                                        current.getSimpleName() + "#" + am.methodName);

                                StringBuilder actualParams = new StringBuilder();
                                try {
                                    boolean first = true;
                                    for (java.lang.reflect.Method mm : current.getDeclaredMethods()) {
                                        if (!am.methodName.equals(mm.getName())) continue;
                                        if (!first) actualParams.append(" | ");
                                        actualParams.append(am.methodName).append("(");
                                        Class<?>[] pts;
                                        try { pts = mm.getParameterTypes(); }
                                        catch (Throwable t) { pts = new Class<?>[0]; }
                                        for (int i = 0; i < pts.length; i++) {
                                            if (i > 0) actualParams.append(", ");
                                            actualParams.append(pts[i].getName());
                                        }
                                        actualParams.append(")");
                                        first = false;
                                    }
                                    if (first) actualParams.append("<no overloads found>");
                                } catch (Throwable t) {
                                    actualParams.append("<getDeclaredMethods failed: ")
                                            .append(t.getClass().getSimpleName());
                                    String msg = t.getMessage();
                                    if (msg != null && !msg.isEmpty()) {
                                        actualParams.append(": ").append(msg);
                                    }
                                    actualParams.append(">");
                                }
                                String getDeclaredDetail;
                                if (getDeclaredThrowable == null) {
                                    getDeclaredDetail = "null";
                                } else {
                                    String m = getDeclaredThrowable.getMessage();
                                    getDeclaredDetail = getDeclaredThrowable.getClass().getSimpleName()
                                            + (m == null || m.isEmpty() ? "" : ": " + m);
                                }
                                String mhFindDetail;
                                if (mhFindThrowable == null) {
                                    mhFindDetail = "n/a";
                                } else {
                                    String m = mhFindThrowable.getMessage();
                                    mhFindDetail = mhFindThrowable.getClass().getSimpleName()
                                            + (m == null || m.isEmpty() ? "" : ": " + m);
                                }
                                String mlfMsg = "[Vyne-Bus] " + current.getSimpleName()
                                        + "#" + am.methodName + ": method lookup failed"
                                        + " (expected=" + eventType.getName()
                                        + ", rawBytecode=" + am.rawParamClassName
                                        + ", remapBytecode=" + am.paramClassName
                                        + ", actualMethods=[" + actualParams + "]"
                                        + ", getDeclaredError=" + getDeclaredDetail
                                        + ", mhFindError=" + mhFindDetail
                                        + ") — handler will NOT fire.";
                                try { System.out.println(mlfMsg); } catch (Throwable ignored) {}
                                VyneLogger.warn(mlfMsg + " Root cause is usually the class listed after "
                                        + "'NoClassDefFoundError:' above — add a shim for it "
                                        + "or remove the mod's hard dependency.");
                                continue;
                            }
                            if (method != null) {
                                try { method.setAccessible(true); } catch (Throwable ignored) {}
                            }

                            boolean annotationSurvivedLoad = false;
                            if (method != null) {
                                try {
                                    for (java.lang.annotation.Annotation ann : method.getAnnotations()) {
                                        if (ann.annotationType().getName().contains("SubscribeEvent")) {
                                            annotationSurvivedLoad = true;
                                            break;
                                        }
                                    }
                                } catch (Throwable ignored) {}
                            } else {
                                annotationSurvivedLoad = true;
                            }
                            if (!annotationSurvivedLoad) {
                                annotationStrippedRef[0]++;
                                annotationStrippedNames.add(current.getSimpleName()
                                        + "#" + am.methodName);
                            }

                            Class<?> effectiveEventType = eventType;
                            if (method != null) {
                                try {
                                    Class<?>[] pts = method.getParameterTypes();
                                    if (pts.length == 1 && pts[0] != eventType) {
                                        effectiveEventType = pts[0];
                                    }
                                } catch (Throwable ignored) {}
                            }

                            final Class<?> evTypeF = effectiveEventType;
                            final Class<?> declaringClsF = current;
                            final String handlerDisplayName = am.methodName;

                            if (method != null) {
                                final java.lang.reflect.Method methodF = method;
                                final String keyF = declaringClsF.getName() + "#" + methodF.getName();
                                listeners.add(event -> {
                                    if (!listenerEventMatches(evTypeF, event)) return;
                                    invokeSubscribeHandler(methodF, instance, event, keyF,
                                        declaringClsF.getSimpleName());
                                });
                            } else {
                                final java.lang.invoke.MethodHandle mhF =
                                        mhIsStatic ? mh : (instance == null ? null : mh.bindTo(instance));
                                final boolean staticF = mhIsStatic;
                                final String keyF = declaringClsF.getName() + "#" + handlerDisplayName;
                                listeners.add(event -> {
                                    if (!listenerEventMatches(evTypeF, event)) return;
                                    if (mhF == null) return;
                                    try {
                                        if (staticF) {
                                            mhF.invoke(event);
                                        } else {
                                            mhF.invoke(event);
                                        }
                                    } catch (Throwable e) {
                                        if (shouldLogHandlerFailure(keyF, e)) {
                                            VyneLogger.warn("[Vyne-Bus] @SubscribeEvent " + handlerDisplayName
                                                    + " in " + declaringClsF.getSimpleName()
                                                    + " (MethodHandle) threw: " + e
                                                    + "  (suppressing further identical failures for this handler)");
                                        }
                                        if (event.getClass().getName().contains("RegisterEvent")) {
                                            throw new RuntimeException("Critical registration failure in MethodHandle @SubscribeEvent " + handlerDisplayName, e);
                                        }
                                    }
                                });
                            }
                            registered++;
                            String wiredVia = (method != null) ? "" : " [via MethodHandle]";
                            String displayName = (method != null) ? method.getName() : am.methodName;
                            registeredDetails.add(current.getSimpleName() + "#" + displayName
                                    + "(" + effectiveEventType.getSimpleName() + ")" + wiredVia);
                        } catch (Throwable t) {
                            methodResolutionFailure++;
                            methodResolutionFailureDetails.add(current.getSimpleName()
                                    + "#" + am.methodName + " [" + t.getClass().getSimpleName() + "]");
                            VyneLogger.warn("[Vyne-Bus] " + current.getSimpleName()
                                    + "#" + am.methodName + ": unexpected failure during registration: "
                                    + t, t);
                        }
                    }
                }
            } catch (Throwable outer) {
                failedInspect++;
                failedInspectNames.add(current.getSimpleName() + " (unexpected: "
                        + outer.getClass().getSimpleName() + ")");
                VyneLogger.warn("[Vyne-Bus] Inspection of " + current.getName()
                        + " threw unexpectedly; continuing with superclass (" + outer + ")");
            }

            try {
                current = current.getSuperclass();
            } catch (Throwable t) {
                current = null;
            }
        }

        int skippedMissing = skippedMissingRef[0];
        int annotationStripped = annotationStrippedRef[0];
        StringBuilder summary = new StringBuilder();
        summary.append("[Vyne-Bus] ").append(cls.getSimpleName())
                .append(": ").append(registered).append(" @SubscribeEvent handler(s) registered");
        if (totalBytecodeSubscribeCount > 0 && registered < totalBytecodeSubscribeCount) {
            summary.append(" (bytecode declared ").append(totalBytecodeSubscribeCount).append(")");
        }
        if (annotationStripped > 0) {
            summary.append(", ").append(annotationStripped)
                    .append(" handler(s) had @SubscribeEvent erased at class-load — rescued via ")
                    .append("bytecode fallback: ").append(String.join(", ", annotationStrippedNames));
        }
        if (skippedMissing > 0 || !skippedMissingNames.isEmpty()) {
            summary.append(", ").append(Math.max(skippedMissing, skippedMissingNames.size()))
                    .append(" skipped (missing event type shim): ")
                    .append(String.join(", ", skippedMissingNames));
        }
        if (methodResolutionFailure > 0) {
            summary.append(", ").append(methodResolutionFailure)
                    .append(" method-resolution failure(s): ")
                    .append(String.join(", ", methodResolutionFailureDetails));
        }
        if (failedInspect > 0) {
            summary.append(", ").append(failedInspect)
                    .append(" class-level inspect failure(s): ")
                    .append(String.join(", ", failedInspectNames));
        }

        if (registered > 0 && !registeredDetails.isEmpty()) {
            summary.append(" — wired: ").append(String.join(", ", registeredDetails));
        }
        boolean hasAny = registered > 0 || skippedMissing > 0 || !skippedMissingNames.isEmpty()
                || failedInspect > 0 || totalBytecodeSubscribeCount > 0
                || annotationStripped > 0 || methodResolutionFailure > 0;

        try { System.out.println(summary.toString()); } catch (Throwable ignored) {}
        if (registered == 0 && !hasAny) {
            VyneLogger.debug(summary + " — no methods annotated with @SubscribeEvent found.");
        } else if (registered == 0 && totalBytecodeSubscribeCount > 0) {
            VyneLogger.warn(summary + " — bytecode listed "
                    + totalBytecodeSubscribeCount + " handler(s) but none resolved. "
                    + "See per-handler [Vyne-Bus] WARN lines above for the exact failure cause "
                    + "(missing event shim, classloader mismatch, or method signature drift).");
        } else if (skippedMissing > 0 || !skippedMissingNames.isEmpty() || failedInspect > 0
                || annotationStripped > 0 || methodResolutionFailure > 0) {
            VyneLogger.warn(summary.toString());
        } else {
            VyneLogger.info(summary.toString());
        }
    }

    private static java.lang.reflect.Method findDeclaredMethodLoose(Class<?> cls, String methodName,
                                                                    Class<?> eventType) {
        try {
            for (java.lang.reflect.Method m : cls.getDeclaredMethods()) {
                if (!methodName.equals(m.getName())) continue;
                if (m.getParameterCount() != 1) continue;
                Class<?> p;
                try { p = m.getParameterTypes()[0]; } catch (Throwable t) { continue; }
                if (p.isAssignableFrom(eventType) || eventType.isAssignableFrom(p)) {
                    return m;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static java.lang.reflect.Method findDeclaredMethodByNameAndAnnotation(
            Class<?> cls, String methodName) {
        try {
            java.lang.reflect.Method bestMatch = null;
            int matches = 0;
            for (java.lang.reflect.Method m : cls.getDeclaredMethods()) {
                if (!methodName.equals(m.getName())) continue;
                if (m.getParameterCount() != 1) continue;
                try {
                    for (java.lang.annotation.Annotation ann : m.getAnnotations()) {
                        if (ann.annotationType().getName().contains("SubscribeEvent")) {
                            matches++;
                            bestMatch = m;
                            break;
                        }
                    }
                } catch (Throwable ignored) {
                    matches++;
                    bestMatch = m;
                }
            }
            if (matches == 1) return bestMatch;

            if (matches > 1) return bestMatch;

            java.lang.reflect.Method solo = null;
            int soloCount = 0;
            for (java.lang.reflect.Method m : cls.getDeclaredMethods()) {
                if (!methodName.equals(m.getName())) continue;
                if (m.getParameterCount() != 1) continue;
                solo = m;
                soloCount++;
            }
            if (soloCount == 1) return solo;
        } catch (Throwable ignored) {}
        return null;
    }

    private static String safeLoaderName(ClassLoader cl) {
        if (cl == null) return "null";
        try {
            return cl.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(cl));
        } catch (Throwable t) {
            return "<unknown loader>";
        }
    }

    private static final class AsmMethodEntry {
        final String methodName;

        final String rawParamClassName;

        final String paramClassName;
        AsmMethodEntry(String methodName, String rawParamClassName, String paramClassName) {
            this.methodName = methodName;
            this.rawParamClassName = rawParamClassName;
            this.paramClassName = paramClassName;
        }
    }

    private static String remapParamClassName(String rawDotted) {
        if (rawDotted == null || rawDotted.isEmpty()) return rawDotted;

        if (rawDotted.startsWith("net.minecraftforge.")) {
            if (rawDotted.startsWith("net.minecraftforge.fml.")
                    || rawDotted.startsWith("net.minecraftforge.vyne.")) {
                return rawDotted;
            }
            String remapped = "net.neoforged.neoforge." + rawDotted.substring("net.minecraftforge.".length());

            if (remapped.contains("ForgeConfigSpec")) {
                remapped = remapped.replace("ForgeConfigSpec", "ModConfigSpec");
            }
            return remapped;
        }

        if (rawDotted.startsWith("net.minecraft.")) {
            try {
                if (dev.stolym.vyne.mappings.MappingRegistry.isReady()) {
                    String slash = rawDotted.replace('.', '/');
                    String intermediary = dev.stolym.vyne.mappings.MappingRegistry.get()
                            .mapClass(slash);
                    if (intermediary != null && !intermediary.isEmpty()) {
                        return intermediary.replace('/', '.');
                    }
                }
            } catch (Throwable ignored) {}
        }
        return rawDotted;
    }

    private static java.util.List<AsmMethodEntry> readSubscribeEventMethodsViaAsm(Class<?> cls) {
        byte[] bytes = readClassBytes(cls);
        if (bytes == null) return null;
        try {
            org.objectweb.asm.ClassReader reader = new org.objectweb.asm.ClassReader(bytes);
            java.util.List<AsmMethodEntry> out = new java.util.ArrayList<>();
            reader.accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                @Override
                public org.objectweb.asm.MethodVisitor visitMethod(int access, String name,
                                                                   String descriptor, String signature,
                                                                   String[] exceptions) {
                    final org.objectweb.asm.Type[] argTypes = org.objectweb.asm.Type.getArgumentTypes(descriptor);
                    if (argTypes.length != 1 || argTypes[0].getSort() != org.objectweb.asm.Type.OBJECT) {
                        return null;
                    }
                    final String rawParamClassName = argTypes[0].getClassName();
                    final String remappedParamClassName = remapParamClassName(rawParamClassName);
                    final String methodName = name;
                    return new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9) {
                        @Override
                        public org.objectweb.asm.AnnotationVisitor visitAnnotation(String annDesc, boolean visible) {
                            if (annDesc != null && annDesc.contains("SubscribeEvent")) {
                                out.add(new AsmMethodEntry(
                                        methodName, rawParamClassName, remappedParamClassName));
                            }
                            return null;
                        }
                    };
                }
            }, org.objectweb.asm.ClassReader.SKIP_CODE | org.objectweb.asm.ClassReader.SKIP_DEBUG
                    | org.objectweb.asm.ClassReader.SKIP_FRAMES);
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    private static byte[] readClassBytes(Class<?> cls) {
        String resName = cls.getName().replace('.', '/') + ".class";
        ClassLoader[] loaders = {
                cls.getClassLoader(),
                ClassLoader.getSystemClassLoader()
        };
        for (ClassLoader cl : loaders) {
            if (cl == null) continue;
            try (java.io.InputStream in = cl.getResourceAsStream(resName)) {
                if (in != null) {
                    return in.readAllBytes();
                }
            } catch (Throwable ignored) {}
        }
        try {
            java.security.ProtectionDomain pd = cls.getProtectionDomain();
            if (pd != null && pd.getCodeSource() != null && pd.getCodeSource().getLocation() != null) {
                java.net.URL loc = pd.getCodeSource().getLocation();
                if ("file".equalsIgnoreCase(loc.getProtocol())) {
                    java.io.File f = new java.io.File(loc.toURI());
                    if (f.isFile()) {
                        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(f)) {
                            java.util.zip.ZipEntry entry = zf.getEntry(resName);
                            if (entry != null) {
                                try (java.io.InputStream in = zf.getInputStream(entry)) {
                                    return in.readAllBytes();
                                }
                            }
                        }
                    } else if (f.isDirectory()) {
                        java.io.File cf = new java.io.File(f, resName);
                        if (cf.isFile()) {
                            return java.nio.file.Files.readAllBytes(cf.toPath());
                        }
                    }
                } else {
                    try (java.io.InputStream in = loc.openStream()) {
                        return in.readAllBytes();
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private int reflectiveRegisterFallback(Class<?> current, Object instance, Class<?> declaring,
                                           java.util.List<String> skippedMissingNames,
                                           int[] skippedMissingCounter) {
        int registered = 0;

        java.util.Set<String> namesSeen = new java.util.LinkedHashSet<>();
        try {
            java.lang.reflect.Method[] methods = current.getDeclaredMethods();
            for (java.lang.reflect.Method method : methods) {
                namesSeen.add(method.getName());
                int r = tryRegisterReflectiveMethod(method, instance, declaring,
                        skippedMissingNames, skippedMissingCounter, current);
                if (r > 0) registered += r;
            }
            return registered;
        } catch (NoClassDefFoundError ncdfe) {
            VyneLogger.warn("[Vyne-Bus] Bulk getDeclaredMethods() failed for " + current.getName()
                    + " (" + ncdfe.getMessage() + "); retrying per-method via ASM-driven lookup.");
        } catch (Throwable t) {
            VyneLogger.warn("[Vyne-Bus] Reflective scan fallback failed for "
                    + current.getName() + " (" + t.getClass().getSimpleName() + "), no methods registered");
            return 0;
        }

        java.util.List<AsmMethodEntry> asmMethods = readSubscribeEventMethodsViaAsm(current);
        if (asmMethods == null) return registered;
        for (AsmMethodEntry am : asmMethods) {
            try {
                Class<?> eventType = Class.forName(am.paramClassName, false, current.getClassLoader());
                java.lang.reflect.Method method = current.getDeclaredMethod(am.methodName, eventType);
                int r = tryRegisterReflectiveMethod(method, instance, declaring,
                        skippedMissingNames, skippedMissingCounter, current);
                if (r > 0) registered += r;
            } catch (Throwable t) {
                skippedMissingCounter[0]++;
                skippedMissingNames.add(current.getSimpleName() + "#" + am.methodName);
            }
        }
        return registered;
    }

    private int tryRegisterReflectiveMethod(java.lang.reflect.Method method, Object instance,
                                            Class<?> declaring, java.util.List<String> skippedMissingNames,
                                            int[] skippedMissingCounter, Class<?> current) {
        try {
            boolean isSubscriber = false;
            try {
                for (java.lang.annotation.Annotation ann : method.getAnnotations()) {
                    String annName = ann.annotationType().getName();
                    if (annName.contains("SubscribeEvent")) { isSubscriber = true; break; }
                }
            } catch (Throwable t) {
                isSubscriber = methodHasSubscribeEventInBytecode(method);
                if (!isSubscriber) {
                    if (VyneLogLevel.isVerbose()) {
                        VyneLogger.debug("[Vyne-Bus] getAnnotations() failed on "
                                + current.getSimpleName() + "#" + method.getName() + " and bytecode scan "
                                + "didn't see @SubscribeEvent: " + t);
                    }
                    return 0;
                }
            }
            if (!isSubscriber) return 0;
            if (method.getParameterCount() != 1) return 0;

            Class<?> eventType;
            try {
                eventType = method.getParameterTypes()[0];
            } catch (NoClassDefFoundError ncdfe) {
                skippedMissingCounter[0]++;
                skippedMissingNames.add(current.getSimpleName() + "#" + method.getName()
                        + " (" + ncdfe.getMessage() + ")");
                return 0;
            }
            method.setAccessible(true);

            final Class<?> evF = eventType;
            final java.lang.reflect.Method mF = method;
            final Class<?> declF = declaring;
            final String keyF = declF.getName() + "#" + mF.getName();
            listeners.add(event -> {
                if (event == null || !listenerEventMatches(evF, event)) return;
                invokeSubscribeHandler(mF, instance, event, keyF, declF.getSimpleName());
            });
            if (VyneLogLevel.isVerbose()) {
                VyneLogger.info("[Vyne-Bus] (reflect) Registered @SubscribeEvent: "
                        + current.getSimpleName() + "#" + method.getName());
            }
            return 1;
        } catch (NoClassDefFoundError ncdfe) {
            skippedMissingCounter[0]++;
            skippedMissingNames.add(current.getSimpleName() + "#" + method.getName()
                    + " (" + ncdfe.getMessage() + ")");
        } catch (Throwable t) {
            if (VyneLogLevel.isVerbose()) {
                VyneLogger.debug("[Vyne-Bus] Skipping " + current.getSimpleName()
                        + "#" + method.getName() + ": " + t);
            }
        }
        return 0;
    }

    private static boolean methodHasSubscribeEventInBytecode(java.lang.reflect.Method method) {
        Class<?> declaring = method.getDeclaringClass();
        java.util.List<AsmMethodEntry> asm = readSubscribeEventMethodsViaAsm(declaring);
        if (asm == null) return false;
        String targetName = method.getName();
        String[] paramNames = java.util.Arrays.stream(method.getParameterTypes())
                .map(Class::getName).toArray(String[]::new);
        for (AsmMethodEntry entry : asm) {
            if (!entry.methodName.equals(targetName)) continue;
            if (paramNames.length == 1 && paramNames[0].equals(entry.paramClassName)) return true;
        }
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void post(Object event) {
        if (event == null) return;
        List<Consumer<Object>> list;
        synchronized (listeners) {
            list = new ArrayList<>(listeners);
        }

        String evtName = event.getClass().getName();
        if (VyneLogLevel.isVerbose() && evtName.contains("RegisterEvent")) {
            VyneLogger.debug("[Vyne-Bus] Posting " + evtName + " to " + list.size() + " listeners");
        }

        for (Consumer<Object> listener : list) {
            try {
                listener.accept(event);
            } catch (Throwable t) {
                logListenerFailure(evtName, t);
            }
        }
    }

    private static final java.util.Map<String, int[]> LISTENER_FAILURE_COUNTS =
            java.util.Collections.synchronizedMap(new java.util.HashMap<>());

    private static final int LISTENER_FAILURE_PRINT_THRESHOLD = 16;

    private static void logListenerFailure(String evtName, Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String key = evtName + "|" + root.getClass().getName() + "|" + String.valueOf(root.getMessage());
        int[] counter = LISTENER_FAILURE_COUNTS.computeIfAbsent(key, k -> new int[1]);
        int n;
        synchronized (counter) {
            n = ++counter[0];
        }
        if (n == 1) {
            VyneLogger.error("[Vyne-Bus] Error in listener for " + evtName, t);
        } else if (n == LISTENER_FAILURE_PRINT_THRESHOLD
                || (n > LISTENER_FAILURE_PRINT_THRESHOLD
                    && Integer.bitCount(n) == 1)) {
            VyneLogger.warn("[Vyne-Bus] " + evtName + " listener error repeated "
                    + n + " times (" + root.getClass().getSimpleName()
                    + (root.getMessage() != null ? ": " + root.getMessage() : "")
                    + "); suppressing further stacktraces for this failure.");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addListener(Consumer<?> consumer) {
        Class<?> eventType = resolveConsumerType(consumer);
        VyneLogger.info("[Vyne-Bus] addListener: " + (eventType != null ? eventType.getName() : "null") + " consumer=" + consumer.getClass().getName());
        listeners.add(event -> {
            if (eventType != null && !listenerEventMatches(eventType, event)) return;
            String modId = dev.stolym.vyne.containment.VyneModIdentity.resolveModId(consumer.getClass());
            dev.stolym.vyne.containment.VyneExecutionGate.gate(() -> {
                try { ((Consumer<Object>) consumer).accept(event); }
                catch (ClassCastException ignored) {}
            }, modId, "event:" + event.getClass().getSimpleName());
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addListener(EventPriority priority, Consumer<?> consumer) {
        Class<?> eventType = resolveConsumerType(consumer);
        listeners.add(event -> {
            if (eventType != null && !listenerEventMatches(eventType, event)) return;
            String modId = dev.stolym.vyne.containment.VyneModIdentity.resolveModId(consumer.getClass());
            dev.stolym.vyne.containment.VyneExecutionGate.gate(() -> {
                try { ((Consumer<Object>) consumer).accept(event); }
                catch (ClassCastException ignored) {}
            }, modId, "event:" + event.getClass().getSimpleName());
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addListener(EventPriority priority, boolean receiveCancelled, Consumer<?> consumer) {
        Class<?> eventType = resolveConsumerType(consumer);
        listeners.add(event -> {
            if (eventType != null && !listenerEventMatches(eventType, event)) return;
            try { ((Consumer<Object>) consumer).accept(event); }
            catch (ClassCastException ignored) {}
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addListener(EventPriority priority, boolean receiveCancelled,
                            Class<?> eventType, Consumer<?> consumer) {
        if (eventType == null || consumer == null) return;
        listeners.add(event -> {
            if (event == null || !listenerEventMatches(eventType, event)) return;
            try { ((Consumer<Object>) consumer).accept(event); }
            catch (ClassCastException ignored) {}
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addListener(Class<?> eventType, Consumer<?> consumer) {
        addListener(EventPriority.NORMAL, false, eventType, consumer);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addListener(EventPriority priority, Class<?> eventType, Consumer<?> consumer) {
        addListener(priority, false, eventType, consumer);
    }

    private static Class<?> resolveConsumerType(Consumer<?> consumer) {
        try {
            java.lang.reflect.Type[] ifaces = consumer.getClass().getGenericInterfaces();
            for (java.lang.reflect.Type iface : ifaces) {
                if (iface instanceof java.lang.reflect.ParameterizedType pt) {
                    java.lang.reflect.Type raw = pt.getRawType();
                    if (raw == Consumer.class || (raw instanceof Class<?> c && c.getName().endsWith(".Consumer"))) {
                        java.lang.reflect.Type arg = pt.getActualTypeArguments()[0];
                        if (arg instanceof Class<?> cls) return cls;
                    }
                }
            }
            java.lang.reflect.Method sam = consumer.getClass().getMethod("accept", Object.class);
            java.lang.reflect.Type[] params = sam.getGenericParameterTypes();
            if (params.length == 1 && params[0] instanceof Class<?> cls) return cls;
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean listenerEventMatches(Class<?> expectedType, Object event) {
        if (expectedType == null) {
            return true;
        }
        if (event == null) {
            return false;
        }
        if (expectedType.isInstance(event)) {
            return true;
        }
        return expectedType.getName().equals(event.getClass().getName());
    }

    @Override
    public void unregister(Object obj) {
        if (VyneLogLevel.isVerbose()) VyneLogger.debug("[Vyne-Bus] Unregistering: " + obj);
    }

    private static Object invokeKeyAccessor(Object target, String mName) throws Exception {
        Class<?> c = target.getClass();
        while (c != null && c != Object.class) {
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(mName) && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    return m.invoke(target);
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    public void dispatchRegistration(ClassLoader modCl) {
        if (VyneLogLevel.isVerbose()) {
            System.out.println("[Vyne-Bus] Starting registration dispatch phase for mod...");
        }
        VyneLogger.info("[Vyne-Bus] Starting registration dispatch phase for mod...");
        try {
            Class<?> eventClass = modCl.loadClass("net.neoforged.neoforge.registries.RegisterEvent");
            java.lang.reflect.Constructor<?> oneArgCtor = null;
            java.lang.reflect.Constructor<?> twoArgCtor = null;
            for (java.lang.reflect.Constructor<?> ctor : eventClass.getDeclaredConstructors()) {
                ctor.setAccessible(true);
                if (ctor.getParameterCount() == 1) oneArgCtor = ctor;
                if (ctor.getParameterCount() == 2) twoArgCtor = ctor;
            }
            if (oneArgCtor == null && twoArgCtor == null) {
                VyneLogger.warn("[Vyne-Bus] No usable constructor on RegisterEvent");
                return;
            }

            java.util.LinkedHashSet<String> firedLocs = new java.util.LinkedHashSet<>();
            java.util.List<Object> allKeys = new java.util.ArrayList<>(registryKeys);

            try {
                Class<?> regClass = null;
                try {
                    regClass = modCl.loadClass("net.minecraft.class_7923");
                } catch (ClassNotFoundException e) {
                    String builtInName = MappingRegistry.isReady()
                        ? MappingRegistry.get().mapClass("net/minecraft/core/registries/BuiltInRegistries").replace('/', '.')
                        : "net.minecraft.core.registries.BuiltInRegistries";
                    regClass = modCl.loadClass(builtInName);
                }

                if (VyneLogLevel.isVerbose()) VyneLogger.info("[Vyne-Bus] Scanning registries in " + regClass.getName());
                java.lang.reflect.Field[] fields = regClass.getDeclaredFields();
                for (java.lang.reflect.Field field : fields) {
                    if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true);
                    Object candidate = null;
                    try { candidate = field.get(null); } catch (Throwable ignored) { continue; }
                    if (candidate == null) continue;

                    Object key = null;
                    for (String mName : new String[]{"method_30517", "key", "getKey"}) {
                        try {
                            key = invokeKeyAccessor(candidate, mName);
                            if (key != null) break;
                        } catch (Throwable ignored) {}
                    }

                    if (key != null) {
                        String loc = ReflectionHelper.extractRegistryKeyLocation(key);
                        if (loc != null && !loc.equals("null")) {
                            allKeys.add(key);
                            if (VyneLogLevel.isVerbose()) {
                                VyneLogger.debug("[Vyne-Bus] Found standard registry key: " + loc);
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                VyneLogger.warn("[Vyne-Bus] Standard registries scan failed: "
                    + t.getClass().getName() + ": " + t.getMessage());
            }

            try {
                Class<?> keysClass = modCl.loadClass("net.neoforged.neoforge.registries.NeoForgeRegistries$Keys");
                for (java.lang.reflect.Field field : keysClass.getDeclaredFields()) {
                    if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true);
                    Object regKey = field.get(null);
                    if (regKey != null) allKeys.add(regKey);
                }
            } catch (Exception e) {
                VyneLogger.debug("[Vyne-Bus] NeoForgeRegistries$Keys scan: " + e.getMessage());
            }

            for (String path : dev.stolym.vyne.registry.VyneGenericRegistry.getAllRegistryPaths()) {
                try {
                    ResourceLocation id = toResourceLocation(path);
                    Object key = net.minecraft.resources.ResourceKey.createRegistryKey(id);
                    allKeys.add(key);
                } catch (Throwable ignored) {}
            }

            try {
                for (Object drObj : net.neoforged.neoforge.registries.DeferredRegister.getAllRegisters()) {
                    if (drObj instanceof net.neoforged.neoforge.registries.DeferredRegister<?> dr) {
                        Object key = dr.getRegistryKey();
                        if (key != null) allKeys.add(key);
                    } else {
                        try {
                            Method getRegistryKey = drObj.getClass().getMethod("getRegistryKey");
                            getRegistryKey.setAccessible(true);
                            Object key = getRegistryKey.invoke(drObj);
                            if (key != null) allKeys.add(key);
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}

            java.util.List<Object> sortedKeys = new java.util.ArrayList<>(allKeys);
            sortedKeys.sort(Comparator
                .comparingInt((Object k) -> registryDispatchRank(ReflectionHelper.extractRegistryKeyLocation(k)))
                .thenComparing(k -> Objects.toString(ReflectionHelper.extractRegistryKeyLocation(k), "\uFFFF")));

            final java.lang.reflect.Constructor<?> finalOneArg = oneArgCtor;
            final java.lang.reflect.Constructor<?> finalTwoArg = twoArgCtor;

            String modId = dev.stolym.vyne.containment.VyneModIdentity.resolveModId(modCl);
            dev.stolym.vyne.containment.VyneModIdentity.tagCurrentThread(modId);
            java.util.Map<String, Object> regKeyByLoc = new java.util.HashMap<>();
            try {
                for (Object regKey : sortedKeys) {
                    String loc = ReflectionHelper.extractRegistryKeyLocation(regKey);
                    if (loc == null || loc.isEmpty() || "null".equals(loc)) {
                        continue;
                    }
                    if (!firedLocs.add(loc)) {
                        continue;
                    }
                    Object dispatchKey = resolveCanonicalRegistryKeyForLocation(modCl, loc, regKey);
                    boolean dispatchKeyIsProxy = dispatchKey != null
                            && dispatchKey.getClass().getName().endsWith("VyneRegistryProxy");
                    if (dispatchKeyIsProxy) {
                        try {
                            ResourceLocation proxyId = toResourceLocation(loc);
                            dispatchKey = ResourceKey.createRegistryKey(proxyId);
                        } catch (Throwable ignored) {}
                    }
                    regKeyByLoc.put(loc, dispatchKey);
                    ClassLoader prevCl = Thread.currentThread().getContextClassLoader();
                    try {
                        Thread.currentThread().setContextClassLoader(modCl);
                        VyneLogger.info("[Vyne-Bus] Dispatching RegisterEvent for: " + loc + " (key=" + dispatchKey.getClass().getSimpleName() + ")");
                        Object liveReg = resolveLiveRegistry(dispatchKey, modCl);
                        Object event = (finalTwoArg != null && liveReg != null)
                            ? finalTwoArg.newInstance(dispatchKey, liveReg)
                            : finalOneArg.newInstance(dispatchKey);
                        post(event);
                    } catch (Throwable t) {
                        if (t instanceof dev.stolym.vyne.containment.VyneFailureTriage.VyneCleanShutdownError) throw t;
                        VyneLogger.error("[Vyne-Bus] Critical registration failure for " + loc + " in mod " + modId, t);
                    } finally {
                        Thread.currentThread().setContextClassLoader(prevCl);
                    }
                }
                for (String secondPath : secondPassRegistryPaths()) {
                    Object regKeyForPass = regKeyByLoc.get(secondPath);
                    if (regKeyForPass == null) {
                        VyneLogger.debug("[Vyne-Bus] Second pass skipped (no key in sweep): " + secondPath);
                        continue;
                    }
                    ClassLoader prevCl = Thread.currentThread().getContextClassLoader();
                    try {
                        Thread.currentThread().setContextClassLoader(modCl);
                        Object liveReg = resolveLiveRegistry(regKeyForPass, modCl);
                        Object event2 = (finalTwoArg != null && liveReg != null)
                            ? finalTwoArg.newInstance(regKeyForPass, liveReg)
                            : finalOneArg.newInstance(regKeyForPass);
                        post(event2);
                        VyneLogger.info("[Vyne-Bus] Second RegisterEvent for " + secondPath
                            + " (after other registries; DeferredRegister suppliers may depend on later passes).");
                    } catch (Throwable t) {
                        if (t instanceof dev.stolym.vyne.containment.VyneFailureTriage.VyneCleanShutdownError) throw t;
                        VyneLogger.warn("[Vyne-Bus] Second registration pass failed for " + secondPath + ": " + t.getMessage());
                    } finally {
                        Thread.currentThread().setContextClassLoader(prevCl);
                    }
                }
            } finally {
                dev.stolym.vyne.containment.VyneModIdentity.clearCurrentThread();
            }
            VyneLogger.info("[Vyne-Bus] Dispatched " + firedLocs.size() + " registration event(s).");
        } catch (Exception e) {
            VyneLogger.warn("[Vyne-Bus] Failed to dispatch registration: "
                + e.getClass().getName() + ": " + e.getMessage(), e);
        }
    }

    private static ResourceLocation toResourceLocation(String id) {
        String normalized = id.replace('\\', '/');
        int colon = normalized.indexOf(':');
        if (colon > 0) {
            return new ResourceLocation(normalized.substring(0, colon), normalized.substring(colon + 1));
        }
        int slash = normalized.indexOf('/');
        if (slash > 0) {
            return new ResourceLocation(normalized.substring(0, slash), normalized.substring(slash + 1));
        }
        return new ResourceLocation("minecraft", normalized);
    }

    private static java.util.List<String> secondPassRegistryPaths() {
        String raw = System.getProperty("vyne.bus.secondPassRegistries");
        if (raw != null) {
            raw = raw.trim();
            if (raw.isEmpty()) {
                return java.util.List.of();
            }
            java.util.ArrayList<String> out = new java.util.ArrayList<>();
            for (String part : raw.split(",")) {
                if (part == null) {
                    continue;
                }
                String p = part.trim();
                if (!p.isEmpty()) {
                    out.add(p);
                }
            }
            return java.util.List.copyOf(out);
        }
        return java.util.List.of(
            "minecraft:block",
            "minecraft:item",
            "minecraft:recipe_serializer",
            "minecraft:recipe_type",
            "minecraft:worldgen/feature"
        );
    }

    private static Object resolveCanonicalRegistryKeyForLocation(ClassLoader modCl, String loc, Object fallback) {
        if (loc == null || modCl == null) {
            return fallback;
        }
        String norm = ReflectionHelper.normalizeRegistryKeyLocationString(loc);
        try {
            Class<?> regClass;
            try {
                regClass = modCl.loadClass("net.minecraft.class_7923");
            } catch (ClassNotFoundException e) {
                String builtInName = MappingRegistry.isReady()
                    ? MappingRegistry.get().mapClass("net/minecraft/core/registries/BuiltInRegistries").replace('/', '.')
                    : "net.minecraft.core.registries.BuiltInRegistries";
                regClass = modCl.loadClass(builtInName);
            }
            for (java.lang.reflect.Field field : regClass.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                Object candidate;
                try {
                    candidate = field.get(null);
                } catch (Throwable ignored) {
                    continue;
                }
                if (candidate == null) {
                    continue;
                }
                Object key = null;
                for (String mName : new String[]{"method_30517", "key", "getKey"}) {
                    try {
                        key = invokeKeyAccessor(candidate, mName);
                        if (key != null) {
                            break;
                        }
                    } catch (Throwable ignored) {
                    }
                }
                if (key == null) {
                    continue;
                }
                String kLoc = ReflectionHelper.extractRegistryKeyLocation(key);
                if (kLoc == null) {
                    continue;
                }
                if (norm.equals(ReflectionHelper.normalizeRegistryKeyLocationString(kLoc))) {
                    return key;
                }
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static int registryDispatchRank(String loc) {
        if (loc == null) {
            return 9999;
        }
        if (!loc.startsWith("minecraft:") && !loc.startsWith("neoforge:") && !loc.startsWith("forge:")) {
            return -100;
        }

        return switch (loc) {
            case "neoforge:fluid_type", "forge:fluid_type" -> -50;
            case "minecraft:fluid" -> -40;
            case "minecraft:block" -> -30;
            case "minecraft:item" -> -20;
            case "minecraft:entity_type" -> 3;
            case "minecraft:block_entity_type" -> 4;
            case "minecraft:mob_effect" -> 5;
            case "minecraft:sound_event" -> 6;
            case "minecraft:particle_type" -> 7;
            case "minecraft:potion" -> 8;
            case "minecraft:attribute" -> 9;
            case "minecraft:enchantment" -> 10;
            default -> {
                if (loc.startsWith("minecraft:")) {
                    yield 100;
                }
                if (loc.startsWith("neoforge:") || loc.startsWith("forge:")) {
                    yield 5000;
                }
                yield 3000;
            }
        };
    }

    private static Object resolveLiveRegistry(Object regKey, ClassLoader knotCl) {
        try {
            String locStr = ReflectionHelper.extractRegistryKeyLocation(regKey);
            if (locStr == null) return null;
            Object reg = dev.stolym.vyne.registry.VyneRegistryInjector.resolveRegistryForPath(knotCl, locStr);
            if (reg == null) {
                return null;
            }
            if (reg instanceof java.util.Map<?, ?>) {
                return null;
            }
            if (!isAssignableToMcRegistry(reg, knotCl)) {
                return null;
            }
            return reg;
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean isAssignableToMcRegistry(Object reg, ClassLoader knotCl) {
        if (knotCl == null) {
            return true;
        }
        try {
            Class<?> iface = Class.forName("net.minecraft.class_2378", false, knotCl);
            return iface.isInstance(reg);
        } catch (Throwable t) {
            String n = reg.getClass().getName();
            return n.contains("Registry") && !n.contains("Unmodifiable");
        }
    }
}
