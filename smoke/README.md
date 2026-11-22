# Smoke tests

Two layers of verification beyond the unit tests. Run from the repo root.

## 1. `./gradlew classLoadCheck` — fast (seconds, no game launch)

Loads **every class** in the built `vyne-loader` jar in an isolated class loader
and resolves the types in its fields, methods and constructors. Catches missing
classes and broken class hierarchies (`NoClassDefFoundError`,
`ClassCircularityError`, ...) caused by packaging mistakes.

Classpath used for resolution: compile + runtime dependencies plus the local
Minecraft server jar from `run/versions/` when present.

Known limits: loading with `initialize=false` does not run static initializers
or full bytecode verification, so a `VerifyError` can still appear at real
runtime. That is what the server smoke test covers.

Skip prefixes for classes whose signatures only resolve inside the remapped
runtime (Intermediary names): `-Psmoke.skip=dev.stolym.vyne.mixin.,...`.

## 2. `./gradlew smokeServer_<scenario>` — real (boots a dedicated server)

Boots a dedicated server with the built jar plus a scenario's mod set, waits
for the `Done (` line, sends `stop`, and fails on: timeout (180 s), a missing
"Done" line, non-zero exit, or forbidden log patterns (`VYNE CRITICAL FAILURE`,
`NoClassDefFoundError`, `VerifyError`, ...). The full log lands in
`build/smoke/<scenario>/smoke.log`.

Scenarios (the golden mod set from [`docs/dev/improvement-plan.md`](../docs/dev/improvement-plan.md) Phase 3.2):

| Scenario | Mod set | Proves |
|---|---|---|
| `smokeServer_fabric-only` | Fabric API + one Fabric client-ish mod | Fabric stays untouched |
| `smokeServer_neoforge-simple` | one simple NeoForge mod, no dependencies | basic remap + lifecycle |
| `smokeServer_neoforge-with-lib` | NeoForge mod + its library (CraftPresence + UniLib) | dependency visibility between NeoForge mods |
| `smokeServer_mixed` | Fabric + NeoForge mods together | coexistence in the unified Knot |
| `smokeServer_mixin-fixture` | the fixture mod only (built from `src/mixinFixture`) | NeoForge mod mixins, dist-aware init |
| `smokeServer_patch-layer` | Fabric API + the fixture mod | Fabric API events through NeoForge's hooks (fake player, beds, items, ingredients, payloads, menus) |
| `smokeServer_mixed-content` | Fabric API, REI, Architectury, Cloth Config, AppleSkin, Traveler's Backpack, Cardinal Components, Comforts | a realistic mixed pack on the patch layer |

`./gradlew smokeTest` runs all of them. `-Psmoke.secondBoot` boots each scenario twice and fails if the second boot
redoes first-launch work (remapping, building the patched game, downloads).

## 3. `./gradlew smokeClient_<scenario>` — the client (needs `xvfb-run`)

Boots the real client off-screen under Xvfb with the same scenario mods, an empty asset store and an offline
account, and passes once the title screen's textures are built and the client survives 15 more seconds. Fails on a
crash report or the forbidden log patterns. The client is heavy: run one scenario at a time. Log:
`build/smoke-client/<scenario>/smoke.log`.

### Provisioning a scenario

Third-party mod jars are **not committed**. Drop them in locally:

```
smoke/scenarios/<name>/mods/*.jar
```

The scenario task fails with a clear message if the folder is missing.
Everything except this README is gitignored, so logs and mod jars stay local.

## 3. Jar composition unit test

`JarCompositionTest` (in `src/test`) runs as part of `./gradlew test` and fails
the build if compile-only `net/minecraft/**` stubs leak into the shadow jar, or
if the manifest / GameProvider service / bootstrap jar are missing.