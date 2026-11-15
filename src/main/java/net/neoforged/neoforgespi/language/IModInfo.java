/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.neoforged.neoforgespi.language;

import org.apache.maven.artifact.versioning.ArtifactVersion;

public interface IModInfo {
    String getModId();

    String getDisplayName();

    ArtifactVersion getVersion();
}
