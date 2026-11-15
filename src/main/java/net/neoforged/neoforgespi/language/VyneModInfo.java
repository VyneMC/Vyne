/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforgespi.language;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

public final class VyneModInfo implements IModInfo {
    private final String modId;
    private final ArtifactVersion version;

    public VyneModInfo(String modId, String rawVersion) {
        this.modId = modId != null ? modId : "unknown";
        this.version = new DefaultArtifactVersion(rawVersion != null ? rawVersion : "0.0.0");
    }

    @Override public String getModId() { return modId; }
    @Override public String getDisplayName() { return modId; }
    @Override public ArtifactVersion getVersion() { return version; }
}
