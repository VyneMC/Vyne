/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.registries;

import dev.stolym.vyne.registry.RegistrationDiagnostics;
import dev.stolym.vyne.util.VyneLogger;
import dev.stolym.vyne.fluid.VyneFluidTypeRegistry;
import dev.stolym.vyne.fluid.VyneFluidRegistry;
import dev.stolym.vyne.registry.RegistryMirrorUtil;
import dev.stolym.vyne.registry.VyneGenericRegistry;
import dev.stolym.vyne.registry.VyneRegistryInjector;
import dev.stolym.vyne.util.ReflectionHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import dev.stolym.vyne.vcl.VCLFieldPopulator;
import dev.stolym.vyne.vcl.VCLManager;

import java.util.*;
import java.util.function.Supplier;

public class DeferredRegister<T> {
    private final Object registryKey;
    private final String modId;
    private final List<DeferredHolder<T>> entries = new ArrayList<>();
    private boolean seenRegisterEvent = false;
    private boolean isRegisteredToBus = false;

    private static final List<Object> ALL_REGISTERS_GLOBAL;
    static {
        synchronized (System.getProperties()) {
            List<Object> existing = (List<Object>) System.getProperties().get("vyne.all_registers");
            if (existing == null) {
                existing = java.util.Collections.synchronizedList(new ArrayList<>());
                System.getProperties().put("vyne.all_registers", existing);
            }
            ALL_REGISTERS_GLOBAL = existing;
        }
    }

    protected DeferredRegister(Object registryKey, String modId) {
        this.registryKey = registryKey;
        this.modId = modId;
        ALL_REGISTERS_GLOBAL.add(this);
        VyneLogger.info("[DeferredRegister] Created register for " + modId + " -> " + ReflectionHelper.extractRegistryKeyLocation(registryKey) + " (total: " + ALL_REGISTERS_GLOBAL.size() + ")");
        this.isRegisteredToBus = false;
    }

    public DeferredRegister(net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<T>> registryKey, String modId) {
        this((Object) registryKey, modId);
    }

    public static List<Object> getAllRegisters() {
        return ALL_REGISTERS_GLOBAL;
    }

    public Object getRegistryKey() {
        return registryKey;
    }

    public String getNamespace() {
        return modId;
    }

    public Collection<DeferredHolder<T>> getEntries() {
        return Collections.unmodifiableCollection(entries);
    }

    public static <T> DeferredRegister<T> create(net.minecraft.core.Registry<T> key, String modId) {
        return new DeferredRegister<>(key, modId);
    }

    public static <T> DeferredRegister<T> create(net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<T>> key, String modId) {
        return new DeferredRegister<>(key, modId);
    }

    public static <B> DeferredRegister<B> create(net.minecraft.resources.ResourceLocation key, String modId) {
        return new DeferredRegister<>(key, modId);
    }

    public static <T> DeferredRegister<T> create(IForgeRegistry<T> key, String modId) {
        return new DeferredRegister<>(key, modId);
    }

    public static <T> DeferredRegister<T> create(RegistryHolder<T> key, String modId) {
        return key != null ? key : new RegistryHolder<>(null, modId);
    }

    public static <T> RegistryHolder<T> createRegistryHolder(Object key, String modId) {
        return new RegistryHolder<>(key, modId);
    }

    public static DataComponents createDataComponents(
            net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<?>> registryKey,
            String modId) {
        return new DataComponents(registryKey, modId);
    }

    public static final class DataComponents extends DeferredRegister<Object> {
        private DataComponents(net.minecraft.resources.ResourceKey<?> registryKey, String modId) {
            super((Object) registryKey, modId);
        }
    }

