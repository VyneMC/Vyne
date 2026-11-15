/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.core;

public final class GameLoader {
    private static volatile ClassLoader loader;

    private GameLoader() {}

    public static void set(ClassLoader gameLoader) {
        loader = gameLoader;
    }

    public static ClassLoader get() {
        return loader;
    }
}
