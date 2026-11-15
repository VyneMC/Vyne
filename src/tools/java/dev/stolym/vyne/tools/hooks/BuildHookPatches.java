/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.tools.hooks;

import dev.stolym.vyne.convert.HookPatches;
import dev.stolym.vyne.convert.NeoForgeInstall;
import dev.stolym.vyne.convert.VanillaGame;

import java.nio.file.Files;
import java.nio.file.Path;

public final class BuildHookPatches {
    private BuildHookPatches() {}

    public static void main(String[] args) throws Exception {
        String minecraft = args[0];
        String neoForge = args[1];
        Path out = Path.of(args[2]);
        Path work = Path.of(System.getProperty("vyne.gameDir"));
        for (boolean server : new boolean[] {false, true}) {
            String side = server ? "server" : "client";
            NeoForgeInstall install = NeoForgeInstall.ensure(neoForge, server, work);
            Path vanilla = VanillaGame.mojang(minecraft, server, work.resolve(".vyne").resolve("cache"));
            Path zip = out.resolve(side + ".zip");
            HookPatches.write(vanilla, install.patchedGame(), zip);
            System.out.println("Vyne " + side + " patches: " + Files.size(zip) / 1024 + " KB -> " + zip);
        }
    }
}
