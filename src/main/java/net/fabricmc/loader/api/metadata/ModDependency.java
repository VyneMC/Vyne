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

package net.fabricmc.loader.api.metadata;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionInterval;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

public interface ModDependency {
	Kind getKind();

	String getModId();

	boolean matches(Version version);

	Collection<VersionPredicate> getVersionRequirements();

	List<VersionInterval> getVersionIntervals();

	enum Kind {
		DEPENDS("depends", true, false),
		RECOMMENDS("recommends", true, true),
		SUGGESTS("suggests", true, true),
		CONFLICTS("conflicts", false, true),
		BREAKS("breaks", false, false);

		private static final Map<String, Kind> map = createMap();
		private final String key;
		private final boolean positive;
		private final boolean soft;

		Kind(String key, boolean positive, boolean soft) {
			this.key = key;
			this.positive = positive;
			this.soft = soft;
		}

		public String getKey() {
			return key;
		}

		public boolean isPositive() {
			return positive;
		}

		public boolean isSoft() {
			return soft;
		}

		public static Kind parse(String key) {
			return map.get(key);
		}

		private static Map<String, Kind> createMap() {
			Kind[] values = values();
			Map<String, Kind> ret = new HashMap<>(values.length);

			for (Kind kind : values) {
				ret.put(kind.key, kind);
			}

			return ret;
		}
	}
}
