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

import java.util.List;
import java.util.ListIterator;
import java.util.function.Consumer;
import java.util.function.Function;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import net.fabricmc.loader.impl.game.minecraft.Hooks;
import net.fabricmc.loader.impl.game.minecraft.MinecraftGameProvider;
import net.fabricmc.loader.impl.game.patch.GamePatch;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.fabricmc.loader.impl.util.version.VersionPredicateParser;

public class EntrypointPatch extends GamePatch {
	private static final VersionPredicate VERSION_1_19_4 = createVersionPredicate(">=1.19.4-");
	private static final VersionPredicate VERSION_25w14craftmine = createVersionPredicate("1.21.6-alpha.25.14.craftmine");

	private final MinecraftGameProvider gameProvider;

	public EntrypointPatch(MinecraftGameProvider gameProvider) {
		this.gameProvider = gameProvider;
	}

	private void finishEntrypoint(EnvType type, ListIterator<AbstractInsnNode> it) {
		String methodName = String.format("start%s", type == EnvType.CLIENT ? "Client" : "Server");
		it.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Hooks.INTERNAL_NAME, methodName, "(Ljava/io/File;Ljava/lang/Object;)V", false));
	}

	@Override
	public void process(FabricLauncher launcher, Function<String, ClassNode> classSource, Consumer<ClassNode> classEmitter) {
		EnvType type = launcher.getEnvironmentType();
		String entrypoint = launcher.getEntrypoint();
		Version gameVersion = getGameVersion();

		if (!entrypoint.startsWith("net.minecraft.") && !entrypoint.startsWith("com.mojang.")) {
			return;
		}

		String gameEntrypoint = null;
		boolean serverHasFile = true;
		boolean isApplet = entrypoint.contains("Applet");
		ClassNode mainClass = classSource.apply(entrypoint);

		if (mainClass == null) {
			throw new RuntimeException("Could not load main class " + entrypoint + "!");
		}

		boolean is20w22aServerOrHigher = false;

		if (type == EnvType.CLIENT) {
			List<FieldNode> newGameFields = findFields(mainClass,
					(f) -> !isStatic(f.access) && f.desc.startsWith("L") && !f.desc.startsWith("Ljava/")
					);

			if (newGameFields.size() == 1) {
				gameEntrypoint = Type.getType(newGameFields.get(0).desc).getClassName();
			}
		}

		if (gameEntrypoint == null) {
			MethodNode mainMethod = findMethod(mainClass, (method) -> method.name.equals("main") && method.desc.equals("([Ljava/lang/String;)V") && isPublicStatic(method.access));

			if (mainMethod == null) {
				throw new RuntimeException("Could not find main method in " + entrypoint + "!");
			}

			if (type == EnvType.CLIENT && mainMethod.instructions.size() < 10) {
				MethodInsnNode invocation = null;

				for (AbstractInsnNode insn : mainMethod.instructions) {
					MethodInsnNode methodInsn;

					if (invocation == null
							&& insn.getType() == AbstractInsnNode.METHOD_INSN
							&& (methodInsn = (MethodInsnNode) insn).owner.equals(mainClass.name)) {
						invocation = methodInsn;
					} else if (insn.getOpcode() > Opcodes.ALOAD
							&& insn.getOpcode() != Opcodes.RETURN) {
						invocation = null;
						break;
					}
				}

				if (invocation != null) {
					final MethodInsnNode reqMethod = invocation;
					mainMethod = findMethod(mainClass, m -> m.name.equals(reqMethod.name) && m.desc.equals(reqMethod.desc));
				}
			} else if (type == EnvType.SERVER) {
				MethodInsnNode newGameInsn = (MethodInsnNode) findInsn(mainMethod,
						(insn) -> insn.getOpcode() == Opcodes.INVOKESPECIAL && ((MethodInsnNode) insn).name.equals("<init>") && ((MethodInsnNode) insn).owner.equals(mainClass.name),
						false
						);

				if (newGameInsn != null) {
					gameEntrypoint = newGameInsn.owner.replace('/', '.');
					serverHasFile = newGameInsn.desc.startsWith("(Ljava/io/File;");
				}
			}

			if (gameEntrypoint == null) {
				MethodInsnNode newGameInsn = (MethodInsnNode) findInsn(mainMethod,
						type == EnvType.CLIENT
						? (insn) -> (insn.getOpcode() == Opcodes.INVOKESPECIAL || insn.getOpcode() == Opcodes.INVOKEVIRTUAL) && !((MethodInsnNode) insn).owner.startsWith("java/")
								: (insn) -> insn.getOpcode() == Opcodes.INVOKESPECIAL && ((MethodInsnNode) insn).name.equals("<init>") && hasSuperClass(((MethodInsnNode) insn).owner, mainClass.name, classSource),
								true
						);

				if (newGameInsn == null && type == EnvType.SERVER) {
					newGameInsn = (MethodInsnNode) findInsn(mainMethod,
							insn -> (insn instanceof MethodInsnNode) && insn.getOpcode() == Opcodes.INVOKESPECIAL && hasStrInMethod(((MethodInsnNode) insn).owner, "<clinit>", "()V", "^[a-fA-F0-9]{40}$", classSource),
							false);
				}

				if (type == EnvType.SERVER && hasStrInMethod(mainClass.name, mainMethod.name, mainMethod.desc, "Safe mode active, only vanilla datapack will be loaded", classSource)) {
					is20w22aServerOrHigher = true;
					gameEntrypoint = mainClass.name;
				}

				if (newGameInsn != null) {
					gameEntrypoint = newGameInsn.owner.replace('/', '.');
					serverHasFile = newGameInsn.desc.startsWith("(Ljava/io/File;");
				}
			}
		}

		if (gameEntrypoint == null) {
			throw new RuntimeException("Could not find game constructor in " + entrypoint + "!");
		}

		Log.debug(LogCategory.GAME_PATCH, "Found game constructor: %s -> %s", entrypoint, gameEntrypoint);
		ClassNode gameClass;

		if (gameEntrypoint.equals(entrypoint) || is20w22aServerOrHigher) {
			gameClass = mainClass;
		} else {
			gameClass = classSource.apply(gameEntrypoint);
			if (gameClass == null) throw new RuntimeException("Could not load game class " + gameEntrypoint + "!");
		}

		MethodNode gameMethod = null;
		MethodNode gameConstructor = null;
		AbstractInsnNode lwjglLogNode = null;
		AbstractInsnNode currentThreadNode = null;
		int gameMethodQuality = 0;

		if (!is20w22aServerOrHigher) {
			for (MethodNode gmCandidate : gameClass.methods) {
				if (gmCandidate.name.equals("<init>")) {
					gameConstructor = gmCandidate;

					if (gameMethodQuality < 1) {
						gameMethod = gmCandidate;
						gameMethodQuality = 1;
					}
				}

				if (type == EnvType.CLIENT && !isApplet && gmCandidate.name.equals("run")) {
					MethodInsnNode potentialInitInsn = null;
					boolean hasFailedToStartLog = false;

					for (AbstractInsnNode insn : gmCandidate.instructions) {
						if (insn instanceof MethodInsnNode && potentialInitInsn == null) {
							MethodInsnNode methodInsn = (MethodInsnNode) insn;

							if (methodInsn.getOpcode() == Opcodes.INVOKEVIRTUAL && methodInsn.owner.equals(gameClass.name)) {
								potentialInitInsn = methodInsn;
							} else {
								break;
							}
						}

						if (insn instanceof LdcInsnNode && !hasFailedToStartLog) {
							if (potentialInitInsn == null) {
								break;
							}

							Object cst = ((LdcInsnNode) insn).cst;

							if (cst instanceof String) {
								String s = (String) cst;

								if (s.equals("Failed to start RubyDung")) {
									hasFailedToStartLog = true;
								}
							}

							if (!hasFailedToStartLog) {
								break;
							}
						}

						if (potentialInitInsn != null && hasFailedToStartLog) {
							for (MethodNode gm : gameClass.methods) {
								if (gm.name.equals(potentialInitInsn.name) && gm.desc.equals(potentialInitInsn.desc)) {
									gameMethod = gm;
									gameMethodQuality = 2;

									break;
								}
							}
						}
					}
				}

				if (type == EnvType.CLIENT && !isApplet && gameMethodQuality < 2) {
					int qual = 2;
					boolean hasLwjglLog = false;

					for (AbstractInsnNode insn : gmCandidate.instructions) {
						if (insn.getOpcode() == Opcodes.INVOKESTATIC && insn instanceof MethodInsnNode) {
							final MethodInsnNode methodInsn = (MethodInsnNode) insn;

							if ("currentThread".equals(methodInsn.name) && "java/lang/Thread".equals(methodInsn.owner) && "()Ljava/lang/Thread;".equals(methodInsn.desc)) {
								currentThreadNode = methodInsn;
							}
						} else if (insn instanceof LdcInsnNode) {
							Object cst = ((LdcInsnNode) insn).cst;

							if (cst instanceof String) {
								String s = (String) cst;

								if (s.startsWith("LWJGL Version: ") || s.startsWith("Backend library: ")) {
									hasLwjglLog = true;

									if ("LWJGL Version: ".equals(s) || "LWJGL Version: {}".equals(s) || "Backend library: {}".equals(s)) {
										qual = 3;
										lwjglLogNode = insn;
									}

									break;
								}
							}
						}
					}

					if (hasLwjglLog) {
						gameMethod = gmCandidate;
						gameMethodQuality = qual;
					}
				}
			}
		} else {
			gameMethod = findMethod(mainClass, (method) -> method.name.equals("main") && method.desc.equals("([Ljava/lang/String;)V") && isPublicStatic(method.access));
		}

		if (gameMethod == null) {
			throw new RuntimeException("Could not find game constructor method in " + gameClass.name + "!");
		}

		boolean patched = false;
		Log.debug(LogCategory.GAME_PATCH, "Patching game constructor %s%s", gameMethod.name, gameMethod.desc);

		if (type == EnvType.SERVER) {
			ListIterator<AbstractInsnNode> it = gameMethod.instructions.iterator();

			if (!is20w22aServerOrHigher) {
				moveBefore(it, Opcodes.RETURN);

				if (serverHasFile) {
					it.add(new VarInsnNode(Opcodes.ALOAD, 1));
				} else {
					it.add(new InsnNode(Opcodes.ACONST_NULL));
				}

				it.add(new VarInsnNode(Opcodes.ALOAD, 0));

				finishEntrypoint(type, it);
				patched = true;
			} else {
				Log.debug(LogCategory.GAME_PATCH, "20w22a+ detected, patching main method...");

				LdcInsnNode serverPropertiesLdc = (LdcInsnNode) findInsn(gameMethod, insn -> insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst.equals("server.properties"), false);

				moveBefore(it, serverPropertiesLdc);

				MethodNode serverStartMethod = findMethod(mainClass, method -> {
					if ((method.access & Opcodes.ACC_SYNTHETIC) == 0
							|| method.name.equals("main") && method.desc.equals("([Ljava/lang/String;)V")
							|| VERSION_25w14craftmine.test(gameVersion) && method.parameters.size() < 10) {
						return false;
					}

					final Type methodReturnType = Type.getReturnType(method.desc);

					return methodReturnType.getSort() != Type.BOOLEAN && methodReturnType.getSort() != Type.VOID && methodReturnType.getSort() == Type.OBJECT;
				});

				if (serverStartMethod == null) {
					Log.debug(LogCategory.GAME_PATCH, "Detected 20w22a");
				} else {
					Log.debug(LogCategory.GAME_PATCH, "Detected version above 20w22a");

					AbstractInsnNode previous = serverPropertiesLdc.getPrevious();

					while (true) {
						if (previous == null) {
							throw new RuntimeException("Failed to find static method before loading server properties");
						}

						if (previous.getOpcode() == Opcodes.INVOKESTATIC) {
							break;
						}

						previous = previous.getPrevious();
					}

					boolean foundNode = false;

					while (it.hasPrevious()) {
						if (it.previous() == previous) {
							if (it.hasPrevious()) {
								foundNode = true;

								it.previous();
							}

							break;
						}
					}

					if (!foundNode) {
						throw new RuntimeException("Failed to find static method before loading server properties");
					}
				}

				it.add(new InsnNode(Opcodes.ACONST_NULL));

				it.add(new InsnNode(Opcodes.ACONST_NULL));

				finishEntrypoint(type, it);

				if (serverStartMethod == null) {
					Log.debug(LogCategory.GAME_PATCH, "Server game instance has not be implemented yet for 20w22a");
				} else {
					final ListIterator<AbstractInsnNode> serverStartIt = serverStartMethod.instructions.iterator();

					MethodInsnNode dedicatedServerConstructor = (MethodInsnNode) findInsn(serverStartMethod, insn -> {
						if (insn instanceof MethodInsnNode && ((MethodInsnNode) insn).name.equals("<init>")) {
							Type constructorType = Type.getMethodType(((MethodInsnNode) insn).desc);

							if (constructorType.getArgumentTypes().length <= 0) {
								return false;
							}

							return constructorType.getArgumentTypes()[0].getDescriptor().equals("Ljava/lang/Thread;");
						}

						return false;
					}, false);

					if (dedicatedServerConstructor == null) {
						throw new RuntimeException("Could not find dedicated server constructor");
					}

					moveAfter(serverStartIt, dedicatedServerConstructor);

					serverStartIt.add(new InsnNode(Opcodes.DUP));
					serverStartIt.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Hooks.INTERNAL_NAME, "setGameInstance", "(Ljava/lang/Object;)V", false));
				}

				patched = true;
			}
		} else if (type == EnvType.CLIENT && isApplet) {
			FieldNode runDirectory = findField(gameClass, (f) -> isStatic(f.access) && f.desc.equals("Ljava/io/File;"));

			if (runDirectory == null) {
				Log.warn(LogCategory.GAME_PATCH, "Could not find applet run directory! (If you're running pre-late-indev versions, this is fine.)");

				ListIterator<AbstractInsnNode> it = gameMethod.instructions.iterator();

				if (gameConstructor == gameMethod) {
					moveBefore(it, Opcodes.RETURN);
				}

				it.add(new InsnNode(Opcodes.ACONST_NULL));
				it.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/fabricmc/loader/impl/game/minecraft/applet/AppletMain", "hookGameDir", "(Ljava/io/File;)Ljava/io/File;", false));
				it.add(new VarInsnNode(Opcodes.ALOAD, 0));
				finishEntrypoint(type, it);
			} else {
				ListIterator<AbstractInsnNode> it = gameConstructor.instructions.iterator();
				moveAfter(it, Opcodes.INVOKESPECIAL);
				it.add(new FieldInsnNode(Opcodes.GETSTATIC, gameClass.name, runDirectory.name, runDirectory.desc));
				it.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/fabricmc/loader/impl/game/minecraft/applet/AppletMain", "hookGameDir", "(Ljava/io/File;)Ljava/io/File;", false));
				it.add(new FieldInsnNode(Opcodes.PUTSTATIC, gameClass.name, runDirectory.name, runDirectory.desc));

				it = gameMethod.instructions.iterator();

				if (gameConstructor == gameMethod) {
					moveBefore(it, Opcodes.RETURN);
				}

				it.add(new FieldInsnNode(Opcodes.GETSTATIC, gameClass.name, runDirectory.name, runDirectory.desc));
				it.add(new VarInsnNode(Opcodes.ALOAD, 0));
				finishEntrypoint(type, it);
			}

			patched = true;
		} else {
			if (gameConstructor == null) {
				throw new RuntimeException("Non-applet client-side, but could not find constructor?");
			}

			ListIterator<AbstractInsnNode> consIt = gameConstructor.instructions.iterator();

			while (consIt.hasNext()) {
				AbstractInsnNode insn = consIt.next();
				if (insn.getOpcode() == Opcodes.PUTFIELD
						&& ((FieldInsnNode) insn).desc.equals("Ljava/io/File;")) {
					Log.debug(LogCategory.GAME_PATCH, "Run directory field is thought to be %s/%s", ((FieldInsnNode) insn).owner, ((FieldInsnNode) insn).name);

					ListIterator<AbstractInsnNode> it;

					if (gameMethod == gameConstructor) {
						it = consIt;
					} else {
						it = gameMethod.instructions.iterator();
					}

					if (currentThreadNode != null && VERSION_1_19_4.test(gameVersion)) {
						moveBefore(it, currentThreadNode);
					} else if (lwjglLogNode != null) {
						moveBefore(it, lwjglLogNode);

						for (int i = 0; i < 4; i++) {
							moveBeforeType(it, AbstractInsnNode.METHOD_INSN);
						}
					}

					it.add(new VarInsnNode(Opcodes.ALOAD, 0));
					it.add(new FieldInsnNode(Opcodes.GETFIELD, ((FieldInsnNode) insn).owner, ((FieldInsnNode) insn).name, ((FieldInsnNode) insn).desc));
					it.add(new VarInsnNode(Opcodes.ALOAD, 0));
					finishEntrypoint(type, it);

					patched = true;
					break;
				}
			}

			if (!patched && gameMethod != gameConstructor) {
				ListIterator<AbstractInsnNode> it = gameMethod.instructions.iterator();

				it.add(new InsnNode(Opcodes.ACONST_NULL));
				it.add(new VarInsnNode(Opcodes.ALOAD, 0));
				finishEntrypoint(type, it);

				patched = true;
			}
		}

		if (!patched) {
			throw new RuntimeException("Game constructor patch not applied!");
		}

		if (gameClass != mainClass) {
			classEmitter.accept(gameClass);
		} else {
			classEmitter.accept(mainClass);
		}

		if (isApplet) {
			Hooks.appletMainClass = entrypoint;
		}
	}

	private boolean hasSuperClass(String cls, String superCls, Function<String, ClassNode> classSource) {
		if (cls.contains("$") || (!cls.startsWith("net/minecraft") && cls.contains("/"))) {
			return false;
		}

		ClassNode classNode = classSource.apply(cls);

		return classNode != null && classNode.superName.equals(superCls);
	}

	private boolean hasStrInMethod(String cls, String methodName, String methodDesc, String str, Function<String, ClassNode> classSource) {
		if (cls.contains("$") || (!cls.startsWith("net/minecraft") && cls.contains("/"))) {
			return false;
		}

		ClassNode node = classSource.apply(cls);
		if (node == null) return false;

		for (MethodNode method : node.methods) {
			if (method.name.equals(methodName) && method.desc.equals(methodDesc)) {
				for (AbstractInsnNode insn : method.instructions) {
					if (insn instanceof LdcInsnNode) {
						Object cst = ((LdcInsnNode) insn).cst;

						if (cst instanceof String) {
							if (cst.equals(str)) {
								return true;
							}
						}
					}
				}

				break;
			}
		}

		return false;
	}

	private Version getGameVersion() {
		try {
			return Version.parse(gameProvider.getNormalizedGameVersion());
		} catch (VersionParsingException e) {
			throw new RuntimeException(e);
		}
	}

	private static VersionPredicate createVersionPredicate(String predicate) {
		try {
			return VersionPredicateParser.parse(predicate);
		} catch (VersionParsingException e) {
			throw new RuntimeException(e);
		}
	}
}
