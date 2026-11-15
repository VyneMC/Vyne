# Hook inventory

The hook inventory lists every spot in vanilla Minecraft that NeoForge's patches and Fabric API's mixins change. Vyne's own hook layer is written from it (plan, Phase 5), and comparing two inventories shows what a new NeoForge or Fabric API release changed (Phase 9).

## Running it

```
./gradlew hookInventory
```

Versions come from `gradle.properties` (`minecraft_version`, `hook_neoforge_version`, `hook_fabric_api_version`). The task:

1. runs NeoForge's installer for the client (through Vyne's `NeoForgeInstall`, cached under `build/hook-inventory/`) to get NeoForge's patched classes and the vanilla client with Mojang names;
2. downloads Fabric API from Fabric's Maven and the Intermediary and Mojang mappings;
3. writes the files below to `data/hooks/`.

After the first run (about 40 s for the installer), it takes about 10 s. The tool's code is in `src/tools/java` and is not part of the Vyne jar.

## Files

All tables are tab-separated, one row per change, sorted by class and member. Names are Mojang names.

**`neoforge-<version>.tsv`**: what NeoForge changes in vanilla.

| Column | Meaning |
|---|---|
| `class` | Vanilla class |
| `member` | Method (`name` + descriptor), field (`name:descriptor`) or interface. A lambda NeoForge renumbered is shown with its vanilla name and `(now ...)` |
| `change` | `method~` changed, `method+` added, `method-` removed, `field+` added field, `implements` added interface, `class+` class only in NeoForge's jar |
| `synthetic` | `yes` for lambdas and bridge methods |
| `neoforge_added` | Calls, field accesses and types from `net.neoforged` the change adds |
| `other_added` | Other references and constants the change adds |
| `removed` | References and constants the change removes from vanilla |

**`fabric-api-<version>.tsv`**: every Fabric API mixin injection into Minecraft: class, target method, injector (`Inject`, `Redirect`, `WrapOperation`, ...), the `@At` point, the Fabric API module and the mixin class.

**`overlap-...tsv`**: methods both change (same name and descriptor), with NeoForge's added references and the Fabric API modules involved. These are the spots where one Vyne hook has to serve both APIs.

**`summary-...txt`**: counts.

## How changes are detected

NeoForge's patched classes are recompiled, so their bytecode differs from vanilla even where the source is unchanged (different local variable slots, jumps to a shared `return`, renumbered lambdas). A method counts as changed only if what it does changed: the methods it calls, the fields it reads or writes, the types it creates or casts to, and its constants. Methods that differ only in layout are counted in the summary as "only recompiled" and not listed. Lambdas are paired by enclosing method and signature, in order; when the counts differ, identical lambdas are paired first.

## Known limits

- A change that only alters control flow (for example `>` to `>=`) without changing any call, field, type or constant is not detected. NeoForge's patches almost always add a call, so this is rare.
- Anonymous classes NeoForge adds shift the numbering of later anonymous classes in the same outer class (`Util$7` becomes `Util$8`). These show up as `method-` in one class and `class+` or `method+` in another instead of being paired.

## NeoForge 21.1.265 and Fabric API 0.116.17 (2026-11-15)

- NeoForge changes 938 vanilla classes: 1914 methods changed, 1240 added, 161 removed, 396 fields added, 93 classes given new interfaces, 8 new classes. 2466 further methods differ only by recompilation.
- Fabric API: 230 mixin classes, 186 Minecraft classes, 365 (class, method) targets.
- Both: 147 classes; 211 methods both change.
