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

## Vyne's copy of the NeoForge API

`src/neoforge/` is NeoForge 21.1.265's API source and resources, unchanged so far, with NeoForge's LGPL-2.1 headers. It compiles against `minecraftHooked` plus NeoForge's libraries (`gradle/neoforge.gradle`). It is not shipped yet: until Vyne's hooks run it, NeoForge-only packs use the temporary patched mode.
