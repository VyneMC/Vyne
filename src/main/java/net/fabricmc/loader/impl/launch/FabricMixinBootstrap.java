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

package net.fabricmc.loader.impl.launch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.launch.MixinBootstrap;
import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import org.spongepowered.asm.mixin.FabricUtil;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.transformer.Config;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModDependency.Kind;
import net.fabricmc.loader.api.metadata.version.VersionInterval;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.ModContainerImpl;
import net.fabricmc.loader.impl.launch.knot.MixinServiceKnot;
import net.fabricmc.loader.impl.launch.knot.MixinServiceKnotBootstrap;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.fabricmc.loader.impl.util.mappings.MixinIntermediaryDevRemapper;
import net.fabricmc.mappingio.tree.MappingTree;

public final class FabricMixinBootstrap {
	private FabricMixinBootstrap() { }

	private static boolean initialized = false;

	public static void init(EnvType side, FabricLoaderImpl loader) {
		if (initialized) {
			throw new RuntimeException("FabricMixinBootstrap has already been initialized!");
		}

		System.setProperty("mixin.bootstrapService", MixinServiceKnotBootstrap.class.getName());
		System.setProperty("mixin.service", MixinServiceKnot.class.getName());

		MixinBootstrap.init();
		MixinExtrasBootstrap.init();

		if (FabricLauncherBase.getLauncher().isDevelopment()) {
			MappingConfiguration config = FabricLauncherBase.getLauncher().getMappingConfiguration();
			MappingTree mappings = config.getMappings();
			final String modNs = config.getDefaultModDistributionNamespace();
			String runtimeNs = config.getRuntimeNamespace();

			if (config.hasAnyMappings() && !modNs.equals(runtimeNs)) {
				List<String> namespaces = new ArrayList<>(mappings.getDstNamespaces());
				namespaces.add(mappings.getSrcNamespace());

				if (namespaces.contains(modNs) && namespaces.contains(runtimeNs)) {
					System.setProperty("mixin.env.remapRefMap", "true");

					try {
						MixinIntermediaryDevRemapper remapper = new MixinIntermediaryDevRemapper(mappings, modNs, runtimeNs);
						MixinEnvironment.getDefaultEnvironment().getRemappers().add(remapper);
						Log.info(LogCategory.MIXIN, "Loaded Fabric development mappings for mixin remapper!");
					} catch (Exception e) {
						Log.error(LogCategory.MIXIN, "Fabric development environment setup error - the game will probably crash soon!", e);
					}
				}
			}
		}

		Map<String, ModContainerImpl> configToModMap = new HashMap<>();

		for (ModContainerImpl mod : loader.getModsInternal()) {
			for (String config : mod.getMetadata().getMixinConfigs(side)) {
				ModContainerImpl prev = configToModMap.putIfAbsent(config, mod);
				if (prev != null) throw new RuntimeException(String.format("Non-unique Mixin config name %s used by the mods %s and %s", config, prev.getMetadata().getId(), mod.getMetadata().getId()));

				if (dev.stolym.vyne.core.ModActivity.isInactive(mod.getMetadata().getId())) {
					Log.info(LogCategory.MIXIN, "Vyne: Skipping mixin config %s — mod %s is inactive.", config, mod.getMetadata().getId());
					continue;
				}

				try {
					Mixins.addConfiguration(config);
				} catch (Throwable t) {
					throw new RuntimeException(String.format("Error parsing or using Mixin config %s for mod %s", config, mod.getMetadata().getId()), t);
				}
			}
		}

		java.util.List<dev.stolym.vyne.core.DetectedMod> neoForgeMods = dev.stolym.vyne.core.VyneLauncher.getNeoForgeMods();
		if (neoForgeMods != null) {
			for (dev.stolym.vyne.core.DetectedMod mod : neoForgeMods) {
				if (mod.getMixins() == null) continue;
				for (String config : mod.getMixins()) {
					if (configToModMap.containsKey(config)) continue;
					try {
						Mixins.addConfiguration(config);
						Log.info(LogCategory.MIXIN, "Vyne: Registered mixin config %s for %s", config, mod.getModId());
					} catch (Throwable t) {
						Log.warn(LogCategory.MIXIN, "Vyne: Could not register mixin config %s for %s: %s", config, mod.getModId(), t);
					}
				}
			}
		}

		try {
			IMixinConfig.class.getMethod("decorate", String.class, Object.class);
			MixinConfigDecorator.apply(configToModMap);
		} catch (NoSuchMethodException e) {
			Log.info(LogCategory.MIXIN, "Detected old Mixin version without config decoration support");
		}

		initialized = true;
	}

	private static final class MixinConfigDecorator {
		static void apply(Map<String, ModContainerImpl> configToModMap) {
			for (Config rawConfig : Mixins.getConfigs()) {
				ModContainerImpl mod = configToModMap.get(rawConfig.getName());
				if (mod == null) continue;

				IMixinConfig config = rawConfig.getConfig();
				config.decorate(FabricUtil.KEY_MOD_ID, mod.getMetadata().getId());
				config.decorate(FabricUtil.KEY_COMPATIBILITY, getMixinCompat(mod));
			}
		}

		private static int getMixinCompat(ModContainerImpl mod) {
			List<VersionInterval> reqIntervals = Collections.singletonList(VersionInterval.INFINITE);

			for (ModDependency dep : mod.getMetadata().getDependencies()) {
				if (dep.getModId().equals("fabricloader") || dep.getModId().equals("fabric-loader")) {
					if (dep.getKind() == Kind.DEPENDS) {
						reqIntervals = VersionInterval.and(reqIntervals, dep.getVersionIntervals());
					} else if (dep.getKind() == Kind.BREAKS) {
						reqIntervals = VersionInterval.and(reqIntervals, VersionInterval.not(dep.getVersionIntervals()));
					}
				}
			}

			if (reqIntervals.isEmpty()) throw new IllegalStateException("mod "+mod+" is incompatible with every loader version?");

			Version minLoaderVersion = reqIntervals.get(0).getMin();

			if (minLoaderVersion != null) {
				for (FabricMixinVersions.LoaderMixinVersionEntry version : FabricMixinVersions.getVersions()) {
					if (minLoaderVersion.compareTo(version.loaderVersion) >= 0) {
						return version.mixinVersion;
					}
				}
			}

			return FabricUtil.COMPATIBILITY_0_17_1;
		}
	}
}
