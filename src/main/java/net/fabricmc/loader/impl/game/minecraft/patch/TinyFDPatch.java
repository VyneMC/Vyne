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

package net.fabricmc.loader.impl.game.minecraft.patch;

import static net.fabricmc.loader.impl.launch.MappingConfiguration.INTERMEDIARY_NAMESPACE;

import java.util.ListIterator;
import java.util.function.Consumer;
import java.util.function.Function;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.game.patch.GamePatch;
import net.fabricmc.loader.impl.launch.FabricLauncher;

public final class TinyFDPatch extends GamePatch {
	private static final String MORE_OPTIONS_DIALOG_CLASS_NAME = "net.minecraft.class_5292";
	private static final String TINYFD_METHOD_NAME = "tinyfd_openFileDialog";

	private static final String DIALOG_TITLE = "Select settings file (.json)";

	@Override
	public void process(FabricLauncher launcher, Function<String, ClassNode> classSource, Consumer<ClassNode> classEmitter) {
		if (launcher.getEnvironmentType() != EnvType.CLIENT) {
			return;
		}

		String className = MORE_OPTIONS_DIALOG_CLASS_NAME;

		if (!launcher.getMappingConfiguration().getRuntimeNamespace().equals(INTERMEDIARY_NAMESPACE)
				&& FabricLoader.getInstance().getMappingResolver().getNamespaces().contains(INTERMEDIARY_NAMESPACE)) {
			className = FabricLoader.getInstance().getMappingResolver().mapClassName(INTERMEDIARY_NAMESPACE, MORE_OPTIONS_DIALOG_CLASS_NAME);
		}

		final ClassNode classNode = classSource.apply(className);

		if (classNode == null) {
			return;
		}

		patchMoreOptionsDialog(classNode);
		classEmitter.accept(classNode);
	}

	private void patchMoreOptionsDialog(ClassNode classNode) {
		for (MethodNode method : classNode.methods) {
			final ListIterator<AbstractInsnNode> iterator = findTargetMethodNode(method);

			if (iterator == null) {
				continue;
			}

			while (iterator.hasPrevious()) {
				final AbstractInsnNode insnNode = iterator.previous();

				if (insnNode.getOpcode() == Opcodes.INVOKEINTERFACE
						|| insnNode.getOpcode() == Opcodes.INVOKEVIRTUAL) {
					InsnList insnList = new InsnList();

					insnList.add(new InsnNode(Opcodes.POP));

					insnList.add(new LdcInsnNode(DIALOG_TITLE));

					method.instructions.insert(insnNode, insnList);
					return;
				}
			}

			throw new IllegalStateException("Failed to patch MoreOptionsDialog");
		}
	}

	private ListIterator<AbstractInsnNode> findTargetMethodNode(MethodNode methodNode) {
		if ((methodNode.access & Opcodes.ACC_SYNTHETIC) == 0) {
			return null;
		}

		ListIterator<AbstractInsnNode> iterator = methodNode.instructions.iterator();

		while (iterator.hasNext()) {
			final AbstractInsnNode instruction = iterator.next();

			if (instruction.getOpcode() != Opcodes.INVOKESTATIC) {
				continue;
			}

			if (!(instruction instanceof MethodInsnNode)) {
				continue;
			}

			final MethodInsnNode methodInsnNode = (MethodInsnNode) instruction;

			if (methodInsnNode.name.equals(TINYFD_METHOD_NAME)) {
				return iterator;
			}
		}

		return null;
	}
}
