<p align="center">
  <img src=".github/assets/vyne_banner.png" alt="Vyne" width="100%" />
</p>

<p align="center">
  <a href="https://github.com/VyneMC/Vyne/actions/workflows/ci.yml"><img alt="CI" src="https://github.com/VyneMC/Vyne/actions/workflows/ci.yml/badge.svg"></a>
  <a href="LICENSE"><img alt="GPL-3.0" src="https://img.shields.io/badge/license-GPL--3.0-blue"></a>
</p>

# Vyne

Vyne is an experimental Minecraft 1.21.1 modloader that runs NeoForge mods next to Fabric mods in the same game. It is built on a patched Fabric Loader: Fabric mods load normally, and NeoForge mods are remapped at load time and connected to the game through the real NeoForge API.

**It is early.** Vyne can boot a server with small NeoForge mods today, but most content mods don't load yet (see below). Don't use it on a world you care about.

## Current state

**Dedicated server** (smoke suite, 2026-11-14; Vyne `0.1.0-alpha`, Minecraft 1.21.1, Java 25):

| Mods | Result |
|---|---|
| No mods | Boots. First start takes about a minute while Vyne downloads and prepares mappings. |
| Fabric API 0.116.17 + Mouse Tweaks 2.26 (Fabric) | Boots |
| Mouse Tweaks 2.26.1 (NeoForge) | Boots |
| CraftPresence 2.7.1 + UniLib 1.2.1 (NeoForge mod + library) | Boots |
| Fabric and NeoForge mods above, together | Boots |
| Test mod with a mixin into vanilla code (`src/mixinFixture`) | Boots, mixin applies |

**Client** (tested by hand, 2026-11-14): CraftPresence + UniLib + Mouse Tweaks (NeoForge), with and without Fabric API, reach the title screen and initialise. CraftPresence connects to Discord and Mouse Tweaks registers its handlers. Playing in a world with them has not been tested yet.

**Content mods** such as Comforts, Farmer's Delight, Waystones and Create failed at startup on 2026-11-08 because their mixins target vanilla code by Mojang names. Vyne now translates those mixins, but these mods have not been retested since. Expect most content mods to fail until the NeoForge game hooks are in place; see the [heavy-mod roadmap](docs/dev/heavy-mod-roadmap.md) and the [kernel architecture](docs/dev/kernel-architecture.md).

What's there on the client:

- A Vyne loading screen that opens with the game window and shows remapping, mod loading and memory use, one tile per mod.
- A **Mods** button in Options that lists every loaded mod with its loader and opens NeoForge config screens.
- NeoForge access transformers and key mappings are applied.

## Trying it

You need Java 21 or newer.

**Server:** download `vyne-loader-<version>.jar` from [Releases](https://github.com/VyneMC/Vyne/releases) into an empty folder, then run:

```bash
java -jar vyne-loader-<version>.jar --install --server
./start.sh          # start.bat on Windows
```

The installer downloads the vanilla 1.21.1 server jar and writes the start scripts. Put mods in `mods/`.

**Client:** run `java -jar vyne-loader-<version>.jar` to open the installer, and pick Prism Launcher, Modrinth App, CurseForge or the vanilla launcher. Vyne is installed as the instance's loader. It isn't a mod, so putting the jar in `mods/` does nothing.

If something breaks, an issue with your `logs/latest.log` and mod list helps a lot. There's a [form for mod incompatibilities](https://github.com/VyneMC/Vyne/issues/new/choose).

## Building

```bash
git clone https://github.com/VyneMC/Vyne.git
cd Vyne
./gradlew build      # jar ends up in build/libs/
```

Vyne compiles against the real Minecraft jar with the names the game uses at runtime, Fabric's Intermediary names (`class_310`, `method_1523`); `./gradlew minecraftRuntimeJar` builds it from Mojang's jar and Fabric's mappings.

**Planned change:** Vyne will move to Mojang's names at runtime, so logs and Vyne's own code become readable; Fabric mods will then be converted once at load instead of NeoForge mods. Details: [unified platform design](docs/dev/unified-platform.md).

**How NeoForge mods run today:** every pack with NeoForge mods, with or without Fabric mods, runs on NeoForge's patched Minecraft behind Vyne's loader. By default Vyne patches vanilla itself with its own copy of NeoForge's patches and its own copy of the NeoForge API; `-Dvyne.hooks=false` uses NeoForge's installer instead (kept in `.vyne/neoforge/`). In mixed packs, Fabric API runs on that game through Vyne's patch layer, which sends Fabric API's hooks through NeoForge's own events and call sites. Packs with only Fabric mods run on plain vanilla, as with Fabric Loader. Legacy Forge mods (`net.minecraftforge`) are not supported. See [plan.md](plan.md).

`./gradlew build` runs the unit tests and a check that every class in the jar loads. There are also end-to-end tests that boot real servers. See the [playtesting guide](docs/playtest.md#7-automated-testing-for-contributors) and [CONTRIBUTING.md](CONTRIBUTING.md).

## How it works

Every mod, Fabric or NeoForge, is loaded by Fabric's class loader, so they can see each other's classes. The first time Vyne sees a NeoForge mod, it remaps the whole jar exactly from Mojang names to the Intermediary names the game uses, against NeoForge's patched game, and saves the result under `.vyne/cache/converted/`. Later starts load the saved jar directly. NeoForge mods are run by NeoForge's own mod loader (FML) on Vyne's class loader, with the real NeoForge API.

Vyne also catches exceptions thrown by some NeoForge event listeners and rendering hooks, and can disable the mod responsible instead of crashing. This part is still rough.

More detail: [design notes](docs/dev/unified-knot.md), [loader support](docs/loaders.md), [all docs](docs/README.md).

## License

GPL-3.0, see [LICENSE](LICENSE). Vyne includes a patched copy of [Fabric Loader](https://github.com/FabricMC/fabric-loader) (Apache-2.0) and downloads [NeoForge](https://github.com/neoforged/NeoForge) at runtime. Other bundled libraries are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Made by [Ryvione](https://ryvione.dev) / [Stolym Studios](https://stolymstudios.vercel.app). Not affiliated with Mojang, Microsoft, Fabric or NeoForged.
