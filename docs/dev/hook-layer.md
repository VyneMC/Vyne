# Hook layer

Vyne patches vanilla Minecraft itself (see [plan.md](../../plan.md)). The hook layer is everything Vyne changes in vanilla so that Vyne's copies of the NeoForge API and of Fabric API can run on it. It has two parts.

## 1. Structure: the hook surface

The members NeoForge's API expects to find on vanilla classes:

- superclasses NeoForge changes (`Entity`, `Level`, `BlockEntity` extend `AttachmentHolder`; `MappedRegistry` extends `BaseMappedRegistry`; ...);
- interfaces NeoForge adds (`Block implements IBlockExtension`, `ItemStack implements IItemStackExtension`, ...);
- fields and methods NeoForge adds;
- access NeoForge widens, both through its access-transformer list and directly in its patches;
- abstract vanilla methods NeoForge gives a body.

This is data, not code: `src/main/resources/vyne/hooks/neoforge-<version>/surface.tsv` and `accesstransformer.cfg`. `dev.stolym.vyne.hooks.HookSurface` applies it to a class. The same data is used in two places:

- **Build time:** `./gradlew minecraftHookedJar` applies it to vanilla Minecraft (Mojang names). Vyne's copy of the NeoForge API (`src/neoforge/`) compiles against that jar. Methods get a body that throws `UnsupportedOperationException("Vyne hook not implemented yet: ...")`.
- **Runtime** (next step): a Vyne transformer applies it to the game's classes as they load. Methods that only override an extension interface's default method are left out at runtime, so the default applies until Vyne implements the override.

`surface.tsv` was generated once with `./gradlew hookSurface` (from NeoForge's patched classes compared with vanilla) and is Vyne's data from then on. For a new NeoForge version, regenerate it and review the diff.

For NeoForge 21.1.265: 6 superclasses, 91 interfaces, 272 fields, 853 methods, 830 access changes.

## 2. Behaviour: Vyne's hooks

The calls NeoForge's patches insert inside vanilla methods (fire `BlockEvent.BreakEvent` here, ask `IBlockExtension.isBed` there) and the bodies of the methods in the surface. These are Vyne mixins, one per spot, written from:

- the hook inventory (`data/hooks/`, see [hook-inventory.md](hook-inventory.md)): what NeoForge adds and removes in each vanilla method, and which Fabric API mixins target the same spot;
- NeoForge's source patches (in NeoForge's `userdev` jar), which show the same change as readable Java.

Where Fabric API also changes the spot, the Vyne hook fires Fabric API's event too (Phase 6).

Progress is measurable: every surface method still throwing "Vyne hook not implemented yet" is a hook to write, and the inventory lists every behaviour change.

## Hooks mode (in progress)

`-Dvyne.hooks=true` runs a NeoForge-only pack on Vyne's hook layer instead of NeoForge's patched game:

1. `VanillaGame` builds vanilla Minecraft with Mojang names for the side being launched (client or server jar plus Mojang's mappings), cached.
2. `HookedGame` applies the hook surface to the classes it touches. At runtime, methods that override an inherited method are left out (the inherited one keeps working), static initializers are never stubbed, and access rows only widen visibility and remove `final`.
3. The hooked classes, Vyne's NeoForge copy (`META-INF/vyne/neoforge.jar` inside the Vyne jar) and the NeoForge mods go through the same exact rename as patched mode and are served the same way, until runtime names become Mojang names (Phase 6b).
4. Behaviour hooks are mixins in `src/hooks/java` written with Mojang names, compiled against the hooked jar, renamed to runtime names at build time (`./gradlew remapHooksJar`) and applied only in hooks mode (`vyne.hooks.mixins.json`, `HooksMixinPlugin`).

First hook: `MainHooks` starts NeoForge's server lifecycle where NeoForge's own patch does.

**2026-11-15:** the NeoForge-only test server (Mouse Tweaks, CraftPresence, UniLib) reaches `Done` with 0 ERROR lines in hooks mode: vanilla Minecraft, Vyne's hook surface, 318 generated mixins and five hand-written hooks, Vyne's NeoForge copy. Most of NeoForge's behaviour changes inside vanilla methods (events) are not written yet, so gameplay events mostly do not fire; that is the next body of work.

## Generated hooks

The bodies of the methods NeoForge adds to vanilla, and the initial values of the fields it adds, are generated, then owned:

1. `./gradlew decompileHookClasses` decompiles the vanilla classes NeoForge changes, from NeoForge's patched game, with Vineflower (a development-only Gradle dependency of the `tools` source set).
2. `./gradlew generateHookBodies` writes one mixin per vanilla class into `src/hooks/generated/java` (config `vyne.hooks.generated.mixins.json`): each added method copied whole (`@Overwrite` of the surface stub, or merged when it overrides an inherited method), `@Shadow` for the members it uses, static field values in a `static {}` block, instance field values set when a constructor returns. The mixin extends the class's own superclass so inherited members resolve, and bare `this` becomes `((Target) (Object) this)`. It compiles against `minecraftHookedOpen`, the hooked jar with everything public, because mixin code is merged into the class at runtime.
3. Members whose copied code does not compile are listed in `src/hooks/generated/hook-exclusions.txt` (run the generator with `-PhookErrors=<javac output>` until the hooks compile); methods it cannot find in the source (mostly accessors of record components NeoForge adds) are listed in `build/hook-inventory/hook-unmatched.txt`. Both lists, plus constructors NeoForge adds and changes, are the hand-written backlog in `src/hooks/java`.

The generated files are committed and maintained as Vyne code; regenerate after a NeoForge update and review the diff.

## Vyne's copy of the NeoForge API

`src/neoforge/` is NeoForge 21.1.265's API source and resources, unchanged so far, with NeoForge's LGPL-2.1 headers. It compiles against `minecraftHooked` plus NeoForge's libraries (`gradle/neoforge.gradle`). It ships inside the Vyne jar and is used in hooks mode; until Vyne's hooks run it completely, NeoForge-only packs use the temporary patched mode by default.

## Updating NeoForge

Mods almost always declare a minimum NeoForge version (`[21.1.0,)`), so shipping the newest 21.1.x satisfies nearly every 1.21.1 mod; a mod that pins an upper bound is listed in the compatibility table.

Measured between NeoForge 21.1.233 and 21.1.265 (32 releases): 24 of 956 API source files changed and 4 were added (about 300 lines); 10 of 768 patched vanilla classes changed and 4 were added. An update is: regenerate the inventory and the surface, merge NeoForge's API changes into Vyne's copy, regenerate the generated hook bodies, review the hand-written hooks on the spots the diff lists (plan, Phase 9). A new Minecraft version is a port, as for every loader.
