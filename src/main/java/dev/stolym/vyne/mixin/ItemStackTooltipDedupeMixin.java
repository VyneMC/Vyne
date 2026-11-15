/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(targets = "net.minecraft.class_1799")
public abstract class ItemStackTooltipDedupeMixin {
    private static volatile Method CACHED_GET_STRING;
    private static volatile Class<?> CACHED_GET_STRING_OWNER;

    @Inject(method = "method_7950", at = @At("RETURN"), require = 0)
    private void vyne$dedupeTooltipLines(CallbackInfoReturnable<List<?>> cir) {
        List<?> rawLines = cir.getReturnValue();
        if (rawLines == null || rawLines.size() < 2) return;

        @SuppressWarnings("unchecked")
        List<Object> lines = (List<Object>) rawLines;

        Set<String> seen = new HashSet<>(lines.size());
        int writeIdx = 0;
        for (int readIdx = 0; readIdx < lines.size(); readIdx++) {
            Object entry = lines.get(readIdx);
            if (entry == null) {
                if (writeIdx != readIdx) lines.set(writeIdx, null);
                writeIdx++;
                continue;
            }
            String rendered = vyne$renderLine(entry, readIdx);

            if (readIdx < 2) {
                seen.add(rendered);
                if (writeIdx != readIdx) lines.set(writeIdx, entry);
                writeIdx++;
                continue;
            }
            if (!seen.add(rendered)) {
                continue;
            }
            if (writeIdx != readIdx) lines.set(writeIdx, entry);
            writeIdx++;
        }
        if (writeIdx < lines.size()) {
            lines.subList(writeIdx, lines.size()).clear();
        }
    }

    private static String vyne$renderLine(Object component, int fallback) {
        try {
            Class<?> owner = component.getClass();
            Method cached = CACHED_GET_STRING;
            if (cached == null || CACHED_GET_STRING_OWNER != owner) {
                Method resolved = null;
                try {
                    resolved = owner.getMethod("getString");
                } catch (NoSuchMethodException ignored) {}
                if (resolved == null) {
                    for (Method m : owner.getMethods()) {
                        if (m.getParameterCount() != 0) continue;
                        if (m.getReturnType() != String.class) continue;
                        String name = m.getName();
                        if (name.equals("getString") || name.equals("method_54160")
                                || name.equals("method_11023")) {
                            resolved = m;
                            break;
                        }
                    }
                }
                if (resolved != null) {
                    resolved.setAccessible(true);
                    CACHED_GET_STRING = resolved;
                    CACHED_GET_STRING_OWNER = owner;
                    cached = resolved;
                }
            }
            if (cached != null) {
                Object result = cached.invoke(component);
                if (result instanceof String s) return s;
            }
        } catch (Throwable ignored) {
        }
        return "\0raw:" + fallback + ":" + System.identityHashCode(component);
    }
}
