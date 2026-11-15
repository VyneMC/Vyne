/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.engine.dynamic;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

public final class VyneRemapperAlias {
    private VyneRemapperAlias() {}

    public static String remapForgeToNeoforge(String internalName) {
        if (internalName == null) return null;
        if (internalName.startsWith("net/minecraftforge/vyne/")) return null;
        if (internalName.startsWith("net/minecraftforge/fml/")) return null;
        if (internalName.startsWith("net/minecraftforge/common/capabilities/")) return null;

        String result = internalName;
        if (internalName.startsWith("net/minecraftforge/")) {
            result = internalName.replace("net/minecraftforge/", "net/neoforged/neoforge/");
        } else if (internalName.startsWith("net/minecraftforge/common/")) {
            result = internalName.replace("net/minecraftforge/common/", "net/neoforged/neoforge/common/");
        }

        if (result.contains("ForgeConfigSpec")) {
            result = result.replace("ForgeConfigSpec", "ModConfigSpec");
        }
        if (result.contains("IBlockExtensions")) {
            result = result.replace("IBlockExtensions", "IBlockExtension");
        }

        return result.equals(internalName) ? null : result;
    }

    public static byte[] renameClassBytes(byte[] classBytes, String newInternalName) {
        ClassReader cr = new ClassReader(classBytes);
        String oldInternalName = cr.getClassName();
        ClassWriter cw = new ClassWriter(0);
        ClassRemapper remapper = new ClassRemapper(cw, new Remapper() {
            @Override
            public String map(String internalName) {
                if (internalName == null) return null;
                if (internalName.equals(oldInternalName)) return newInternalName;
                String aliased = remapForgeToNeoforge(internalName);
                return aliased != null ? aliased : internalName;
            }
        });
        cr.accept(remapper, 0);
        return cw.toByteArray();
    }
}
