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

package net.fabricmc.loader.api.metadata.version;

import java.util.Collection;
import java.util.List;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.impl.util.version.VersionIntervalImpl;

public interface VersionInterval {
	VersionInterval INFINITE = new VersionIntervalImpl(null, false, null, false);

	boolean isSemantic();

	Version getMin();

	boolean isMinInclusive();

	Version getMax();

	boolean isMaxInclusive();

	default VersionInterval and(VersionInterval o) {
		return and(this, o);
	}

	default List<VersionInterval> or(Collection<VersionInterval> o) {
		return or(o, this);
	}

	default List<VersionInterval> not() {
		return not(this);
	}

	static VersionInterval and(VersionInterval a, VersionInterval b) {
		return VersionIntervalImpl.and(a, b);
	}

	static List<VersionInterval> and(Collection<VersionInterval> a, Collection<VersionInterval> b) {
		return VersionIntervalImpl.and(a, b);
	}

	static List<VersionInterval> or(Collection<VersionInterval> a, VersionInterval b) {
		return VersionIntervalImpl.or(a, b);
	}

	static List<VersionInterval> not(VersionInterval interval) {
		return VersionIntervalImpl.not(interval);
	}

	static List<VersionInterval> not(Collection<VersionInterval> intervals) {
		return VersionIntervalImpl.not(intervals);
	}
}
