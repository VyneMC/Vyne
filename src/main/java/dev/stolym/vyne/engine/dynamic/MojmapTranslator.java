/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.engine.dynamic;

import dev.stolym.vyne.mappings.MappingComposer;
import dev.stolym.vyne.mappings.MappingRegistry;

public final class MojmapTranslator {
    private MojmapTranslator() {}

    public static String classInter(String mojInternal) {
        if (mojInternal == null || mojInternal.isEmpty()) return mojInternal;
        if (!MappingRegistry.isReady()) return mojInternal;
        try {
            return MappingRegistry.get().mapClass(mojInternal);
        } catch (Throwable ignored) {
            return mojInternal;
        }
    }

    public static String methodNameInter(String mojOwner, String mojName, String mojDesc) {
        if (mojOwner == null || mojName == null) return mojName;
        if (!MappingRegistry.isReady()) return mojName;
        try {
            return MappingRegistry.get().mapMethod(mojOwner, mojName, mojDesc);
        } catch (Throwable ignored) {
            return mojName;
        }
    }

    public static String descInter(String mojDesc) {
        if (mojDesc == null || mojDesc.isEmpty()) return mojDesc;
        if (!MappingRegistry.isReady()) return mojDesc;
        StringBuilder sb = new StringBuilder(mojDesc.length());
        int i = 0;
        while (i < mojDesc.length()) {
            char c = mojDesc.charAt(i);
            if (c == 'L') {
                int semi = mojDesc.indexOf(';', i);
                if (semi < 0) {
                    sb.append(mojDesc.substring(i));
                    break;
                }
                String owner = mojDesc.substring(i + 1, semi);
                sb.append('L').append(classInter(owner)).append(';');
                i = semi + 1;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }
}
