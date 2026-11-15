/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the Licenses/LICENSE_VYNE file in the project root.
 */

package dev.stolym.vyne.install.gui;

import java.nio.file.Path;
import java.util.Objects;

public record LauncherProfile(Kind kind, String displayName, Path instanceDir, String mcVersion,
                              String loaderHint) {
    public enum Kind {
        PRISM("PrismLauncher", "Prism / MultiMC / PolyMC instance"),
        MULTIMC("MultiMC", "MultiMC instance"),
        POLYMC("PolyMC", "PolyMC instance"),
        ATLAUNCHER("ATLauncher", "ATLauncher instance"),
        MODRINTH("Modrinth App", "Modrinth App instance"),
        CURSEFORGE("CurseForge", "CurseForge / Overwolf instance"),
        VANILLA("Minecraft Launcher", "Official .minecraft profile"),
        SERVER("Server", "Dedicated server directory"),
        CUSTOM("Custom", "Custom directory");

        private final String label;
        private final String description;

        Kind(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String label() { return label; }
        public String description() { return description; }
    }

    public LauncherProfile {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(instanceDir, "instanceDir");
    }

    public static LauncherProfile of(Kind kind, String displayName, Path instanceDir) {
        return new LauncherProfile(kind, displayName, instanceDir, null, null);
    }

    public static LauncherProfile of(Kind kind, String displayName, Path instanceDir,
                                     String mcVersion, String loaderHint) {
        return new LauncherProfile(kind, displayName, instanceDir, mcVersion, loaderHint);
    }

    public String subtitle() {
        StringBuilder sb = new StringBuilder(kind.label());
        if (mcVersion != null && !mcVersion.isBlank()) {
            sb.append("  ·  MC ").append(mcVersion);
        }
        if (loaderHint != null && !loaderHint.isBlank()) {
            sb.append("  ·  ").append(loaderHint);
        }
        return sb.toString();
    }
}
