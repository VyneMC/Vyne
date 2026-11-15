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

package net.fabricmc.loader.impl.lib.gson;

import java.io.IOException;

public final class MalformedJsonException extends IOException {
	private static final long serialVersionUID = 1L;

	public MalformedJsonException(String msg) {
		super(msg);
	}

	public MalformedJsonException(String msg, Throwable throwable) {
		super(msg);

		initCause(throwable);
	}

	public MalformedJsonException(Throwable throwable) {
		initCause(throwable);
	}
}
