/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.util;

public final class VersionConstraintMatcher {
    private VersionConstraintMatcher() {}

    public static boolean isAny(String constraint) {
        return constraint == null || constraint.isBlank() || constraint.equals("*") || constraint.equals("any");
    }

    public static boolean matches(String version, String constraint) {
        if (isAny(constraint)) return true;
        try {
            if (constraint.startsWith(">=")) {
                return compareVersions(version, constraint.substring(2).trim()) >= 0;
            } else if (constraint.startsWith("<=")) {
                return compareVersions(version, constraint.substring(2).trim()) <= 0;
            } else if (constraint.startsWith(">")) {
                return compareVersions(version, constraint.substring(1).trim()) > 0;
            } else if (constraint.startsWith("<")) {
                return compareVersions(version, constraint.substring(1).trim()) < 0;
            } else if (constraint.startsWith("[") || constraint.startsWith("(")) {
                return matchesMavenRange(version, constraint);
            } else {
                return version.equals(constraint) || version.startsWith(constraint);
            }
        } catch (Exception e) {
            return false;
        }
    }

    public static int compareVersions(String a, String b) {
        int[] pa = parseVersion(a), pb = parseVersion(b);
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int va = i < pa.length ? pa[i] : 0;
            int vb = i < pb.length ? pb[i] : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    private static int[] parseVersion(String v) {
        try {
            String[] parts = v.replaceAll("[^0-9.]", "").split("\\.");
            int[] result = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                result[i] = parts[i].isEmpty() ? 0 : Integer.parseInt(parts[i]);
            }
            return result;
        } catch (Exception e) {
            return new int[]{0};
        }
    }

    private static boolean matchesMavenRange(String version, String range) {
        try {
            boolean inclLow = range.startsWith("[");
            boolean inclHigh = range.endsWith("]");
            String inner = range.substring(1, range.length() - 1);
            String[] bounds = inner.split(",", 2);
            String low = bounds[0].trim();
            String high = bounds.length > 1 ? bounds[1].trim() : "";
            if (!low.isEmpty()) {
                int cmp = compareVersions(version, low);
                if (inclLow ? cmp < 0 : cmp <= 0) return false;
            }
            if (!high.isEmpty()) {
                int cmp = compareVersions(version, high);
                if (inclHigh ? cmp > 0 : cmp >= 0) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static int[] parseVersionForSort(String v) {
        return parseVersion(v);
    }
}
