/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.tools.hooks;

import dev.stolym.vyne.convert.NeoForgeInstall;

import java.nio.file.Path;

public final class InstallNeoForge {
    private InstallNeoForge() {}

    public static void main(String[] args) throws Exception {
        Path work = Path.of(System.getProperty("vyne.gameDir"));
        NeoForgeInstall.ensure(args[0], false, work);
    }
}
