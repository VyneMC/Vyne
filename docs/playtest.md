# Playtesting Vyne — everything you need to know

Vyne is an **alpha** universal modloader: it runs Fabric mods natively and
emulates **NeoForge**/**Forge** mods on top of a Fabric-hosted Minecraft 1.21.1,
with a *Failure Containment* layer (the "Guard") that freezes misbehaving mods
instead of crashing the server. This page is the practical guide: install it,
playtest it, report it.

---

## 1. Build it

Requirements: **JDK 21**, Gradle wrapper (bundled).

```bash
./gradlew shadowJar
# → build/libs/vyne-loader-<version>.jar
```

`./gradlew check` runs the full test suite (unit tests, jar-hygiene, class-load
check). `./gradlew smokeTest` boots four real dedicated servers with real mods —
see §7.

## 2. Install it

**Recommended: the built-in installer.**

```bash
# GUI (opens in a desktop session when run without args):
java -jar vyne-loader-<version>.jar

# CLI:
java -jar vyne-loader-<version>.jar --install
```

The installer supports **PrismLauncher**, **Modrinth App** and **CurseForge
instances**: point it at the instance folder (the one containing
`mmc-pack.json` etc.) and it installs Vyne as a launcher component
(main class `dev.stolym.vyne.core.VyneLauncher`) plus start scripts.

**Dedicated server:** run the installer against a server instance folder, or
manually place `vyne-loader.jar` next to a **full** vanilla server jar named
`minecraft_server.1.21.1.jar` (it must be the extracted jar that bundles
`META-INF/libraries/` — the bare bootstrap jar will NOT boot) and start with:

```bash
java -jar vyne-loader.jar --server
```

**Do not** just drop the jar into `mods/` of a normal Fabric instance — that
path is not supported yet (see Known issues).

## 3. Add mods

Put mod jars into the instance's `mods/` folder, mixed freely:

- Fabric mods (with `fabric.mod.json`) run natively and are **never** transformed.
- NeoForge mods (`META-INF/neoforge.mods.toml`) and Forge mods
  (`META-INF/mods.toml`) are emulated.
- Vyne's own metadata format (`vyne.mod.json`) takes priority when present.
- Dependencies are checked **with version ranges**: `mod A requires unilib >= 1.2.0`
  passes with 1.2.1 installed, and disables A (with a clear log line) if the
  installed version doesn't satisfy the range.
- Jar-in-Jar (embedded) dependencies are detected.

## 4. What currently works / doesn't

Verified by the automated smoke suite (real mods, real dedicated servers):

| Scenario | Status |
|---|---|
| Fabric API + Fabric mods (e.g. MouseTweaks) | ✅ Works |
| Simple NeoForge mod (MouseTweaks-NeoForge) | ✅ Works |
| NeoForge mod + library (CraftPresence + UniLib) | ✅ Works |
| Mixed Fabric + NeoForge pack | ✅ Works |
| Real NeoForge API on the classpath (default since Phase 0) | ✅ Works |
| Mod-provided mixins (emulated mods) — core path | ✅ Works (`mixin-fixture` smoke scenario) |
| Client boot to title screen with CraftPresence + UniLib + Mouse Tweaks (NeoForge) | ✅ Works (tested by hand, 2026-11-14) — not automated |
| NeoForge config screens (`IConfigScreenFactory`), access transformers, key mappings | ✅ Wired — config screens open from the Mods screen |
| NeoForge content mods (Comforts, Farmer's Delight, Waystones, Create) | ❓ Failed on 2026-11-08 (Mojang-named mixin targets); mixin translation has landed since but they have not been retested. Most need NeoForge's game hooks, which are not implemented yet. See [the heavy-mod roadmap](dev/heavy-mod-roadmap.md) |
| FML config files (ModConfigSpec TOML) | ❌ Stub |
| Dist machinery (DistExecutor / client-only classes) | ❌ Stub — avoid client-heavy NeoForge mods for now |
| Old Forge (pre-1.20.2) | ⚠️ Thin support |

## 5. Directories & caches Vyne creates

Inside the game/instance folder:

| Path | Purpose |
|---|---|
| `logs/latest.log` | Full game log (Vyne lines are duplicated to stdout in some setups — known issue) |
| `.vyne/reports/` | Human-readable failure reports when the Guard freezes a mod |
| `.vyne/mappings/` | Downloaded Mojmap + Intermediary mapping files |
| `.vyne/references/` | The downloaded NeoForge universal jar |
| `.vyne/cache/converted/` | NeoForge/Forge mod jars translated once to Intermediary names; reused on every later start (delete to force re-translation) |
| `.vyne/cache/remap/` | Per-class cache for the few classes translated while the game runs |
| `.vyne/cache/realneo/` | Derived real-NeoForge-API jar |
| `.vyne/cache/dynamic/` | Rewritten whole-jar cache for the dynamic bridge engine |
| `.vyne/state/` (and friends) | Guard state: frozen/failed mod persistence |

Deleting `.vyne/` gives you a completely fresh Vyne state (mods and world untouched).

`config/vyne-loading-screen.properties` (created on first client start) sets the loading screen's colours and which panels are shown (memory, mod tiles, recent activity). Delete it to restore the defaults.

## 6. Useful system properties (put before `-jar`)

| Property | Default | Effect |
|---|---|---|
| `-Dvyne.hooks=false` | on | Patch the game with NeoForge's installer instead of Vyne's own patches |
| `-Dvyne.debug.loaderAudit=true` | off | Log which classloader defines each class — invaluable for bug reports about classloading |
| `-Dvyne.verbose=true` | off | More detailed dependency-resolution logging |
| `-Dvyne.dev=true` | off | Dev mode (used by smoke tests) |
| `-Dvyne.earlyWindow=false` | on | Don't open Vyne's loading window; Minecraft opens its own window as usual |

## 7. Automated testing (for contributors)

```bash
./gradlew check        # unit tests + classLoadCheck + jar composition guard
./gradlew smokeTest    # 6 dedicated-server scenarios (incl. mixin fixture and patch layer), ~15 min
./gradlew smokeServer_mixed            # a single scenario
./gradlew smokeServer_mixed -Psmoke.installer  # use NeoForge's installer instead of Vyne's patches
./gradlew smokeTest -Psmoke.timeout=900
```

Scenarios live in `smoke/scenarios/<name>/mods/`; forbidden log patterns
(`NoClassDefFoundError`, `VerifyError`, `VYNE CRITICAL FAILURE`, …) fail a run
even if the server starts. Logs land in `build/smoke/<scenario>/smoke.log`.

## 8. What to do when something breaks

1. **Read the failure box.** The Guard prints what to do next (update mod /
   install dependency / report bug) and writes `.vyne/reports/report-*.log`.
2. **Check the mod list** in the log under mod detection: the mod may have been
   *disabled* (dependency freeze) rather than crashed — search for
   `Disabling this mod`.
3. **Collect for a report:** `latest.log`, `.vyne/reports/`, your mod list with
   versions, Vyne version, and (if it's a classloading issue) a run with
   `-Dvyne.debug.loaderAudit=true`.
4. Distinguish the three failure owners the report tries to name: **user error**
   (missing/old mod), **mod bug**, **Vyne bug** (please report!).

## 9. Known issues (as of this build)

- **Log duplication:** Vyne lines appear both via the logger and `[STDOUT]` in
  some setups; error levels are not always right. Cosmetic but noisy.
- **Client-side emulation is untested territory:** no dist machinery yet, so
  client-only NeoForge classes can crash on a dedicated server and vice versa.
- **The mixin pipeline's core path is proven** by the `mixin-fixture` smoke
  scenario (a fixture mod with a Mojmap-targeted mixin injects into the server).
  Cross-mod mixin targets and client-only mixins are still unproven.
- **Config:** NeoForge config specs are stubs; mods that *require* config files
  at startup may fail.
- **Class visibility between emulated mods** relies on the unified Knot
  classloader; if a mod can't see its library mod, re-check that both jars are
  in `mods/` and the dependency version range is satisfied.
- **First boot is slow** (mapping download + translating every NeoForge mod
  jar once). Later boots reuse the translated jars. Delete `.vyne/cache/` if the
  cache ever looks stale.
- **Forge (pre-1.20.2) support is thin**; NeoForge 1.21.x is the priority target.
