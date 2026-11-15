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

package net.fabricmc.loader.impl.gui;

import java.awt.GraphicsEnvironment;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.gui.FabricStatusTree.FabricBasicButtonType;
import net.fabricmc.loader.impl.gui.FabricStatusTree.FabricStatusNode;
import net.fabricmc.loader.impl.gui.FabricStatusTree.FabricStatusTab;
import net.fabricmc.loader.impl.gui.FabricStatusTree.FabricTreeWarningLevel;
import net.fabricmc.loader.impl.metadata.DependencyOverrides;
import net.fabricmc.loader.impl.util.LoaderUtil;
import net.fabricmc.loader.impl.util.Localization;
import net.fabricmc.loader.impl.util.SystemProperties;
import net.fabricmc.loader.impl.util.UrlUtil;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

import dev.stolym.vyne.util.VyneFabricCrashFormat;

public final class FabricGuiEntry {
	public static void open(FabricStatusTree tree) throws Exception {
		GameProvider provider = FabricLoaderImpl.INSTANCE.tryGetGameProvider();

		if (provider == null && LoaderUtil.hasAwtSupport()
				|| provider != null && provider.hasAwtSupport()) {
			FabricMainWindow.open(tree, true);
		} else {
			openForked(tree);
		}
	}

	private static void openForked(FabricStatusTree tree) throws IOException, InterruptedException {
		Path javaBinDir = LoaderUtil.normalizePath(Paths.get(System.getProperty("java.home"), "bin"));
		String[] executables = { "javaw.exe", "java.exe", "java" };
		Path javaPath = null;

		for (String executable : executables) {
			Path path = javaBinDir.resolve(executable);

			if (Files.isRegularFile(path)) {
				javaPath = path;
				break;
			}
		}

		if (javaPath == null) throw new RuntimeException("can't find java executable in "+javaBinDir);

		Process process = new ProcessBuilder(javaPath.toString(), "-Xmx100M", "-cp", UrlUtil.LOADER_CODE_SOURCE.toString(), FabricGuiEntry.class.getName())
				.redirectOutput(ProcessBuilder.Redirect.INHERIT)
				.redirectError(ProcessBuilder.Redirect.INHERIT)
				.start();

		final Thread shutdownHook = new Thread(process::destroy);

		Runtime.getRuntime().addShutdownHook(shutdownHook);

		try (DataOutputStream os = new DataOutputStream(process.getOutputStream())) {
			tree.writeTo(os);
		}

		int rVal = process.waitFor();

		Runtime.getRuntime().removeShutdownHook(shutdownHook);

		if (rVal != 0) throw new IOException("subprocess exited with code "+rVal);
	}

	public static void main(String[] args) throws Exception {
		FabricStatusTree tree = new FabricStatusTree(new DataInputStream(System.in));
		FabricMainWindow.open(tree, true);
		System.exit(0);
	}

	public static void displayCriticalError(Throwable exception, boolean exitAfter) {
		Log.error(LogCategory.GENERAL, "A critical error occurred", exception);

		displayError(Localization.format("gui.error.header"), exception, exitAfter);
	}

	public static void displayError(String mainText, Throwable exception, boolean exitAfter) {
		displayError(mainText, exception, tree -> {
			String clipboard = exception == null
				? mainText
				: VyneFabricCrashFormat.formatClipboard(mainText, exception);
			tree.addButton(Localization.format("gui.button.copyError"), FabricBasicButtonType.CLICK_MANY).withClipboard(clipboard);
		}, exitAfter);
	}

	public static void displayError(String mainText, Throwable exception, Consumer<FabricStatusTree> treeCustomiser, boolean exitAfter) {
		boolean isCI = System.getenv("CI") != null;
		boolean isNoGui = SystemProperties.isSet(SystemProperties.NO_GUI);

		GameProvider provider = FabricLoaderImpl.INSTANCE.tryGetGameProvider();

		if (!isCI && !isNoGui && !GraphicsEnvironment.isHeadless() && (provider == null || provider.canOpenErrorGui())) {
			String title = "Vyne " + dev.stolym.vyne.core.VyneLauncher.VERSION + " — Crash Report";
			FabricStatusTree tree = new FabricStatusTree(title, mainText);
			FabricStatusTab crashTab = tree.addTab(Localization.format("gui.tab.crash"));

			if (exception != null) {
				crashTab.node.addMessage(VyneFabricCrashFormat.formatDeveloperSummary(exception),
					FabricTreeWarningLevel.ERROR);
				String treeBlock = "Vyne report — truncated stack (hover for full summary + trace)\n"
					+ VyneFabricCrashFormat.formatAbbreviatedStackTrace(exception, 36);
				FabricStatusNode vyneReport = crashTab.node.addMessage(treeBlock, FabricTreeWarningLevel.NONE);
				vyneReport.expandByDefault = true;
				vyneReport.details = VyneFabricCrashFormat.formatClipboard("", exception);
			} else {
				crashTab.node.addMessage(Localization.format("gui.error.missingException"), FabricTreeWarningLevel.NONE);
			}

			tree.addButton(Localization.format("gui.button.exit"), FabricBasicButtonType.CLICK_ONCE).makeClose();
			treeCustomiser.accept(tree);

			DependencyOverrides dependencyOverrides = FabricLoaderImpl.INSTANCE.getDependencyOverrides();

			if (dependencyOverrides != null && !dependencyOverrides.getAffectedModIds().isEmpty()) {
				crashTab.node.addMessage(Localization.format("gui.error.dependencyOverrides",
						String.join(", ", dependencyOverrides.getAffectedModIds())), FabricTreeWarningLevel.WARN);
			}

			try {
				open(tree);
			} catch (Exception e) {
				if (exitAfter) {
					Log.warn(LogCategory.GENERAL, "Failed to open the error gui!", e);
				} else {
					throw new RuntimeException("Failed to open the error gui!", e);
				}
			}
		}

		if (exitAfter) {
			System.exit(1);
		}
	}
}
