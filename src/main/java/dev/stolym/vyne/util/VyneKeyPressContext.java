/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.util;

public final class VyneKeyPressContext {
    public static final int KEY_ESCAPE = 256;

    private static final ThreadLocal<int[]> CURRENT = new ThreadLocal<>();

    private VyneKeyPressContext() {}

    public static void push(int keyCode, int scanCode, int modifiers) {
        CURRENT.set(new int[] { keyCode, scanCode, modifiers });
    }

    public static void pop() {
        CURRENT.remove();
    }

    public static int currentKeyCode() {
        int[] state = CURRENT.get();
        return state == null ? -1 : state[0];
    }

    public static boolean isEscape() {
        return currentKeyCode() == KEY_ESCAPE;
    }
}
