/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.engine.dynamic;

import dev.stolym.vyne.engine.dynamic.cache.EngineCache;
import dev.stolym.vyne.engine.dynamic.catalog.BridgeCatalog;
import dev.stolym.vyne.engine.dynamic.rewrite.GetServiceMultiLoaderPatch;
import dev.stolym.vyne.engine.dynamic.rewrite.GlitchCorePacketHandlerStubPatch;
import dev.stolym.vyne.engine.dynamic.rewrite.MojmapClassHeaderRemapper;
import dev.stolym.vyne.engine.dynamic.rewrite.ReferenceRewriter;
import dev.stolym.vyne.engine.dynamic.rewrite.RegistrateStackWalkerStripPatch;
import dev.stolym.vyne.engine.dynamic.rewrite.ServiceLoaderClassLoaderGuardPatch;
import dev.stolym.vyne.engine.dynamic.rewrite.EntityTypeBuilderTrackingRangePatch;
import dev.stolym.vyne.engine.dynamic.rewrite.FoodPropertiesSupplierEffectPatch;
import dev.stolym.vyne.engine.dynamic.rewrite.CreateSimpleRegistryNullValuePatch;
import dev.stolym.vyne.engine.dynamic.rewrite.RegistrateLenientErrorsPatch;
import dev.stolym.vyne.engine.dynamic.scanner.ModReferenceScanner;
import dev.stolym.vyne.engine.dynamic.scanner.ReferenceReport;
import dev.stolym.vyne.engine.dynamic.synth.AutoShimSynthesizer;
import dev.stolym.vyne.engine.dynamic.synth.StubClassSynthesizer;
import dev.stolym.vyne.mappings.MappingComposer;
import dev.stolym.vyne.vcl.RemappingClassLoader;
import dev.stolym.vyne.util.VyneLogger;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.Arrays;
import java.util.zip.ZipFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;

public final class DynamicBridgeEngine {
    public static final class PreparedMod {
        public final Path originalJar;
        public final Path rewrittenJar;
        public final Path stubsJar;
        public final Path shimsJar;
        public final int synthesizedClassCount;
        public final int shimMethodCount;
        public final int rewrittenClassCount;
        public final String sha256;

        PreparedMod(Path orig, Path rewr, Path stubs, Path shims,
                    int synths, int shimMethods, int rewr_classes, String sha) {
            this.originalJar = orig;
            this.rewrittenJar = rewr;
            this.stubsJar = stubs;
            this.shimsJar = shims;
            this.synthesizedClassCount = synths;
            this.shimMethodCount = shimMethods;
            this.rewrittenClassCount = rewr_classes;
            this.sha256 = sha;
    }
    }

