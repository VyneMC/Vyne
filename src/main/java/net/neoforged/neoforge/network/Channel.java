/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.network;

import java.util.function.Predicate;

public final class Channel {
    private Channel() {}

    @FunctionalInterface
    public interface VersionTest extends Predicate<String> {
        @Override
        boolean test(String version);

        default boolean accepts(String version) {
            return test(version);
        }

        static VersionTest exact(int protocolId) {
            final String expected = Integer.toString(protocolId);
            return version -> expected.equals(version);
        }
    }

    public static VersionTest acceptMissingOr(Predicate<String> predicate) {
        return version -> version == null || predicate == null || predicate.test(version);
    }

    public static VersionTest acceptedVersions(Predicate<String> predicate) {
        return version -> predicate == null || predicate.test(version);
    }
}
