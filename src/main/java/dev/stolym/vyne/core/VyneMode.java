/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.core;

public final class VyneMode {
    private static volatile boolean patchedGame;
    private static volatile boolean hooks;
    private static volatile java.nio.file.Path neoForgeApiJar;
    private static volatile java.util.List<java.nio.file.Path> remapClasspath = java.util.List.of();

    private VyneMode() {}

    public static void enablePatchedGame() {
        patchedGame = true;
    }

    public static boolean patchedGame() {
        return patchedGame;
    }

    public static void enableHooks() {
        hooks = true;
    }

    public static boolean hooks() {
        return hooks;
    }

    public static void neoForgeApiJar(java.nio.file.Path runtimeJar) {
        neoForgeApiJar = runtimeJar;
    }

    public static java.nio.file.Path neoForgeApiJar() {
        return neoForgeApiJar;
    }

    public static void remapClasspath(java.util.List<java.nio.file.Path> classpath) {
        remapClasspath = java.util.List.copyOf(classpath);
    }

    public static java.util.List<java.nio.file.Path> remapClasspath() {
        return remapClasspath;
    }
}
