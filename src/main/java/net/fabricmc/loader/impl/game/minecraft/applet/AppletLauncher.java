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

package net.fabricmc.loader.impl.game.minecraft.applet;

import java.applet.Applet;
import java.applet.AppletStub;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import net.fabricmc.loader.impl.game.minecraft.Hooks;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;

@SuppressWarnings({"serial", "deprecation", "removal"})
public class AppletLauncher extends Applet implements AppletStub {
	@SuppressWarnings("removal")
	public static File gameDir;

	private final Map<String, String> params;
	private Applet mcApplet;
	private boolean active;

	public AppletLauncher(File instance, String username, String sessionid, String host, String port, boolean doConnect, boolean fullscreen, boolean demo) {
		gameDir = instance;

		params = new HashMap<>();
		params.put("username", username);
		params.put("sessionid", sessionid);
		params.put("stand-alone", "true");

		if (doConnect) {
			params.put("server", host);
			params.put("port", port);
		}

		params.put("fullscreen", Boolean.toString(fullscreen));
		params.put("demo", Boolean.toString(demo));

		try {
			mcApplet = (Applet) FabricLauncherBase.getLauncher()
					.getTargetClassLoader()
					.loadClass(Hooks.appletMainClass)
					.getDeclaredConstructor()
					.newInstance();

			if (mcApplet == null) {
				throw new RuntimeException("Could not instantiate MinecraftApplet - is null?");
			}

			this.add(mcApplet, "Center");
		} catch (InstantiationException | InvocationTargetException | IllegalAccessException | NoSuchMethodException | ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	public Map<String, String> getParams() {
		return params;
	}

	public void replace(Applet applet) {
		this.mcApplet = applet;
		init();

		if (active) {
			start();
			validate();
		}
	}

	@Override
	public void appletResize(int width, int height) {
		mcApplet.resize(width, height);
	}

	@Override
	public void resize(int width, int height) {
		mcApplet.resize(width, height);
	}

	@Override
	public void resize(Dimension dim) {
		mcApplet.resize(dim);
	}

	@Override
	public String getParameter(String name) {
		String value = params.get(name);
		if (value != null) return value;

		try {
			return super.getParameter(name);
		} catch (Exception ignored) {
		}

		return null;
	}

	@Override
	public boolean isActive() {
		return this.active;
	}

	@Override
	public void init() {
		mcApplet.setStub(this);
		mcApplet.setSize(getWidth(), getHeight());
		setLayout(new BorderLayout());
		this.add(mcApplet, "Center");
		mcApplet.init();
	}

	@Override
	public void start() {
		mcApplet.start();
		active = true;
	}

	@Override
	public void stop() {
		mcApplet.stop();
		active = false;
	}

	private URL getMinecraftHostingUrl() {
		try {
			return new URL("http://www.minecraft.net/game");
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}

		return null;
	}

	@Override
	public URL getCodeBase() {
		return getMinecraftHostingUrl();
	}

	@Override
	public URL getDocumentBase() {
		return getMinecraftHostingUrl();
	}

	@Override
	public void setVisible(boolean flag) {
		super.setVisible(flag);
		mcApplet.setVisible(flag);
	}
}
