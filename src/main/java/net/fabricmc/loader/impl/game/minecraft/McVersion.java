/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package net.fabricmc.loader.impl.game.minecraft;

import java.util.OptionalInt;

public final class McVersion {
	private final String id;

	private final String name;

	private final String raw;

	private final String normalized;
	private final OptionalInt classVersion;

	private McVersion(String id, String name, String raw, String release, OptionalInt classVersion) {
		this.id = id;
		this.name = name;
		this.raw = raw;
		this.normalized = McVersionLookup.normalizeVersion(raw, release);
		this.classVersion = classVersion;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getRaw() {
		return this.raw;
	}

	public String getNormalized() {
		return this.normalized;
	}

	public OptionalInt getClassVersion() {
		return this.classVersion;
	}

	@Override
	public String toString() {
		return String.format("McVersion{id=%s, name=%s, raw=%s, normalized=%s, classVersion=%s}",
				id, name, raw, normalized, classVersion);
	}

	public static final class Builder {
		private String id;
		private String name;
		private String version;
		private String release;
		private OptionalInt classVersion = OptionalInt.empty();

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setName(String name) {
			this.name = name;
			return this;
		}

		public Builder setVersion(String name) {
			this.version = name;
			return this;
		}

		public Builder setRelease(String release) {
			this.release = release;
			return this;
		}

		public Builder setClassVersion(int classVersion) {
			this.classVersion = OptionalInt.of(classVersion);
			return this;
		}

		public Builder setNameAndRelease(String name) {
			return setVersion(name)
					.setRelease(McVersionLookup.getRelease(name));
		}

		public Builder setFromFileName(String name) {
			int pos = name.lastIndexOf('.');
			if (pos > 0) name = name.substring(0, pos);

			return setNameAndRelease(name);
		}

		public McVersion build() {
			return new McVersion(this.id, this.name, this.version, this.release, this.classVersion);
		}
	}
}
