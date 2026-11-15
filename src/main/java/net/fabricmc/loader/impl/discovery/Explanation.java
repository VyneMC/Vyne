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

package net.fabricmc.loader.impl.discovery;

import net.fabricmc.loader.api.metadata.ModDependency;

class Explanation implements Comparable<Explanation> {
	private static int nextCmpId;

	final ErrorKind error;
	final ModCandidateImpl mod;
	final ModDependency dep;
	final String data;
	private final int cmpId;

	Explanation(ErrorKind error, ModCandidateImpl mod) {
		this(error, mod, null, null);
	}

	Explanation(ErrorKind error, ModCandidateImpl mod, ModDependency dep) {
		this(error, mod, dep, null);
	}

	Explanation(ErrorKind error, String data) {
		this(error, null, data);
	}

	Explanation(ErrorKind error, ModCandidateImpl mod, String data) {
		this(error, mod, null, data);
	}

	private Explanation(ErrorKind error, ModCandidateImpl mod, ModDependency dep, String data) {
		this.error = error;
		this.mod = mod;
		this.dep = dep;
		this.data = data;
		this.cmpId = nextCmpId++;
	}

	@Override
	public int compareTo(Explanation o) {
		return Integer.compare(cmpId, o.cmpId);
	}

	@Override
	public String toString() {
		if (mod == null) {
			return String.format("%s %s", error, data);
		} else if (dep == null) {
			return String.format("%s %s", error, mod);
		} else {
			return String.format("%s %s %s", error, mod, dep);
		}
	}

	enum ErrorKind {
		PRESELECT_HARD_DEP(true),

		PRESELECT_SOFT_DEP(true),

		PRESELECT_NEG_HARD_DEP(true),

		PRESELECT_FORCELOAD(false),

		HARD_DEP_INCOMPATIBLE_PRESELECTED(true),

		HARD_DEP_NO_CANDIDATE(true),

		HARD_DEP(true),

		SOFT_DEP(true),

		NEG_HARD_DEP(true),

		NESTED_FORCELOAD(false),

		NESTED_REQ_PARENT(false),

		ROOT_FORCELOAD_SINGLE(false),

		ROOT_FORCELOAD(false),

		UNIQUE_ID(false);

		final boolean isDependencyError;

		ErrorKind(boolean isDependencyError) {
			this.isDependencyError = isDependencyError;
		}
	}
}
