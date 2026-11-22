# Third-Party Notices

Vyne bundles the following third-party libraries and code. Each entry lists what is bundled, why, and its license.

## Bundled in `vyne-loader.jar`

Shadowed from `gradle/dependencies.gradle`:

| Library | Version | License | Use |
|---|---|---|---|
| ASM (core, analysis, commons, tree, util) | 9.10.1 | BSD-3-Clause | Bytecode reading, remapping and generation |
| Sponge Mixin (Fabric fork) | 0.17.5+mixin.0.8.7 | MIT | Mixin engine |
| MixinExtras (fabric) | 0.5.5 | MIT | Extra mixin injection features |
| tiny-remapper | 0.15.0 | LGPL-3.0 | Mapping application / remapping |
| mapping-io | 0.9.1 | Apache-2.0 | Mapping file parsing |
| class-tweaker | 0.3.0 | Apache-2.0 | Access widening and class tweaks |
| jbsdiff | 1.0 | BSD-2-Clause | Applying Vyne's binary patches to vanilla classes |
| Apache Commons Compress | 1.21 | Apache-2.0 | Compression used by jbsdiff |
| Sat4j (core, pb) | 2.3.6 | EPL-1.0 / LGPL-2.1 (dual) | Constraint solving for version conflict resolution |
| Gson | 2.10.1 | Apache-2.0 | JSON parsing (`fabric.mod.json` etc.) |
| toml4j | 0.7.2 | MIT | TOML parsing (`mods.toml`) |
| Night Config (core, toml) | 3.8.3 | LGPL-3.0 | Forge/NeoForge config format compatibility |
| TypeTools | 0.6.3 | Apache-2.0 | Generic type resolution |
| Apache Commons Lang3 | 3.14.0 | Apache-2.0 | Utilities |
| Apache Maven Artifact | 3.8.5 | Apache-2.0 | Version and version-range handling for NeoForge mod metadata |
| SLF4J | 2.0.9 | MIT | Logging API |
| Log4J (api, core, slf4j2-impl) | 2.22.0 | Apache-2.0 | Logging implementation |

## Vendored or derived source

| Code | Origin | License |
|---|---|---|
| `net/fabricmc/loader/**` | Patched fork of [Fabric Loader](https://github.com/FabricMC/fabric-loader) 0.20.1. Original headers are kept in forked files. | Apache-2.0 |
| `src/fml/java/cpw/mods/modlauncher/log/TransformingThrowablePatternConverter.java` | Vyne's own stand-in for the one modlauncher method NeoForge's crash reports call; Vyne's FML replaces modlauncher. Not copied source. | GPL-3.0 (Vyne) |
| `src/neoforge/` | Vyne's copy of the [NeoForge](https://github.com/neoforged/NeoForge) 21.1.265 API source and resources (from NeoForge's published sources jar). NeoForge's own headers are kept in every file. Vyne changes it to run on Vyne's hook layer. | LGPL-2.1-only (`Licenses/LICENSE_NEOFORGE`) |
| `src/neoforge/java/net/neoforged/neoforge/common/LenientUnboundedMapCodec.java` | Part of NeoForge's source, originally from Microsoft's DataFixerUpper | MIT (header kept) |
| `src/main/resources/vyne/hooks/neoforge-21.1.265/` | `accesstransformer.cfg` is NeoForge's access-transformer list; `surface.tsv` is generated from NeoForge's patched classes (the interfaces, fields, methods and access NeoForge adds to vanilla) | LGPL-2.1-only |
| `src/hooks/generated/` | Vyne mixins generated from NeoForge 21.1.265's patched classes (decompiled with Vineflower): the bodies of the methods and the values of the fields NeoForge adds to vanilla. Each file says which class it comes from. | LGPL-2.1-only |
| `data/hooks/` | Generated from NeoForge's patched classes and Fabric API's mixins (lists of names and signatures) | Facts about LGPL-2.1 / Apache-2.0 code |
| `Licenses/` | Original license texts for Fabric, Forge, NeoForge and Vyne | n/a |

## Images

| Files | Origin | Terms |
|---|---|---|
| `ui/icon/**`, `assets/fabricloader/icon.png` | Fabric Loader | Apache-2.0 |
| `vyne/assets/launchers/prism.png`, `multimc.png`, `modrinth.png`, `curseforge.png` | Logos of Prism Launcher, MultiMC, Modrinth and CurseForge | Trademarks of their owners. Used only to label each launcher in Vyne's installer; no endorsement implied. |
| `vyne/assets/vyne_logo.png`, `vyne_banner.png` | Vyne | GPL-3.0 |

## Downloaded or read at runtime (not bundled)

| Component | Source | License | How Vyne uses it |
|---|---|---|---|
| NeoForge universal jar | maven.neoforged.net | LGPL-2.1 | Downloaded on first start into `.vyne/references/`. A translated copy is kept in the local cache (`.vyne/cache/`) on the player's machine and is never redistributed by Vyne. |
| NeoForge's libraries: FancyModLoader `loader` 4.0.45, `bus` 8.0.5, `mergetool` 2.0.0 (`api`), `securejarhandler` 3.0.8; on servers also JLine 3.20.0 and TerminalConsoleAppender 1.3.0 | maven.neoforged.net, Maven Central | LGPL-2.1 (NeoForge's), BSD-3-Clause (JLine), MIT (TerminalConsoleAppender) | Downloaded into `.vyne/libraries/` when a pack has NeoForge mods, checked against the repository's SHA-1. Never bundled. |
| NeoForge's installer (only with `-Dvyne.hooks=false`) | maven.neoforged.net | LGPL-2.1 | Run once on the player's machine; the jars it produces are kept in `.vyne/neoforge/`. Never bundled. |
| Mojang mappings and the Minecraft client jar | Mojang's official distribution | Mojang EULA / mapping license | Downloaded into `.vyne/mappings/` for remapping. Never bundled. |
| Intermediary mappings | maven.fabricmc.net | CC0-1.0 | Downloaded into `.vyne/mappings/`. |
| Minecraft's `ascii.png` font sheet | The installed Minecraft jar | Mojang EULA | Read from the game jar at runtime to draw Vyne's loading screen in Minecraft's font. Never bundled. |

[Vineflower](https://github.com/Vineflower/vineflower) (Apache-2.0) is used only by the `decompileHookClasses` build task and is not bundled.

Launchwrapper (`net.minecraft:launchwrapper`) is **not** bundled. Vyne compiles against it only for Fabric Loader's legacy launch adapter, which Minecraft 1.21.1 does not use.

Vyne itself is licensed under GPL-3.0 (`LICENSE`). This file is a good-faith attribution list, not legal advice; consult the upstream license texts for exact terms.
