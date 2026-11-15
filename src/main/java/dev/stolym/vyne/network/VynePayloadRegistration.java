/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.network;

import dev.stolym.vyne.util.VyneLogger;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class VynePayloadRegistration {
    public record Entry(String modId, String method, String detail) {}

    private static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();

    private VynePayloadRegistration() {}

    public static void record(String modId, String method, Object[] args) {
        String detail = summarizeArgs(args);
        ENTRIES.add(new Entry(modId, method, detail));
        VyneLogger.debug("[Vyne-Net] " + modId + " :: " + method + " " + detail);
    }

    private static String summarizeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "()";
        }
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            Object a = args[i];
            if (a == null) {
                sb.append("null");
            } else {
                String s = a.getClass().getSimpleName();
                sb.append(s);
            }
        }
        sb.append(')');
        if (sb.length() > 240) {
            return sb.substring(0, 236) + "...)";
        }
        return sb.toString();
    }

    public static int size() {
        return ENTRIES.size();
    }

    public static void logSummaryIfAny() {
        int n = ENTRIES.size();
        if (n == 0) {
            return;
        }
        VyneLogger.info("[Vyne-Net] NeoForge payload/channel API used " + n
            + " time(s) this session (registrations recorded; Fabric packet bridge optional).");
    }
}
