/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.stolym.vyne.registry.RegistryDatapackLookupFallback;
import dev.stolym.vyne.util.ReflectionHelper;
import dev.stolym.vyne.vcl.VCLManager;
import java.util.Optional;
import net.minecraft.class_2960;
import net.minecraft.class_5321;
import net.minecraft.class_6880;
import net.minecraft.class_7871;
import org.spongepowered.asm.mixin.Mixin;
import dev.stolym.vyne.util.VyneLogger;

@Mixin(targets = "net.minecraft.class_2370")
public abstract class MappedRegistryHolderLookupMixin {
    @WrapMethod(method = "method_46769")
    @SuppressWarnings("rawtypes")
    private class_7871 vyne$entryLookup(Operation<class_7871> original) {
        class_7871 base = original.call();
        if (base == null) {
            return null;
        }
        ClassLoader knot = VCLManager.getCanonicalKnotLoader();
        if (knot == null) {
            knot = Thread.currentThread().getContextClassLoader();
        }
        Object wrapped = RegistryDatapackLookupFallback.wrapRegistryEntryLookup(base, knot);
        if (wrapped == null) {
            return base;
        }
        return (class_7871) wrapped;
    }

    @WrapMethod(method = "method_10250")
    private boolean vyne$containsRl(class_2960 rl, Operation<Boolean> original) {
        boolean base = original.call(rl);
        if (base) {
            return true;
        }
        if (Boolean.getBoolean("vyne.registry.skipMappedRegistryLenientContains")) {
            return false;
        }
        ClassLoader knot = VCLManager.getCanonicalKnotLoader();
        if (knot == null) {
            knot = Thread.currentThread().getContextClassLoader();
        }
        return RegistryDatapackLookupFallback.lenientRegistryContainsResourceLocation(this, rl, knot);
    }

    @WrapMethod(method = "method_35842")
    private boolean vyne$containsResourceKey(class_5321<?> key, Operation<Boolean> original) {
        boolean base = original.call(key);
        if (base) {
            return true;
        }
        if (Boolean.getBoolean("vyne.registry.skipMappedRegistryLenientContains")) {
            return false;
        }
        ClassLoader knot = VCLManager.getCanonicalKnotLoader();
        if (knot == null) {
            knot = Thread.currentThread().getContextClassLoader();
        }
        return RegistryDatapackLookupFallback.lenientRegistryContainsResourceKey(this, key, knot);
    }

    @WrapMethod(method = "method_55841")
    @SuppressWarnings("rawtypes")
    private Optional vyne$holderByRl(class_2960 rl, Operation<Optional> original) {
        Optional base = original.call(rl);
        if (base.isPresent()) {
            return base;
        }
        if (RegistryDatapackLookupFallback.isInsideMappedRegistryHolderFallback()) {
            return base;
        }
        if (Boolean.getBoolean("vyne.registry.skipMappedRegistryHolderFallback")) {
            return base;
        }
        ClassLoader knot = VCLManager.getCanonicalKnotLoader();
        if (knot == null) {
            knot = Thread.currentThread().getContextClassLoader();
        }
        Optional<Object> live = RegistryDatapackLookupFallback.lenientHolderAfterMappedRegistryMiss(
            this, knot, rl);
        if (live.isPresent()) {
            return live;
        }
        Optional<Object> dr = RegistryDatapackLookupFallback.tryDeferredRegisterBlockHolderForMappedRegistryRl(
            this, knot, rl);
        return dr.isPresent() ? dr : base;
    }

    @WrapMethod(method = "method_40264")
    @SuppressWarnings("rawtypes")
    private Optional vyne$holderByKey(class_5321 key, Operation<Optional> original) {
        Optional base = original.call(key);
        if (base.isPresent()) {
            return base;
        }
        if (RegistryDatapackLookupFallback.isInsideMappedRegistryHolderFallback()) {
            return base;
        }
        if (Boolean.getBoolean("vyne.registry.skipMappedRegistryHolderFallback")) {
            return base;
        }
        ClassLoader knot = VCLManager.getCanonicalKnotLoader();
        if (knot == null) {
            knot = Thread.currentThread().getContextClassLoader();
        }
        Optional<Object> live = RegistryDatapackLookupFallback.lenientHolderAfterMappedRegistryMissForKey(
            this, knot, key);
        if (live.isPresent()) {
            return live;
        }
        Optional<Object> dr = RegistryDatapackLookupFallback.tryDeferredRegisterBlockHolderForMappedRegistry(
            this, knot, key);
        return dr.isPresent() ? dr : base;
    }

    @WrapMethod(method = "method_47983")
    private class_6880 vyne$holderByRegisteredValue(Object value, Operation<class_6880> original) {
        class_6880 base = original.call(value);
        if (base != null && Boolean.TRUE.equals(ReflectionHelper.call(base, "isBound", "method_40248"))) {
            return base;
        }

        try {
            if (base != null) {
                ReflectionHelper.call(base, "bindValue", "method_40250");
                if (Boolean.TRUE.equals(ReflectionHelper.call(base, "isBound", "method_40248"))) {
                    VyneLogger.info("[Vyne-Recovery] Dynamically bound unbound holder");
                    return base;
                }
            }
        } catch (Throwable ignored) {}

        if (RegistryDatapackLookupFallback.isInsideMappedRegistryHolderFallback()) {
            return base;
        }
        ClassLoader knot = VCLManager.getCanonicalKnotLoader();
        if (knot == null) {
            knot = Thread.currentThread().getContextClassLoader();
        }
        Optional<Object> keyOpt = RegistryDatapackLookupFallback.tryResourceKeyForRegistryValue(
            this, value);
        if (keyOpt.isEmpty()) {
            return base;
        }
        Optional<Object> live = RegistryDatapackLookupFallback.lenientHolderAfterMappedRegistryMissForKey(
            this, knot, keyOpt.get());
        if (live.isPresent() && live.get() instanceof class_6880 h) {
            return h;
        }
        Optional<Object> dr = RegistryDatapackLookupFallback.tryDeferredRegisterBlockHolderForMappedRegistry(
            this, knot, keyOpt.get());
        if (dr.isPresent() && dr.get() instanceof class_6880 h) {
            return h;
        }
        return base;
    }
}
