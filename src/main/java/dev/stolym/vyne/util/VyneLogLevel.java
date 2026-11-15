/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.util;

public final class VyneLogLevel {
    public static final String VERBOSE_PROPERTY = "vyne.verbose";

    private static volatile Boolean cached;

    private VyneLogLevel() {}

    public static boolean isVerbose() {
        Boolean c = cached;
        if (c != null) return c;
        String raw = System.getProperty(VERBOSE_PROPERTY);
        if (raw == null || raw.isEmpty()) raw = System.getenv("VYNE_VERBOSE");
        boolean v = raw != null && switch (raw.trim().toLowerCase()) {
            case "1", "true", "yes", "on", "all", "debug" -> true;
            default -> false;
        };
        cached = v;
        return v;
    }

    public static boolean isQuiet() {
        return !isVerbose();
    }

    public static void invalidate() {
        cached = null;
    }
}
