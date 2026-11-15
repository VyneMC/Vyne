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

package net.fabricmc.loader.api;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.impl.FabricLoaderImpl;

public interface FabricLoader {
	static FabricLoader getInstance() {
		FabricLoader ret = FabricLoaderImpl.INSTANCE;

		if (ret == null) {
			throw new RuntimeException("Accessed FabricLoader too early!");
		}

		return ret;
	}

	<T> List<T> getEntrypoints(String key, Class<T> type);

	<T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type);

	<T> void invokeEntrypoints(String key, Class<T> type, Consumer<? super T> invoker);

	ObjectShare getObjectShare();

	MappingResolver getMappingResolver();

	Optional<ModContainer> getModContainer(String id);

	Collection<ModContainer> getAllMods();

	boolean isModLoaded(String id);

	boolean isDevelopmentEnvironment();

	EnvType getEnvironmentType();

	String getRawGameVersion();

	@Deprecated
	Object getGameInstance();

	Path getGameDir();

	@Deprecated
	File getGameDirectory();

	Path getConfigDir();

	@Deprecated
	File getConfigDirectory();

	String[] getLaunchArguments(boolean sanitize);
}
