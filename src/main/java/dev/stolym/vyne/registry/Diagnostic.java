/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.registry;

public class Diagnostic {
    public static void log(String msg) {
        System.err.println("[Vyne-Diag] " + msg);
    }
}
