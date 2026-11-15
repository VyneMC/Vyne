# Getting Started

> **Alpha notice.** Vyne 0.1.0-alpha is under active development. The installer UI, launcher
> integration, and loader wiring all still change between patch releases. Expect occasional rough
> edges — and please report them.

This guide walks you from "nothing installed" to "NeoForge and Fabric mods running side by side
in one world" in about ten minutes.

## 1. What you need

- **Java 21.** Any JDK works (Temurin, Zulu, GraalVM — all tested). Java 17 will boot but will
  refuse some NeoForge 1.21 mods that compile for 21 release targets.
- **Minecraft 1.21.1.** Vyne currently targets this version exclusively. Other 1.21.x releases
  may work but aren't in CI.
- **A launcher.** Vyne supports:
  - Prism Launcher (recommended — Vyne ships a Prism-layout maven artifact natively).
  - The vanilla Minecraft launcher via the provided `VanillaLauncherInstaller`.
  - A bare `java -jar vyne-loader.jar` launch (advanced; see `src/main/java/dev/stolym/vyne/core/VyneLauncher.java`).

## 2. Install

### Prism (recommended)

1. Download `vyne-loader-<version>.jar` from the [releases page](https://github.com/VyneMC/Vyne/releases).
2. Run `VyneInstallerGui` — double-click the jar or `java -jar vyne-loader-<version>.jar`.
3. Pick "Prism" as the target, choose your instance, and let it wire up. It will:
   - Copy the loader to Prism's `meta/com.stolym.vyne/vyne-loader/<version>/`.
   - Register a Prism component so the instance edit screen lists "Vyne".
   - Leave your existing Fabric API install untouched — Vyne runs on top of it.
4. Start the instance. The title screen should show a `Vyne 0.1.0-alpha` banner in the corner.

### Vanilla launcher

1. Same jar download as above.
2. Run the installer, choose "Vanilla launcher" — it creates a profile pointing at Vyne's jar
   under `.minecraft/versions/vyne-<mcver>`.
3. Launch it from the launcher's Play menu.

## 3. Drop in mods

The mods folder Vyne scans is **the same** mods folder Fabric already uses
(`<instance>/.minecraft/mods/` or Prism's `mods/`). Any of these work:

- **Fabric mods** — no change, they load natively.
- **NeoForge mods** — drop the `.jar` in; Vyne detects it, runs its lifecycle events, and
  registers its content under the Fabric-hosted registries.
- **Legacy Forge mods** — same. Expect `EMULATED_PARTIAL` tier — most content registers,
  deep-rendering features may fall back.

Don't worry about loader-specific subfolders. If the file is a valid mod jar, Vyne will
attempt to load it.

## 4. Verify it worked

A healthy first boot produces log lines like this (trimmed):

```text
FORGE-LOAD » [VCL] Dispatching FML lifecycle events...
FORGE-LOAD » [Vyne-Bus] Dispatched 91 registration event(s).
FORGE-LOAD »   ✓ [RegDiag] Registration clean — all tracked ids registered (...)
FORGE-LOAD » [ClientExtensions] 3D item renderers wired: 7 installed, 0 skipped
```

If you see the first three and a non-zero "installed" count on the fourth, you're fully set up:
FML events fired, registries look healthy, and 3D items (e.g. Traveler's Backpack) have their
custom renderers wired into Fabric.

## 5. Check mod status in-game

Run `/vyne status` from the in-game chat to get a one-screen summary:

```text
Vyne 0.1.0-alpha
- loaders: 1 host (Fabric), 3 emulated (NeoForge)
- mods: 94 loaded, 0 failed, 2 partial
- registries: 1423 items / 517 blocks / 8 menus
- containment: 0 runtime catches this session
```

## 6. Common first-boot issues

| Symptom                                  | Fix                                                          |
|------------------------------------------|--------------------------------------------------------------|
| Items appear invisible in hand           | A BEWLR didn't register. Check `[ClientExtensions] skipped`. |
| Creative tab icon missing                | Same — the icon is drawn through the same BEWLR path.        |
| `NoSuchMethodError` on a NeoForge class  | Open an issue — the shim for that method isn't in yet.       |
| Random `AbstractMethodError` on placed blocks | Harmless; absorbed by containment. Log it but keep playing. |

Ready to go deeper? Continue with [Loaders](loaders.md) to understand which mod features are
fully emulated vs. still partial.
