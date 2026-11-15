/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.util;

public final class VyneClientContainment {
    private VyneClientContainment() {}

    public static final String PROPERTY = "vyne.containment.client";

    public enum Mode {
        OFF,
        STRICT,
        MAX
    }

    private static volatile Mode cached;

    public static Mode mode() {
        Mode c = cached;
        if (c != null) {
            return c;
        }
        String raw = System.getProperty(PROPERTY, "max").trim().toLowerCase();
        c = switch (raw) {
            case "off", "false", "0" -> Mode.OFF;
            case "strict", "linkage" -> Mode.STRICT;
            default -> Mode.MAX;
        };
        cached = c;
        return c;
    }

    public static boolean shouldAbsorb(Throwable t) {
        if (isNeverAbsorb(t)) {
            return false;
        }
        return switch (mode()) {
            case OFF -> false;
            case STRICT -> VyneLinkageContainment.shouldContain(t);
            case MAX -> true;
        };
    }

    private static boolean isNeverAbsorb(Throwable t) {
        return t instanceof OutOfMemoryError;
    }
}
