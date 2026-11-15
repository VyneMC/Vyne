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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.fabricmc.loader.api.Version;

final class ModPrioSorter {
	static void sort(List<ModCandidateImpl> mods, Map<String, List<ModCandidateImpl>> modsById) {
		mods.sort(comparator);

		Set<String> providedMods = new HashSet<>();

		for (ModCandidateImpl mod : mods) {
			modsById.computeIfAbsent(mod.getId(), ignore -> new ArrayList<>()).add(mod);

			for (String provided : mod.getProvides()) {
				modsById.computeIfAbsent(provided, ignore -> new ArrayList<>()).add(mod);
				providedMods.add(provided);
			}
		}

		for (Iterator<String> it = providedMods.iterator(); it.hasNext(); ) {
			if (modsById.get(it.next()).size() <= 1) {
				it.remove();
			}
		}

		if (providedMods.isEmpty()) return;

		boolean movedPastRoots = false;
		int startIdx = 0;
		Set<String> potentiallyOverlappingIds = new HashSet<>();

		for (int i = 0, size = mods.size(); i < size; i++) {
			ModCandidateImpl mod = mods.get(i);
			String id = mod.getId();

			if (!movedPastRoots && !mod.isRoot()) {
				movedPastRoots = true;
				startIdx = i;
			}

			if (providedMods.contains(id)) {
				potentiallyOverlappingIds.add(id);
			}

			if (!mod.getProvides().isEmpty()) {
				for (String provId : mod.getProvides()) {
					if (providedMods.contains(provId)) {
						potentiallyOverlappingIds.add(provId);
					}
				}
			}

			if (potentiallyOverlappingIds.isEmpty()) continue;

			int earliestIdx = -1;

			for (int j = i - 1; j >= startIdx; j--) {
				ModCandidateImpl cmpMod = mods.get(j);
				String cmpId = cmpMod.getId();
				if (cmpId.equals(id)) break;

				if (!potentiallyOverlappingIds.contains(cmpId)
						&& (cmpMod.getProvides().isEmpty() || Collections.disjoint(potentiallyOverlappingIds, cmpMod.getProvides()))) {
					continue;
				}

				int cmp = compareOverlappingIds(mod, cmpMod, Integer.MAX_VALUE);

				if (cmp < 0) {
					earliestIdx = j;
				} else if (cmp != Integer.MAX_VALUE) {
					break;
				}
			}

			if (earliestIdx >= 0) {
				mods.remove(i);
				mods.add(earliestIdx, mod);
			}

			potentiallyOverlappingIds.clear();
		}
	}

	private static final Comparator<ModCandidateImpl> comparator = new Comparator<ModCandidateImpl>() {
		@Override
		public int compare(ModCandidateImpl a, ModCandidateImpl b) {
			return ModPrioSorter.compare(a, b);
		}
	};

	private static int compare(ModCandidateImpl a, ModCandidateImpl b) {
		if (a.isRoot()) {
			if (!b.isRoot()) {
				return -1;
			}
		} else if (b.isRoot()) {
			return 1;
		}

		int idCmp = a.getId().compareTo(b.getId());
		if (idCmp != 0) return idCmp;

		int versionCmp = b.getVersion().compareTo(a.getVersion());
		if (versionCmp != 0) return versionCmp;

		int nestCmp = a.getMinNestLevel() - b.getMinNestLevel();
		if (nestCmp != 0) return nestCmp;

		if (a.isRoot()) return 0;

		return compareParents(a, b);
	}

	private static int compareParents(ModCandidateImpl a, ModCandidateImpl b) {
		assert !a.getParentMods().isEmpty() && !b.getParentMods().isEmpty();

		ModCandidateImpl minParent = null;

		for (ModCandidateImpl mod : a.getParentMods()) {
			if (minParent == null || mod != minParent && compare(minParent, mod) > 0) {
				minParent = mod;
			}
		}

		assert minParent != null;
		boolean found = false;

		for (ModCandidateImpl mod : b.getParentMods()) {
			if (mod == minParent) {
				found = true;
			} else if (compare(minParent, mod) > 0) {
				return 1;
			}
		}

		return found ? 0 : -1;
	}

	private static int compareOverlappingIds(ModCandidateImpl a, ModCandidateImpl b, int noMatchResult) {
		assert !a.getId().equals(b.getId());

		int ret = 0;
		boolean matched = false;

		for (String provIdA : a.getProvides()) {
			if (provIdA.equals(b.getId())) {
				Version providedVersionA = a.getVersion();
				ret += Integer.signum(b.getVersion().compareTo(providedVersionA));
				matched = true;
			}
		}

		for (String provIdB : b.getProvides()) {
			if (provIdB.equals(a.getId())) {
				Version providedVersionB = b.getVersion();
				ret += Integer.signum(providedVersionB.compareTo(a.getVersion()));
				matched = true;

				continue;
			}

			for (String provIdA : a.getProvides()) {
				if (provIdB.equals(provIdA)) {
					Version providedVersionA = a.getVersion();
					Version providedVersionB = b.getVersion();

					ret += Integer.signum(providedVersionB.compareTo(providedVersionA));
					matched = true;

					break;
				}
			}
		}

		return matched ? ret : noMatchResult;
	}
}
