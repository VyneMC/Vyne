# Third-Party Notices

Vyne bundles the following third-party libraries and code. Each entry lists what is bundled, why, and its license.

## Bundled in `vyne-loader.jar`

Shadowed from `gradle/dependencies.gradle`:

| Library | Version | License | Use |
|---|---|---|---|
| ASM (core, analysis, commons, tree, util) | 9.7.1 | BSD-3-Clause | Bytecode reading, remapping and generation |
| Sponge Mixin (Fabric fork) | 0.17.1+mixin.0.8.7 | MIT | Mixin engine |
| MixinExtras (fabric) | 0.4.0 | MIT | Extra mixin injection features |
| tiny-remapper | 0.10.3 | LGPL-3.0 | Mapping application / remapping |
| mapping-io | 0.6.1 | Apache-2.0 | Mapping file parsing |
| class-tweaker | 0.3.0-beta.2 | Apache-2.0 | Access widening and class tweaks |
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
| `net/fabricmc/loader/**` | Patched fork of [Fabric Loader](https://github.com/FabricMC/fabric-loader). Original headers are kept in forked files. | Apache-2.0 |
| `net/minecraftforge/**`, `net/neoforged/**` | Vyne's own implementations of the parts of the Forge/NeoForge API surface Vyne must provide itself (loader integration, registries, events). Not copied source. Any copied source must keep its original header and be listed here. | GPL-3.0 (Vyne) |
| `src/neoforge/` | Vyne's copy of the [NeoForge](https://github.com/neoforged/NeoForge) 21.1.265 API source and resources (from NeoForge's published sources jar). NeoForge's own headers are kept in every file. Vyne changes it to run on Vyne's hook layer. | LGPL-2.1-only (`Licenses/LICENSE_NEOFORGE`) |
| `src/neoforge/java/net/neoforged/neoforge/common/LenientUnboundedMapCodec.java` | Part of NeoForge's source, originally from Microsoft's DataFixerUpper | MIT (header kept) |
| `src/main/resources/vyne/hooks/neoforge-21.1.265/` | `accesstransformer.cfg` is NeoForge's access-transformer list; `surface.tsv` is generated from NeoForge's patched classes (the interfaces, fields, methods and access NeoForge adds to vanilla) | LGPL-2.1-only |
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
| Mojang mappings and the Minecraft client jar | Mojang's official distribution | Mojang EULA / mapping license | Downloaded into `.vyne/mappings/` for remapping. Never bundled. |
| Intermediary mappings | maven.fabricmc.net | CC0-1.0 | Downloaded into `.vyne/mappings/`. |
| Minecraft's `ascii.png` font sheet | The installed Minecraft jar | Mojang EULA | Read from the game jar at runtime to draw Vyne's loading screen in Minecraft's font. Never bundled. |

Launchwrapper (`net.minecraft:launchwrapper`) is **not** bundled. Vyne compiles against it only for Fabric Loader's legacy launch adapter, which Minecraft 1.21.1 does not use.

Vyne itself is licensed under GPL-3.0 (`LICENSE`). This file is a good-faith attribution list, not legal advice; consult the upstream license texts for exact terms.