    private static int indexOfAscii(byte[] data, String ascii, int from) {
        if (data == null || ascii == null || ascii.isEmpty()) {
            return -1;
    }
        byte[] needle = ascii.getBytes(StandardCharsets.US_ASCII);
        outer:
        for (int i = Math.max(0, from); i <= data.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) {
                    continue outer;
    }
    }
            return i;
    }
        return -1;
    }

    private static List<Path> extractNestedJarPaths(Path hostJar, Path nestedRoot) throws IOException {
        List<Path> out = new ArrayList<>();
        if (hostJar == null || !Files.isRegularFile(hostJar)) {
            return out;
    }
        Files.createDirectories(nestedRoot);
        try (ZipFile zf = new ZipFile(hostJar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.isDirectory()) {
                    continue;
    }
                String name = e.getName();
                boolean isJij = (name.startsWith("META-INF/jarjar/")
                        || name.startsWith("META-INF/jars/"))
                    && name.endsWith(".jar");
                if (!isJij) {
                    continue;
    }
                String flatName = name.replace('/', '_');
                Path dest = nestedRoot.resolve(flatName);
                try (InputStream is = zf.getInputStream(e)) {
                    Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
    }
                out.add(dest);
    }
    }
        return out;
    }

    private static void recordAllInterfacesFromJar(java.nio.file.Path jar) {
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            java.util.Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (!e.getName().endsWith(".class")) continue;
                try (InputStream is = zf.getInputStream(e)) {
                    recordObservedInterfaces(is.readAllBytes());
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private static void recordObservedInterfaces(byte[] classBytes) {
        if (classBytes == null) return;
        try {
            org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(classBytes);
            String className = cr.getClassName();
            String superName = cr.getSuperName();
            String[] ifaces = cr.getInterfaces();

            if (ifaces != null) {
                for (String iface : ifaces) {
                    if (iface == null) continue;
                    if (iface.startsWith("net/minecraft/class_")) {
                        dev.stolym.vyne.mappings.IntermediaryStubSuperHints.observeSuperInterface(iface);
                    } else if (iface.startsWith("net/minecraft/")
                            && dev.stolym.vyne.mappings.MappingRegistry.isReady()) {
                        String inter = dev.stolym.vyne.mappings.MappingRegistry.get().mapClass(iface);
                        if (inter != null && inter.startsWith("net/minecraft/class_")) {
                            dev.stolym.vyne.mappings.IntermediaryStubSuperHints.observeSuperInterface(inter);
                        }
                    }
                    if (iface.startsWith("net/neoforged/neoforge/client/")
                            || iface.startsWith("net/minecraftforge/client/")) {
                        dev.stolym.vyne.mappings.IntermediaryStubSuperHints
                            .observeNeoForgeClientInterface(iface);
                    }
                }
            }

            if (className != null) {
                dev.stolym.vyne.mappings.IntermediaryStubSuperHints.observeClassShape(
                    className,
                    remapClassToInter(superName),
                    remapIfacesToInter(ifaces));
            }
        } catch (Throwable ignored) {}
    }

    private static String remapClassToInter(String name) {
        if (name == null) return null;
        if (name.startsWith("net/minecraft/class_")) return name;
        if (name.startsWith("net/minecraft/") && dev.stolym.vyne.mappings.MappingRegistry.isReady()) {
            String inter = dev.stolym.vyne.mappings.MappingRegistry.get().mapClass(name);
            if (inter != null && inter.startsWith("net/minecraft/class_")) return inter;
        }
        return name;
    }

    private static String[] remapIfacesToInter(String[] ifaces) {
        if (ifaces == null || ifaces.length == 0) return ifaces;
        String[] result = new String[ifaces.length];
        for (int i = 0; i < ifaces.length; i++) {
            result[i] = remapClassToInter(ifaces[i]);
        }
        return result;
    }

    private static boolean needsFullMinecraftRemapForDynamicRewrite(String internal, byte[] classBytes) {
        if (classBytes == null || internal == null) {
            return false;
        }
        if (indexOfAscii(classBytes, "CreativeModeTab$Builder", 0) >= 0) {
            return true;
        }
        if (indexOfAscii(classBytes, "net/minecraft/world/item/CreativeModeTab$", 0) >= 0) {
            return true;
        }
        if (indexOfAscii(classBytes, "net/minecraft/world/item/CreativeModeTab;", 0) >= 0) {
            return true;
        }
        if (indexOfAscii(classBytes, "org/spongepowered/asm/mixin/", 0) >= 0) {
            return true;
        }
        return false;
    }

    private final BridgeCatalog catalog;
    private final ClasspathExistence existence;
    private final EngineCache cache;
    private final StubClassSynthesizer stubSynth;
    private final AutoShimSynthesizer shimSynth;

    public DynamicBridgeEngine(Path cacheRoot, ClassLoader probeCl, Path userOverrides) {
        this.catalog = BridgeCatalog.load(userOverrides);
        this.existence = new ClasspathExistence(probeCl);
        this.cache = new EngineCache(cacheRoot);
        this.stubSynth = new StubClassSynthesizer(catalog);
        this.shimSynth = new AutoShimSynthesizer(catalog);
        VyneLogger.info("[DynamicEngine] Initialized. Cache root: " + cacheRoot);
    }

    public void registerProvidedFromJars(Iterable<Path> jars) {
        for (Path jar : jars) {
            if (jar == null || !Files.exists(jar)) continue;
            try (ZipFile zf = new ZipFile(jar.toFile())) {
                Enumeration<? extends ZipEntry> entries = zf.entries();
                Set<String> batch = new HashSet<>();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement().getName();
                    if (!name.endsWith(".class")) continue;
                    batch.add(name.substring(0, name.length() - ".class".length()));
    }
                existence.markProvided(batch);
    } catch (IOException ioe) {
                VyneLogger.warn("[DynamicEngine] Failed to index owned classes of "
                    + jar + ": " + ioe.getMessage());
    }
    }
    }

    public PreparedMod prepare(Path modJar) throws IOException {
        ReferenceReport report = ModReferenceScanner.scan(modJar);

        publishHierarchiesToMappingComposer(report);
        if (cache.isComplete(report.jarSha256)) {
            VyneLogger.info("[DynamicEngine] Cache HIT for "
                + modJar.getFileName() + " (" + report.jarSha256.substring(0, 12) + ")");
            return new PreparedMod(modJar,
                cache.rewrittenJar(report.jarSha256),
                cache.stubsJar(report.jarSha256),
                cache.shimsJar(report.jarSha256),
                -1, -1, -1, report.jarSha256);
    }
        return buildFresh(modJar, report);
    }

    private PreparedMod buildFresh(Path modJar, ReferenceReport report) throws IOException {
        VyneLogger.info("[DynamicEngine] Scanning " + modJar.getFileName()
            + " (" + report.jarSha256.substring(0, 12) + ")...");
        existence.markProvided(report.ownedClasses);

        Set<String> missingOwners_moj = new LinkedHashSet<>();
        for (String mojOwner : report.externalUsage.keySet()) {
            if (mojOwner != null && !mojOwner.startsWith("net/minecraft") && 
                (mojOwner.contains("Screen") || mojOwner.contains("Config"))) {
                continue;
            }
            String inter = MojmapTranslator.classInter(mojOwner);
            if (!existence.exists(inter)) missingOwners_moj.add(mojOwner);
        }

        Map<ReferenceRewriter.MojmapKey, ReferenceRewriter.ShimTarget> redirects =
            new HashMap<>();
        List<AutoShimSynthesizer.ShimMethod> missingShimMethods = new ArrayList<>();
        for (ReferenceReport.ClassUsage u : report.allExternal()) {
            if (missingOwners_moj.contains(u.owner)) continue;
            String interOwner = MojmapTranslator.classInter(u.owner);
            for (ReferenceReport.MethodRef mr : u.methods) {
                String interName = MojmapTranslator.methodNameInter(u.owner, mr.name, mr.desc);
                String interDesc = MojmapTranslator.descInter(mr.desc);
                if (isMethodResolvable(interOwner, interName, interDesc)) continue;

                if ("<init>".equals(mr.name) || "<clinit>".equals(mr.name)) continue;

                boolean wasInstance = mr.opcode != Opcodes.INVOKESTATIC;
                String shimDesc = wasInstance
                    ? prependReceiverObject(mr.desc)
                    : mr.desc;
                AutoShimSynthesizer.ShimMethod sm = new AutoShimSynthesizer.ShimMethod(
                    interOwner, interName, interDesc, shimDesc, wasInstance);
                missingShimMethods.add(sm);
                redirects.put(
                    ReferenceRewriter.MojmapKey.of(mr),
                    new ReferenceRewriter.ShimTarget(interOwner, interName, shimDesc));
    }
    }

        Map<String, byte[]> stubBytes = stubSynth.synthesize(missingOwners_moj, report);

        Map<String, byte[]> duplicatedStubs = new LinkedHashMap<>(stubBytes);
        for (Map.Entry<String, byte[]> e : stubBytes.entrySet()) {
            String internal = e.getKey();
            String remapped = VyneRemapperAlias.remapForgeToNeoforge(internal);
            if (remapped == null || remapped.equals(internal)) continue;
            if (duplicatedStubs.containsKey(remapped)) continue;
            try {
                byte[] renamed = VyneRemapperAlias.renameClassBytes(e.getValue(), remapped);
                duplicatedStubs.put(remapped, renamed);
    } catch (Throwable t) {
                VyneLogger.warn("[DynamicEngine] Failed to duplicate stub "
                    + internal + " → " + remapped + ": " + t);
    }
    }
        stubBytes = duplicatedStubs;

        Set<String> synthesizedClassSet = new LinkedHashSet<>(stubBytes.keySet());

        Map<String, AutoShimSynthesizer.ShimPlan> plans =
            AutoShimSynthesizer.planShims(missingShimMethods);
        Map<String, byte[]> shimBytes = new LinkedHashMap<>();
        int shimMethodCount = 0;
        for (AutoShimSynthesizer.ShimPlan plan : plans.values()) {
            byte[] bytes = shimSynth.synthesize(plan, synthesizedClassSet);
            shimBytes.put(AutoShimSynthesizer.shimInternalFor(plan.interOwner), bytes);
            shimMethodCount += plan.methods.size();
    }

        Map<String, byte[]> rewrites = new LinkedHashMap<>();
        int rewrittenClasses = 0;
        ClassLoader probeParent = existence.probeLoader();
        if (probeParent == null) {
            probeParent = MappingComposer.class.getClassLoader();
    }
        Path nestedRoot = cache.nestedJarCacheDir();
        List<Path> nestedJars = extractNestedJarPaths(modJar, nestedRoot);
        List<URL> probeUrls = new ArrayList<>(nestedJars.size() + 1);
        probeUrls.add(modJar.toUri().toURL());
        for (Path nested : nestedJars) {
            if (Files.isRegularFile(nested)) {
                probeUrls.add(nested.toUri().toURL());
    }
    }
        for (Path nested : nestedJars) {
            if (java.nio.file.Files.isRegularFile(nested)) {
                recordAllInterfacesFromJar(nested);
            }
        }

        try (URLClassLoader modProbe = new URLClassLoader(
                probeUrls.toArray(new URL[0]),
                probeParent)) {
            MappingComposer.pushClassByteProbeLoader(modProbe);
            try {
                try (ZipFile zf = new ZipFile(modJar.toFile())) {
                    Enumeration<? extends ZipEntry> entries = zf.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry e = entries.nextElement();
                        String name = e.getName();
                        if (!name.endsWith(".class")) continue;
                        String internal = name.substring(0, name.length() - ".class".length());
                        byte[] bytes;
                        try (InputStream is = zf.getInputStream(e)) {
                            bytes = is.readAllBytes();
    }
                        recordObservedInterfaces(bytes);

                        byte[] newBytes = bytes;
                        byte[] gs = GetServiceMultiLoaderPatch.patch(newBytes);
                        if (gs != newBytes) {
                            newBytes = gs;
    }
                        byte[] gc = GlitchCorePacketHandlerStubPatch.patch(newBytes);
                        if (gc != newBytes) {
                            newBytes = gc;
    }
                        byte[] reg = RegistrateStackWalkerStripPatch.patch(newBytes);
                        if (reg != newBytes) {
                            newBytes = reg;
    }
                        byte[] createRegistry = CreateSimpleRegistryNullValuePatch.patch(internal, newBytes);
                        if (createRegistry != newBytes) {
                            newBytes = createRegistry;
    }
                        byte[] registrateLenient = RegistrateLenientErrorsPatch.patch(internal, newBytes);
                        if (registrateLenient != newBytes) {
                            newBytes = registrateLenient;
    }
                        byte[] slGuarded = ServiceLoaderClassLoaderGuardPatch.patch(newBytes);
                        if (slGuarded != newBytes) {
                            newBytes = slGuarded;
    }
                        byte[] headerFixed = MojmapClassHeaderRemapper.remap(newBytes);
                        if (!Arrays.equals(headerFixed, newBytes)) {
                            newBytes = headerFixed;
    }
                        if (needsFullMinecraftRemapForDynamicRewrite(internal, newBytes)) {
                            byte[] mcRemap =
                                RemappingClassLoader.applyVyneRemapperToModBytesForDynamicEngine(newBytes);
                            if (!Arrays.equals(mcRemap, newBytes)) {
                                if (RemappingClassLoader.isStackMapRecomputeExcluded(internal)) {
                                    newBytes = mcRemap;
                                } else {
                                    newBytes = RemappingClassLoader.recomputeStackMapFramesForDynamicEngine(mcRemap);
                                }
    }
    }
                        byte[] trackRange = EntityTypeBuilderTrackingRangePatch.patch(newBytes);
                        if (!Arrays.equals(trackRange, newBytes)) {
                            newBytes = trackRange;
    }
                        byte[] foodEffect = FoodPropertiesSupplierEffectPatch.patch(newBytes);
                        if (!Arrays.equals(foodEffect, newBytes)) {
                            newBytes = foodEffect;
    }
                        byte[] nfCompat = RemappingClassLoader.applyNeoForgeFabricBytecodePatchesForDynamicEngine(newBytes);
                        if (!Arrays.equals(nfCompat, newBytes)) {
                            newBytes = nfCompat;
    }
                        byte[] inherited = RemappingClassLoader.patchInheritedFieldAccessForDynamicEngine(newBytes);
                        if (!Arrays.equals(inherited, newBytes)) {
                            newBytes = inherited;
    }
                        if (newBytes != bytes) {
                            rewrites.put(internal, newBytes);
                            rewrittenClasses++;
    }
    }
    }
    } finally {
                MappingComposer.popClassByteProbeLoader();
    }
    }

        cache.ensureDirForSha(report.jarSha256);
        EngineCache.writeClassJar(cache.stubsJar(report.jarSha256), stubBytes);
        EngineCache.writeClassJar(cache.shimsJar(report.jarSha256), shimBytes);
        EngineCache.rewriteJar(modJar, cache.rewrittenJar(report.jarSha256), rewrites);
        cache.writeManifest(report.jarSha256,
            "jar=" + modJar + "\n"
            + "sha256=" + report.jarSha256 + "\n"
            + "owned-classes=" + report.totalClassCount + "\n"
            + "missing-classes=" + missingOwners_moj.size() + "\n"
            + "extension-methods=" + missingShimMethods.size() + "\n"
            + "shim-classes=" + shimBytes.size() + "\n"
            + "rewritten-classes=" + rewrittenClasses + "\n");
        cache.markComplete(report.jarSha256);

        VyneLogger.success("[DynamicEngine] Prepared " + modJar.getFileName()
            + ": " + stubBytes.size() + " stub(s), "
            + shimBytes.size() + " shim class(es) holding "
            + shimMethodCount + " extension method(s), "
            + rewrittenClasses + " class(es) rewritten.");

        return new PreparedMod(modJar,
            cache.rewrittenJar(report.jarSha256),
            cache.stubsJar(report.jarSha256),
            cache.shimsJar(report.jarSha256),
            stubBytes.size(), shimMethodCount, rewrittenClasses, report.jarSha256);
    }

    private static void publishHierarchiesToMappingComposer(ReferenceReport report) {
        if (report == null || report.ownedClassHierarchy.isEmpty()) return;
        for (Map.Entry<String, String[]> e : report.ownedClassHierarchy.entrySet()) {
            String[] arr = e.getValue();
            if (arr == null || arr.length == 0) continue;
            String superName = arr[0];
            String[] ifaces = arr.length > 1
                ? java.util.Arrays.copyOfRange(arr, 1, arr.length)
                : null;
            try {
                dev.stolym.vyne.mappings.MappingComposer.ComposedMappings
                    .recordClassHierarchy(e.getKey(), superName, ifaces);
    } catch (Throwable ignored) {}
    }
    }

    private boolean isMethodResolvable(String interOwner, String interName, String interDesc) {
        try {
            Class<?> cls = Class.forName(interOwner.replace('/', '.'), false,
                existence.probeLoader());
            return hasMatchingMethod(cls, interName, interDesc);
        } catch (Throwable ignored) {
            return !isVyneOwnedOwner(interOwner);
        }
    }

    private static boolean isVyneOwnedOwner(String interOwner) {
        return interOwner.startsWith("net/neoforged/")
            || interOwner.startsWith("net/minecraftforge/")
            || interOwner.startsWith("dev/stolym/vyne/");
    }

    private static boolean hasMatchingMethod(Class<?> cls, String name, String desc) {
        java.util.ArrayDeque<Class<?>> queue = new java.util.ArrayDeque<>();
        queue.add(cls);
        Set<Class<?>> seen = new HashSet<>();
        while (!queue.isEmpty()) {
            Class<?> c = queue.poll();
            if (c == null || !seen.add(c)) continue;
            if ("<init>".equals(name)) {
                for (java.lang.reflect.Constructor<?> ctor : c.getDeclaredConstructors()) {
                    if (descFor(ctor).equals(desc)) return true;
    }
    } else {
                for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                    if (!m.getName().equals(name)) continue;
                    if (descFor(m).equals(desc)) return true;
    }
    }
            if (c.getSuperclass() != null) queue.add(c.getSuperclass());
            for (Class<?> itf : c.getInterfaces()) queue.add(itf);
    }

        if (!"<init>".equals(name) && !"<clinit>".equals(name)) {
            for (java.lang.reflect.Method m : Object.class.getDeclaredMethods()) {
                if (!m.getName().equals(name)) continue;
                if (descFor(m).equals(desc)) return true;
            }
        }
        return false;
    }

    private static String descFor(java.lang.reflect.Constructor<?> ctor) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> p : ctor.getParameterTypes()) sb.append(descOf(p));
        sb.append(")V");
        return sb.toString();
    }

    private static String descFor(java.lang.reflect.Method m) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> p : m.getParameterTypes()) sb.append(descOf(p));
        sb.append(")").append(descOf(m.getReturnType()));
        return sb.toString();
    }

    private static String descOf(Class<?> c) {
        if (c == void.class) return "V";
        if (c == boolean.class) return "Z";
        if (c == byte.class) return "B";
        if (c == char.class) return "C";
        if (c == short.class) return "S";
        if (c == int.class) return "I";
        if (c == long.class) return "J";
        if (c == float.class) return "F";
        if (c == double.class) return "D";
        if (c.isArray()) return "[" + descOf(c.getComponentType());
        return "L" + c.getName().replace('.', '/') + ";";
    }

    private static String prependReceiverObject(String desc) {
        int open = desc.indexOf('(');
        int close = desc.indexOf(')');
        return "(Ljava/lang/Object;"
            + desc.substring(open + 1, close + 1)
            + desc.substring(close + 1);
    }

    public BridgeCatalog catalog() { return catalog; }
    public ClasspathExistence existence() { return existence; }

    @SuppressWarnings("unused")
    private static String rawReturn(String desc) {
        return Type.getReturnType(desc).getDescriptor();
    }
}