    private final java.util.Map<DeferredHolder<T>, Supplier<? extends T>> holderSuppliers =
        java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());

    private int blockRegisterPass;
    private final java.util.Set<ResourceLocation> vyneSessionRegisteredBlockIds = new java.util.HashSet<>();

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <I extends T> DeferredHolder<T> register(String name, Supplier<? extends I> supplier) {
        if (seenRegisterEvent) throw new IllegalStateException("Registering after event!");
        String path = normalizeEntryPath(name);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(modId.toLowerCase(java.util.Locale.ROOT), path);

        DeferredHolder<T> holder = callSubclassCreateHolder(registryKey, id);
        if (holder == null) {
            holder = (DeferredHolder<T>) new RegistryObject<>(registryKey, modId.toLowerCase(java.util.Locale.ROOT), path, (Supplier<I>) supplier);
        } else {
            holderSuppliers.put(holder, (Supplier<? extends T>) supplier);
        }
        entries.add(holder);
        return holder;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private DeferredHolder<T> callSubclassCreateHolder(Object rawKey, ResourceLocation id) {
        if (getClass() == RegistryHolder.class) {
            return null;
        }

        java.util.List<java.lang.reflect.Method> candidates = new java.util.ArrayList<>();
        for (Class<?> c = getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            java.lang.reflect.Method[] declared;
            try {
                declared = c.getDeclaredMethods();
            } catch (Throwable t) {
                VyneLogger.info("[DeferredRegister] getDeclaredMethods failed on "
                    + c.getName() + ": " + t.getClass().getSimpleName() + ": " + t.getMessage());
                continue;
            }
            for (java.lang.reflect.Method m : declared) {
                if (!m.getName().equals("createHolder")) continue;
                if (m.getParameterCount() != 2) continue;
                candidates.add(m);
            }
        }
        if (candidates.isEmpty()) {
            VyneLogger.info("[DeferredRegister] no createHolder candidates on "
                + getClass().getName() + " for id=" + id);
            return null;
        }

        candidates.sort((a, b) -> {
            int ab = a.isBridge() ? 1 : 0;
            int bb = b.isBridge() ? 1 : 0;
            return Integer.compare(ab, bb);
        });

        Throwable lastFailure = null;
        for (java.lang.reflect.Method m : candidates) {
            try {
                m.setAccessible(true);
                Object p0 = adaptArgToParam(rawKey, m.getParameterTypes()[0]);
                Object p1 = adaptArgToParam(id, m.getParameterTypes()[1]);
                Object result = m.invoke(this, p0, p1);
                if (result instanceof DeferredHolder<?> dh) {
                    return (DeferredHolder<T>) dh;
                }
                if (result != null) {
                    VyneLogger.info("[DeferredRegister] createHolder " + describeMethod(m)
                        + " returned non-DeferredHolder " + result.getClass().getName()
                        + " for id=" + id);
                }
            } catch (java.lang.reflect.InvocationTargetException ite) {
                Throwable cause = ite.getTargetException() != null ? ite.getTargetException() : ite;
                lastFailure = cause;
                VyneLogger.info("[DeferredRegister] createHolder " + describeMethod(m)
                    + " threw for " + id + ": "
                    + cause.getClass().getSimpleName() + ": " + cause.getMessage());
            } catch (Throwable t) {
                lastFailure = t;
                VyneLogger.info("[DeferredRegister] createHolder " + describeMethod(m)
                    + " reflection failed for " + id + ": "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
        if (lastFailure != null) {
            VyneLogger.info("[DeferredRegister] all createHolder candidates failed for "
                + id + " (" + getClass().getName() + "), falling back to RegistryObject");
        }
        return null;
    }

    private static Object adaptArgToParam(Object value, Class<?> expected) {
        if (value == null) return null;
        if (expected == null || expected.isInstance(value)) return value;

        String expectedName = expected.getName();
        boolean expectsResourceLocation = expectedName.endsWith(".ResourceLocation")
            || expectedName.endsWith(".class_2960");
        if (expectsResourceLocation) {
            Object adapted = dev.stolym.vyne.util.ReflectionHelper.adaptResourceLocation(
                value, expected);
            return adapted;
        }

        boolean expectsResourceKey = expectedName.endsWith(".ResourceKey")
            || expectedName.endsWith(".class_5321");
        if (expectsResourceKey) {
            Object adapted = adaptResourceKey(value, expected);
            return adapted;
        }

        return value;
    }

    private static Object adaptResourceKey(Object src, Class<?> expected) {
        if (src == null || expected == null) return null;
        if (expected.isInstance(src)) return src;

        Object registryLoc = firstNonNullCall(src, "registry", "method_41185", "method_29177");
        Object elementLoc = firstNonNullCall(src, "location", "method_29184");
        if (registryLoc == null && elementLoc == null) return null;
        try {
            String rlName;
            if (expected.getName().endsWith(".class_5321")) {
                rlName = "net.minecraft.class_2960";
            } else {
                rlName = "net.minecraft.resources.ResourceLocation";
            }
            Class<?> rlClass = Class.forName(rlName, true, expected.getClassLoader());
            Object adaptedRegistry = registryLoc != null
                ? dev.stolym.vyne.util.ReflectionHelper.adaptResourceLocation(registryLoc, rlClass)
                : null;
            Object adaptedElement = elementLoc != null
                ? dev.stolym.vyne.util.ReflectionHelper.adaptResourceLocation(elementLoc, rlClass)
                : null;

            if (adaptedRegistry != null && adaptedElement != null) {
                java.lang.reflect.Method createRoot = null;
                java.lang.reflect.Method createChild = null;
                for (java.lang.reflect.Method m : expected.getMethods()) {
                    if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                    if (m.getParameterCount() == 1 && rlClass.isAssignableFrom(m.getParameterTypes()[0])
                            && expected.isAssignableFrom(m.getReturnType())) {
                        createRoot = m;
                    } else if (m.getParameterCount() == 2
                            && expected.isAssignableFrom(m.getParameterTypes()[0])
                            && rlClass.isAssignableFrom(m.getParameterTypes()[1])
                            && expected.isAssignableFrom(m.getReturnType())) {
                        createChild = m;
                    }
                }
                if (createRoot != null && createChild != null) {
                    Object rootKey = createRoot.invoke(null, adaptedRegistry);
                    return createChild.invoke(null, rootKey, adaptedElement);
                }
            }
            if (adaptedElement != null) {
                for (java.lang.reflect.Method m : expected.getMethods()) {
                    if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                    if (m.getParameterCount() != 1) continue;
                    if (!rlClass.isAssignableFrom(m.getParameterTypes()[0])) continue;
                    if (!expected.isAssignableFrom(m.getReturnType())) continue;
                    return m.invoke(null, adaptedElement);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Object firstNonNullCall(Object target, String... names) {
        for (String name : names) {
            Object value = dev.stolym.vyne.util.ReflectionHelper.call(target, name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String describeMethod(java.lang.reflect.Method m) {
        StringBuilder sb = new StringBuilder(m.getDeclaringClass().getSimpleName())
            .append('.').append(m.getName()).append('(');
        Class<?>[] params = m.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(params[i].getSimpleName());
        }
        sb.append(')');
        if (m.isBridge()) sb.append(" [bridge]");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public <I extends T> RegistryObject<I> registerAsObject(String name, Supplier<? extends I> supplier) {
        DeferredHolder<T> h = register(name, supplier);
        return h instanceof RegistryObject<?> ro ? (RegistryObject<I>) ro : null;
    }

    @SuppressWarnings("unchecked")
    public <I extends T> DeferredHolder<T> register(String name,
            java.util.function.Function<ResourceLocation, ? extends I> factory) {
        String path = normalizeEntryPath(name);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(modId.toLowerCase(java.util.Locale.ROOT), path);
        return register(path, (Supplier<I>) () -> factory.apply(id));
    }

    @SuppressWarnings("rawtypes")
    protected DeferredHolder createHolder(net.minecraft.resources.ResourceKey registryKey,
                                          ResourceLocation id) {
        return null;
    }

    private final Set<Object> registeredBuses = Collections.synchronizedSet(Collections.newSetFromMap(new java.util.IdentityHashMap<>()));

    public void register(IEventBus bus) {
        if (!registeredBuses.add(bus)) return;
        bus.addListener(this::onRegister);
    }

    public void register(net.neoforged.neoforge.eventbus.api.IEventBus bus) {
        if (bus instanceof IEventBus) {
            register((IEventBus) bus);
        }
    }

    public void register(Object bus) {
        if (bus instanceof IEventBus) {
            register((IEventBus) bus);
        }
    }

    public void addAlias(ResourceLocation from, ResourceLocation to) {
        if (from != null && to != null) {
            VyneLogger.debug("[DeferredRegister] addAlias ignored (" + from + " → " + to + ")");
        }
    }

    private static boolean registryLocationsMatch(String a, String b) {
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;
        String na = normalizeRegistryPath(a);
        String nb = normalizeRegistryPath(b);
        return na.equals(nb);
    }

    private static String normalizeRegistryPath(String s) {
        int colon = s.indexOf(':');
        if (colon < 0) return s;
        String ns = s.substring(0, colon);
        String path = s.substring(colon + 1).replace('\\', '/');
        return ns + ":" + path;     
    }

    private static String normalizeEntryPath(String name) {
        if (name == null) return null;
        return name.toLowerCase(java.util.Locale.ROOT);
    }

    private void onRegister(Object eventObj) {
        if (eventObj == null) {
            return;
        }

        String eventClassName = eventObj.getClass().getName();
        if (!eventClassName.contains("RegisterEvent") && !eventClassName.contains("Register$")) {
            return;
        }

        Object eventKey = ReflectionHelper.call(eventObj, "getRegistryKey");
        if (eventKey == null) {
            try {
                java.lang.reflect.Field f = eventObj.getClass().getDeclaredField("registryKey");
                f.setAccessible(true);
                eventKey = f.get(eventObj);
            } catch (Throwable ignored) {
            }
        }

        String myLocStr = ReflectionHelper.extractRegistryKeyLocation(registryKey);
        String eventLocStr = ReflectionHelper.extractRegistryKeyLocation(eventKey);

        VyneLogger.debug("[DeferredRegister] onRegister fired for modId=" + modId + " myLoc=" + myLocStr + " eventLoc=" + eventLocStr + " entries=" + entries.size());

        if (myLocStr == null || eventLocStr == null
                || !ReflectionHelper.registryKeyLocationsMatch(myLocStr, eventLocStr)) {
            VyneLogger.debug("[DeferredRegister] onRegister SKIP: myLoc=" + myLocStr + " eventLoc=" + eventLocStr);
            return;
        }

        this.seenRegisterEvent = true;
        String myPath = myLocStr;
        boolean isBlockRegistry = "minecraft:block".equals(myPath);
        if (isBlockRegistry) {
            blockRegisterPass++;
            if (blockRegisterPass == 1) {
                vyneSessionRegisteredBlockIds.clear();
            }
        }
        boolean emitDeferredStats = !isBlockRegistry || blockRegisterPass >= 2;

        List<ResourceLocation> expectedIds = entries.stream().map(DeferredHolder::getId).toList();
        List<ResourceLocation> registeredIds = new ArrayList<>();
        int count = 0;
        int skippedNull = 0;
        List<ResourceLocation> skippedIds = new ArrayList<>();
        List<DeferredHolder<T>> retryCandidates = new ArrayList<>();

        ClassLoader knotForSkip = VCLManager.getCanonicalKnotLoader();
        if (knotForSkip == null) {
            knotForSkip = Thread.currentThread().getContextClassLoader();
        }

        for (DeferredHolder<T> holder : entries) {
            if (isBlockRegistry && vyneSessionRegisteredBlockIds.contains(holder.getId())) {
                continue;
            }
            if ("minecraft:item".equals(myPath) && knotForSkip != null) {
                String idStr = holder.getId().toString();
                boolean itemLive = VyneRegistryInjector.liveRegistryHasId(knotForSkip, myPath, idStr);
                boolean blockLive = VyneRegistryInjector.liveRegistryHasId(knotForSkip, "minecraft:block", idStr);
                if (itemLive && blockLive) {
                    registeredIds.add(holder.getId());
                    continue;
                }
                if (itemLive && !blockLive) {
                    mirrorBlockFromItemIfMissing(knotForSkip, holder);
                    registeredIds.add(holder.getId());
                    continue;
                }
            }
            T value = resolveHolderValue(holder);
            if (value == null && isBlockRegistry && knotForSkip != null) {
                Object bridged = RegistryMirrorUtil.tryResolveLinkedBlockFromItemRegistryBestEffort(
                    knotForSkip, holder.getId().toString());
                if (bridged != null) {
                    @SuppressWarnings("unchecked")
                    T asBlock = (T) bridged;
                    value = asBlock;
                }
            }
            if (value == null) {
                skippedNull++;
                skippedIds.add(holder.getId());
                retryCandidates.add(holder);
                VyneLogger.debug("[DeferredRegister] [" + modId + "] deferred supplier null (will retry)"
                    + " registry=" + myPath + " id=" + holder.getId());
                continue;
            }

            if (registerOne(eventObj, myPath, holder, value)) {
                count++;
                registeredIds.add(holder.getId());
                holder.bind(false);
                if (isBlockRegistry) {
                    vyneSessionRegisteredBlockIds.add(holder.getId());
                }
            }
        }

        VCLFieldPopulator.retryPending(Thread.currentThread().getContextClassLoader());
        if (!retryCandidates.isEmpty()) {
            int recovered = 0;
            Iterator<DeferredHolder<T>> it = retryCandidates.iterator();
            while (it.hasNext()) {
                DeferredHolder<T> holder = it.next();
                if (isBlockRegistry && vyneSessionRegisteredBlockIds.contains(holder.getId())) {
                    continue;
                }
                if ("minecraft:item".equals(myPath) && knotForSkip != null) {
                    String idStr = holder.getId().toString();
                    boolean itemLive = VyneRegistryInjector.liveRegistryHasId(knotForSkip, myPath, idStr);
                    boolean blockLive = VyneRegistryInjector.liveRegistryHasId(knotForSkip, "minecraft:block", idStr);
                    if (itemLive && blockLive) {
                        registeredIds.add(holder.getId());
                        continue;
                    }
                    if (itemLive && !blockLive) {
                        mirrorBlockFromItemIfMissing(knotForSkip, holder);
                        registeredIds.add(holder.getId());
                        continue;
                    }
                }
                T value = resolveHolderValue(holder);
                if (value == null && isBlockRegistry && knotForSkip != null) {
                    Object bridged = RegistryMirrorUtil.tryResolveLinkedBlockFromItemRegistryBestEffort(
                        knotForSkip, holder.getId().toString());
                    if (bridged != null) {
                        @SuppressWarnings("unchecked")
                        T asBlock = (T) bridged;
                        value = asBlock;
                    }
                }
                if (value == null) {
                    continue;
                }
                if (registerOne(eventObj, myPath, holder, value)) {
                    recovered++;
                    count++;
                    holder.bind(false);
                    skippedNull = Math.max(0, skippedNull - 1);
                    registeredIds.add(holder.getId());
                    skippedIds.remove(holder.getId());
                    if (isBlockRegistry) {
                        vyneSessionRegisteredBlockIds.add(holder.getId());
                    }
                }
            }
            if (recovered > 0) {
                VyneLogger.info("[DeferredRegister] [" + modId + "] " + myPath
                    + ": recovered " + recovered + " entr(y/ies) on retry.");
            }
        }

        if (emitDeferredStats) {
            if (isBlockRegistry) {
                registeredIds = new ArrayList<>(vyneSessionRegisteredBlockIds);
                count = vyneSessionRegisteredBlockIds.size();
                skippedIds.clear();
                skippedNull = 0;
                for (DeferredHolder<T> holder : entries) {
                    if (!vyneSessionRegisteredBlockIds.contains(holder.getId())
                            && resolveHolderValue(holder) == null) {
                        skippedNull++;
                        skippedIds.add(holder.getId());
                    }
                }
            }
            RegistrationDiagnostics.recordDeferredRegisterBatch(modId, myPath, count, skippedNull, skippedIds);
            RegistrationDiagnostics.recordExpectedVsRegistered(modId, myPath, expectedIds, registeredIds);
            if (count > 0 || skippedNull > 0) {
                VyneLogger.info("[DeferredRegister] [" + modId + "] " + myPath + ": applied " + count
                    + " entr(y/ies), skipped " + skippedNull + " null supplier(s).");
                if (skippedNull > 0) {
                    String sample = skippedIds.stream().limit(10).map(Object::toString)
                        .collect(java.util.stream.Collectors.joining(", "));
                    if (skippedIds.size() > 10) {
                        sample += " …";
                    }
                    VyneLogger.warn("[DeferredRegister] [" + modId + "] unresolved ids for " + myPath
                        + ": " + sample);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private T resolveHolderValue(DeferredHolder<T> holder) {
        Supplier<? extends T> extSupplier = holderSuppliers.get(holder);
        if (extSupplier != null) {
            try {
                T v = extSupplier.get();
                if (v != null) return v;
                else dev.stolym.vyne.registry.Diagnostic.log("ExtSupplier returned null for " + holder.getId());
            } catch (Throwable t) {
                VyneLogger.debug("[DeferredRegister] external supplier failed for " + holder.getId() + ": " + t.getMessage());
            }
        }
        T v = holder.get();
        if (v == null) {
            dev.stolym.vyne.registry.Diagnostic.log("Holder.get() returned null for " + holder.getId());
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    public static <T> T resolveHolderValueForRecovery(DeferredRegister<T> owner, DeferredHolder<T> holder) {
        if (owner == null || holder == null) {
            return null;
        }
        return owner.resolveHolderValue(holder);
    }

    private void mirrorBlockFromItemIfMissing(ClassLoader knot, DeferredHolder<T> holder) {
        if (knot == null || holder == null) {
            return;
        }
        String idStr = holder.getId().toString();
        T v = resolveHolderValue(holder);
        if (v != null) {
            RegistryMirrorUtil.mirrorBlockItemToBlockRegistryIfNeeded("minecraft:item", holder.getId(), v);
        }
        if (!VyneRegistryInjector.liveRegistryHasId(knot, "minecraft:block", idStr)) {
            Object b = RegistryMirrorUtil.tryResolveBlockForBlockIdFromItemRegistry(knot, idStr);
            if (b != null) {
                VyneGenericRegistry.register("minecraft:block", idStr, b);
                VyneRegistryInjector.inject("minecraft:block", idStr, b);
            }
        }
        try {
            VyneRegistryInjector.syncMissingGenericEntriesToLiveRegistry(knot, "minecraft:block");
        } catch (Throwable ignored) {
        }
    }

    private boolean registerOne(Object eventObj, String myPath, DeferredHolder<T> holder, T value) {
        VyneLogger.debug("[DeferredRegister] [" + modId + "] Registered " + myPath + ": " + holder.getId());

        if (value instanceof net.neoforged.neoforge.fluids.FluidType) {
            VyneLogger.debug("[DeferredRegister] Registering as FluidType: " + holder.getId());
            VyneFluidTypeRegistry.register(holder.getId(), value);
        } else if (myPath.contains("fluid")) {
            VyneLogger.debug("[DeferredRegister] Attempting to record fluid: " + holder.getId() + " path=" + myPath);
            VyneFluidRegistry.record(holder.getId().toString(), value);
        }

        VyneGenericRegistry.register(myPath, holder.getId(), value);
        RegistryMirrorUtil.mirrorBlockItemToBlockRegistryIfNeeded(myPath, holder.getId(), value);
        VyneRegistryInjector.inject(myPath, holder.getId(), value);

        try {
            java.lang.reflect.Method reg = null;
            for (java.lang.reflect.Method m : eventObj.getClass().getMethods()) {
                if (!m.getName().equals("register") || m.getParameterCount() != 3) continue;
                Class<?> p1 = m.getParameterTypes()[1];
                String p1n = p1.getName();
                if (p1 == ResourceLocation.class
                        || "net.minecraft.resources.ResourceLocation".equals(p1n)
                        || p1n.endsWith(".class_2960")) {
                    reg = m;
                    break;
                }
            }
            if (reg == null) {
                for (java.lang.reflect.Method m : eventObj.getClass().getMethods()) {
                    if (!m.getName().equals("register") || m.getParameterCount() != 3) continue;
                    reg = m;
                    break;
                }
            }
            if (reg != null) {
                T captured = value;
                reg.invoke(eventObj, registryKey, holder.getId(), (Supplier<?>) () -> captured);
            } else {
                VyneLogger.debug("[DeferredRegister] no register() on event, skipping reflect call");
            }
        } catch (Throwable t) {
            Throwable root = t instanceof java.lang.reflect.InvocationTargetException ite
                && ite.getCause() != null ? ite.getCause() : t;
            VyneLogger.error("[DeferredRegister] Unexpected error while registering entry "
                + holder.getId() + " to registry " + myPath
                + " via " + eventObj.getClass().getName()
                + " valueType=" + (value != null ? value.getClass().getName() : "null"), root);
            return false;
        }
        return verifyLiveEntryForBuiltInDatapacks(myPath, holder, value);
    }

    private boolean verifyLiveEntryForBuiltInDatapacks(String myPath, DeferredHolder<T> holder, T value) {
        if (!"minecraft:block".equals(myPath) && !"minecraft:item".equals(myPath)) {
            return true;
        }
        ClassLoader knot = VCLManager.getCanonicalKnotLoader();
        if (knot == null) {
            knot = Thread.currentThread().getContextClassLoader();
        }
        String idStr = holder.getId().toString();
        if (VyneRegistryInjector.liveRegistryHasId(knot, myPath, idStr)) {
            return true;
        }
        try {
            VyneRegistryInjector.syncMissingGenericEntriesToLiveRegistry(knot, myPath);
        } catch (Throwable ignored) {
        }
        if (VyneRegistryInjector.liveRegistryHasId(knot, myPath, idStr)) {
            return true;
        }
        try {
            Object live = VyneRegistryInjector.resolveRegistryForPath(knot, myPath);
            if (live != null) {
                Object rl = ReflectionHelper.adaptResourceLocation(holder.getId(),
                    Class.forName(
                        dev.stolym.vyne.mappings.MappingRegistry.isReady()
                            ? dev.stolym.vyne.mappings.MappingRegistry.get()
                                .mapClass("net/minecraft/resources/ResourceLocation").replace('/', '.')
                            : "net.minecraft.class_2960",
                        true, knot));
                if (rl != null) {
                    VyneRegistryInjector.injectIntoLiveRegistry(live, rl, value);
                }
            }
        } catch (Throwable ignored) {
        }
        if (VyneRegistryInjector.liveRegistryHasId(knot, myPath, idStr)) {
            return true;
        }
        VyneLogger.debug("[DeferredRegister] [" + modId + "] live registry still missing " + myPath
            + " id=" + idStr + " after sync — will retry on a later RegisterEvent pass.");
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object tryResolveEntryFromDeferredRegisters(String fullId) {
        if (fullId == null || fullId.indexOf(':') < 0) {
            return null;
        }
        ClassLoader knot = VCLManager.getCanonicalKnotLoader();
        if (knot == null) {
            knot = Thread.currentThread().getContextClassLoader();
        }

        java.util.Set<Object> regs = getAllRegistersAcrossLoaders(knot);
        for (Object drObj : regs) {
            if (!(drObj instanceof DeferredRegister<?> dr)) {
                try {
                    java.lang.reflect.Method getEntries = drObj.getClass().getMethod("getEntries");
                    getEntries.setAccessible(true);
                    java.util.Collection<?> entries = (java.util.Collection<?>) getEntries.invoke(drObj);
                    for (Object holder : entries) {
                        java.lang.reflect.Method getId = holder.getClass().getMethod("getId");
                        getId.setAccessible(true);
                        Object idObj = getId.invoke(holder);
                        if (fullId.equals(idObj.toString())) {
                            java.lang.reflect.Method resolve = drObj.getClass().getDeclaredMethod("resolveHolderValue", Class.forName("net.neoforged.neoforge.registries.DeferredHolder", true, drObj.getClass().getClassLoader()));
                            resolve.setAccessible(true);
                            return resolve.invoke(drObj, holder);
                        }
                    }
                } catch (Throwable ignored) {}
                continue;
            }

            for (DeferredHolder<?> holder : dr.getEntries()) {
                if (!fullId.equals(holder.getId().toString())) {
                    continue;
                }
                try {
                    return dr.resolveHolderValue((DeferredHolder) holder);
                } catch (Throwable ignored) {
                }
            }
        }
        return VyneRegistryInjector.tryResolveEntryFromTrackedDeferredRegisters(fullId);
    }

    public static void fillAllRegistryGapsIntoVyneGeneric() {
        ClassLoader k = VCLManager.getCanonicalKnotLoader();
        if (k == null) {
            k = Thread.currentThread().getContextClassLoader();
        }
        java.util.Set<Object> regs = getAllRegistersAcrossLoaders(k);
        System.err.println("[Vyne-Recovery] fillAllRegistryGapsIntoVyneGeneric: checking " + regs.size() + " registers");
        for (Object dr : regs) {
            if (dr instanceof DeferredRegister<?> sdr) {
                sdr.fillBlockRegistryGapsIntoVyneGenericOne();
            } else {
                fillBlockRegistryGapsIntoVyneGenericReflect(dr, k);
            }
        }
        VyneRegistryInjector.fillAllRegistryGapsForTrackedForeign(k);
    }

    private static java.util.Set<Object> getAllRegistersAcrossLoaders(ClassLoader knot) {
        java.util.Set<Object> all = new java.util.HashSet<>(ALL_REGISTERS_GLOBAL);
        if (knot != null && knot != DeferredRegister.class.getClassLoader()) {
            try {
                Class<?> otherDr = findLoadedDeferredRegisterOn(knot);
                if (otherDr != null) {
                    java.lang.reflect.Field f = null;
                    try {
                        f = otherDr.getDeclaredField("ALL_REGISTERS_GLOBAL");
                    } catch (NoSuchFieldException e) {
                        f = otherDr.getDeclaredField("ALL_REGISTERS");
                    }
                    f.setAccessible(true);
                    Object otherSet = f.get(null);
                    if (otherSet instanceof java.util.Collection<?> col) {
                        all.addAll(col);
                        VyneLogger.info("[DeferredRegister] Found " + col.size() + " registers in other classloader: " + knot);
                    }
                }
            } catch (Throwable t) {
                VyneLogger.info("[DeferredRegister] Failed to get registers from " + knot + ": " + t);
            }
        }
        return all;
    }

    private static Class<?> findLoadedDeferredRegisterOn(ClassLoader knot) {
        try {
            java.lang.reflect.Method findLoadedClassMethod =
                ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
            findLoadedClassMethod.setAccessible(true);
            return (Class<?>) findLoadedClassMethod.invoke(knot,
                "net.neoforged.neoforge.registries.DeferredRegister");
        } catch (java.lang.reflect.InaccessibleObjectException
                | IllegalAccessException
                | SecurityException inaccessible) {
            try {
                return Class.forName(
                    "net.neoforged.neoforge.registries.DeferredRegister", false, knot);
            } catch (ClassNotFoundException notLoadedYet) {
                return null;
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void fillBlockRegistryGapsIntoVyneGenericReflect(Object dr, ClassLoader knot) {
        try {
            java.lang.reflect.Method m = dr.getClass().getDeclaredMethod("fillBlockRegistryGapsIntoVyneGenericOne");
            m.setAccessible(true);
            m.invoke(dr);
        } catch (Throwable ignored) {}
    }

    @SuppressWarnings("unchecked")
    private void fillBlockRegistryGapsIntoVyneGenericOne() {
        String path = ReflectionHelper.extractRegistryKeyLocation(registryKey);
        if (path == null) {
            return;
        }
        if (!isDatapackCriticalRegistry(path)) {
            return;
        }
        boolean isBlock = "minecraft:block".equals(path);
        int resolved = 0;
        for (DeferredHolder<T> holder : entries) {
            T v = resolveHolderValue(holder);
            if (v == null && isBlock) {
                ClassLoader k = VCLManager.getCanonicalKnotLoader();
                if (k == null) {
                    k = Thread.currentThread().getContextClassLoader();
                }
                Object bridged = RegistryMirrorUtil.tryResolveBlockForBlockIdFromItemRegistry(
                    k, holder.getId().toString());
                if (bridged != null) {
                    v = (T) bridged;
                }
            }
            if (v == null) {
                continue;
            }
            String id = holder.getId().toString();
            if (VyneGenericRegistry.getLenient(path, id) != null) {
                continue;
            }
            VyneGenericRegistry.register(path, holder.getId(), v);
            VyneRegistryInjector.inject(path, holder.getId(), v);
            resolved++;
        }
        if (resolved > 0) {
            System.err.println("[Vyne-Recovery] Resolved " + resolved + " entries for " + path + " from DeferredRegister (" + modId + ")");
        }
        ClassLoader k = VCLManager.getCanonicalKnotLoader();
        if (k == null) {
            k = Thread.currentThread().getContextClassLoader();
        }
        if (k != null) {
            try {
                VyneRegistryInjector.syncMissingGenericEntriesToLiveRegistry(k, path);
            } catch (Throwable ignored) {
            }
        }
    }

    public static boolean isDatapackCriticalRegistry(String path) {
        return "minecraft:block".equals(path)
            || "minecraft:item".equals(path)
            || "minecraft:recipe_serializer".equals(path)
            || "minecraft:recipe_type".equals(path)
            || "minecraft:worldgen/feature".equals(path)
            || "minecraft:worldgen/configured_feature".equals(path)
            || "minecraft:worldgen/placed_feature".equals(path)
            || "minecraft:worldgen/biome".equals(path)
            || "minecraft:worldgen/structure".equals(path)
            || "minecraft:worldgen/structure_set".equals(path)
            || "minecraft:worldgen/template_pool".equals(path)
            || "minecraft:worldgen/placement_modifier_type".equals(path);
    }

    public static class RegistryHolder<T> extends DeferredRegister<T> {
        protected RegistryHolder(Object registryKey, String modId) {
            super(registryKey, modId);
        }
    }
}
