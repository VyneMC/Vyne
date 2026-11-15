/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforge.network;

import java.util.function.Supplier;

public class ChannelBuilder {
    private final String name;

    private ChannelBuilder(String name) {
        this.name = name;
    }

    public static ChannelBuilder named(String name) {
        return new ChannelBuilder(name);
    }

    public static ChannelBuilder named(net.minecraft.class_2960 id) {
        return new ChannelBuilder(id == null ? "unknown:unknown" : id.toString());
    }

    public ChannelBuilder networkProtocolVersion(Supplier<String> version) {
        return this;
    }

    public ChannelBuilder networkProtocolVersion(int protocol) {
        return networkProtocolVersion(() -> String.valueOf(protocol));
    }

    public ChannelBuilder clientAcceptedVersions(java.util.function.Predicate<String> pred) {
        return this;
    }

    public ChannelBuilder serverAcceptedVersions(java.util.function.Predicate<String> pred) {
        return this;
    }

    public ChannelBuilder acceptedVersions(Channel.VersionTest versionTest) {
        if (versionTest == null) {
            return this;
        }
        clientAcceptedVersions(versionTest::test);
        serverAcceptedVersions(versionTest::test);
        return this;
    }

    public SimpleChannel simpleChannel() {
        return new SimpleChannel();
    }
}
