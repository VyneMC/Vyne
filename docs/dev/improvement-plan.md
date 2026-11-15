# Vyne Improvement Plan

Project-wide checklist for getting Vyne from "it boots" to something people can install, use and contribute to (0.1.0-alpha, Minecraft 1.21.1, Fabric Loader 0.19.5). It complements the capability-focused [heavy-mod roadmap](heavy-mod-roadmap.md).

**Goal:** make Vyne user-friendly and dev-friendly, with a "drop it in and it works" install experience. This is a UX goal, not an architecture goal: Vyne stays Vyne (NeoForge/Forge mods on a Fabric host, plus the Guard).

**How to use this file:** work top to bottom. Each phase unblocks the next. Tick boxes as you go. Phases 0-3 come before anything else.

---

## Table of contents

0. [Repo hygiene (quick wins)](#phase-0--repo-hygiene-quick-wins)
1. [Decide the architecture, delete the loser](#phase-1--decide-the-architecture-delete-the-loser)
2. [Fix the gate scope](#phase-2--fix-the-gate-scope)
3. [Regression tests and golden mods](#phase-3--regression-tests-and-golden-mods)
4. [Harden the Guard](#phase-4--harden-the-guard)
5. [User experience](#phase-5--user-experience)
6. [Developer experience](#phase-6--developer-experience)
7. [Compatibility matrix and honest status](#phase-7--compatibility-matrix-and-honest-status)
8. [Licensing](#licensing)
9. [Docs consistency fixes](#docs-consistency-fixes)
10. [Things not to do yet](#things-not-to-do-yet)
11. [Suggested milestones](#suggested-milestones)

---

## Phase 0 - Repo hygiene (quick wins)

These take an afternoon and remove a lot of confusion for anyone opening the repo.

- [x] **Remove stray files from the repo root:**
  - `latest.log` (also contains DNS/IP output from the launcher's connectivity check, so don't publish it)
  - `jar_content.txt`
  - `server.properties`
  - the compiled `dev/stolym/vyne/registry/VyneRegistryInjector.class` at the root
  - the root-level `com/llamalad7/...java` and `dev/` folders (they look like extraction leftovers, not source)
- [x] **Add a `.gitignore`** covering `*.log`, `run/`, `build/`, `.gradle/`, `.vyne/`, `*.class` outside `build/`.
- [x] **Include the `gradle/` folder.** `build.gradle` applies `gradle/repositories.gradle`, `gradle/dependencies.gradle`, `gradle/run.gradle`, `gradle/bytecode.gradle`, `gradle/shadow.gradle`, but none of them were in the archive. A fresh clone must build with `./gradlew shadowJar`. Test this on a clean checkout.
- [x] **Stop shipping mock Minecraft classes.** (`shadow.gradle` now excludes `net/minecraft/**` and `JarCompositionTest` guards it; the mocks still sit in the main source set.)
  - `build.gradle` puts `src/mock/java` into the **main** source set.
  - `jar_content.txt` lists 69 `net/minecraft/class_*` entries. If those are your stubs, they are in the released jar.
  - Fix: move mocks to `src/test/java`, or into a separate `mocks` source set used only with `testImplementation`/`compileOnly`.
  - Verify: after the change, `jar tf build/libs/vyne-loader-*.jar | grep '^net/minecraft/'` should return nothing.
- [ ] **Do not vendor Fabric Loader twice.** There is a `fabric-loader/` directory *and* Fabric loader classes under `src/main/java/net/fabricmc/loader/...`. Pick one:
  - preferred: depend on Fabric Loader as a normal dependency (`compileOnly`), and only keep the specific classes you had to patch, clearly marked;
  - or: keep a fork as a git submodule with a documented patch list.
- [x] **One repo URL everywhere.** Everything points at `github.com/VyneMC/Vyne`.
- [x] **Check the `strip_comments.py` script.** `CONTRIBUTING.md` requires Javadoc on every public class and method. Make sure the script cannot delete Javadoc (it should only strip `//` and non-Javadoc block comments), or remove the script from the repo.

---

## Phase 1 - Decide the architecture, delete the loser

Your docs currently describe two different designs:

| Source | Says |
|---|---|
| `README.md`, `legacy-vcl-architecture.md`, `docs/` | NeoForge/Forge mods live in an **isolated `RemappingClassLoader`** (VCL), Fabric mods in Knot |
| `unified-knot.md`, `tasks.md`, the runtime log | **Unified Knot**: everything in `KnotClassLoader`, "abandon the `BridgeClassLoader`" |

- [x] **Make the decision in writing.** Chose the unified Knot, which matches the runtime log ("Initializing Unified Knot Environment"). Recorded in the "Decision" section of `unified-knot.md`.
- [ ] **Delete the old path.** Remove or clearly mark `deprecated`:
  - VCL / `RemappingClassLoader` / `VCLManager` code that is no longer on the boot path;
  - reflection that assumes the old bridge, for example the `DeferredRegister` lookup of an `ALL_REGISTERS` field on `KnotClassLoader` (this fails with `NoSuchFieldException` in the runtime log);
  - the isolated-classloader diagrams in `legacy-vcl-architecture.md`.
- [x] **Finish the transformation gate** before adding any new shims: tasks 2.2 and 2.3 in `tasks.md` (`VyneTransformationGate` in `KnotClassDelegate`, and the Intercept → Remap → Recompute Frames → Return cycle). Done 2026-10: gate wired into both `KnotClassDelegate` transform sites, with a scope rule and an on-disk remap cache.
- [ ] **Re-tick `tasks.md` honestly.** If a box is ticked, there should be a test or a log line proving it.
- [ ] **Write a current `docs/architecture.md`** describing the real boot sequence (the old VCL-era document is kept as `legacy-vcl-architecture.md`). Use the phases from the runtime log: `BOOT → DISCOVERY → DEPENDENCY → REGISTRY → HANDOVER TO KNOT → METADATA INDEXING`).

---

## Phase 2 - Fix the gate scope

Your log shows `VyneGate` transforming `yalter.mousetweaks.*`. MouseTweaks is a **Fabric** mod, and the docs say Fabric mods run natively with no emulation.

- [x] **Add an explicit rule:** only bytecode from mods classified as NeoForge/Forge goes through remapping. Classification comes from `VyneMetadataIndex.ClassInfo(loaderType, sourceJar)`, populated per mod jar by `HierarchyScanner.scan(path, loaderType)` at discovery. Classification should come from the mod's metadata (`fabric.mod.json` vs `META-INF/neoforge.mods.toml` / `mods.toml`), decided once at discovery, not per class.
- [x] **Log a warning if the rule is violated** (`[VyneGate] Refusing to transform <class> (owner mod is FABRIC-native; ...)`).
- [x] **Add a test:** `VyneTransformationGateTest` — Fabric/VYNE-native classes pass through unchanged, classes outside the index pass through, (Neo)Forge classes are remapped (structural superclass check).
- [ ] **Investigate the end of the log.** It stops during a transform ~22 seconds after the last render-thread message, then "Clipboard copy". Find out whether it hung, crashed, or you simply copied the log while it was running. If it hung, you have a deadlock or slow-transform bug to find. Try a thread dump (`jstack`) while it is stuck.

---

## Phase 3 - Regression tests and golden mods

Without these, every change is guesswork. Your own log already gives you the first two test cases.

### 3.1 Known bugs as tests

- [x] **CraftPresence + UniLib.** Root cause had two layers: (1) `net.neoforged.fml.ModList.loadedMods` was never seeded from discovery (fixed: `ModList.seedLoadedMods` in `VyneLauncher`), and (2) Vyne's `net.neoforged.fml.loading.*` shims were not visible to Knot (fixed: `KnotModClasspathBridge.addVyneJar()` + parent-first delegation for `net.fabricmc.*`/`dev.stolym.vyne.*`). UniLib crashed on `FMLEnvironment` NCDFE and CraftPresence misreported that as "UniLib required". `DependencyAnalyzer` now checks version-range satisfaction (`VersionConstraintMatcher`); tests in `DependencyAnalyzerVersionTest`.
- [ ] **`DeferredRegister` startup** with no reflection on `KnotClassLoader`. Test: register a block via `DeferredRegister` in a stub mod and assert it lands in the registry.

### 3.2 Golden mod set (integration)

Pick a small set of real mods and make them boot to the title screen as an automated check (a Gradle task using `runClient` in headless mode, or `runServer` for dedicated server).

> **Scaffolding in place:** `gradle/smoke.gradle` provides `classLoadCheck` (loads and links every class in the built jar; wired into `check`) and `smokeServer_<scenario>` tasks for the four golden combinations below — provision mod jars into `smoke/scenarios/<name>/mods/` and run `./gradlew smokeTest` (see the [playtesting guide](../playtest.md#7-automated-testing-for-contributors)).

| # | Combination | Why |
|---|---|---|
| 1 | Fabric API + 1 Fabric client mod (e.g. MouseTweaks) | Proves Fabric stays untouched |
| 2 | 1 simple NeoForge mod with no dependencies | Proves the basic remap + lifecycle |
| 3 | NeoForge mod + its NeoForge library dependency (CraftPresence + UniLib) | Proves cross-mod visibility |
| 4 | Mixed pack of 1-3 together | Proves coexistence |

- [ ] Store the list in `docs/compatibility.md` (see Phase 7) and reuse it as the test list.
- [ ] Run headless with `runServer` for CI; client rendering tests can stay manual.

### 3.3 Coverage gaps

Right now there are about 14 test files for ~690 classes. Add tests where the risk is highest:

- [ ] `VyneExecutionGate` (does it freeze the right mod, and only that mod?)
- [ ] `VyneModStateRegistry` (state transitions Running → Frozen, thread safety)
- [ ] `MappingComposer` edge cases (a test file exists; extend it with inner classes, lambdas, and methods with the same name but different descriptors)
- [ ] `ConflictEngine` (version ranges, missing required dependency, optional dependency)

---

## Phase 4 - Harden the Guard

The Guard is your most original feature. Make it trustworthy.

### 4.1 Table-driven triage test

`VyneFailureTriage.determineTier(Throwable, String)` is pure classification logic and is easy to test. Sketch (adapt to how `VynePhaseTracker` lets you set the phase; if it has no test hook, add a package-private setter or a `reset()` for tests):

```java
class VyneFailureTriageTest {

    record Case(VynePhaseTracker.Phase phase, boolean postFreeze,
                Throwable error, VyneFailureTriage.FailureTier expected) {}

    static Stream<Case> cases() {
        return Stream.of(
            new Case(Phase.DEPENDENCY, false, new NullPointerException(), TIER_1_ISOLATE),
            new Case(Phase.WORLD_LOAD, true,  new NullPointerException(), TIER_3_CLEAN_SHUTDOWN),
            new Case(Phase.ANY,        false, new OutOfMemoryError(),     TIER_4_EMERGENCY_HALT),
            new Case(Phase.ANY,        false, new StackOverflowError(),   TIER_4_EMERGENCY_HALT)
            // add one row per branch in determineTier
        );
    }

    @ParameterizedTest
    @MethodSource("cases")
    void classifiesByPhaseAndType(Case c) {
        // set phase + freeze state via test hook
        assertEquals(c.expected(), VyneFailureTriage.determineTier(c.error(), "test"));
    }
}
```

- [ ] One row per `if` in `determineTier`, so the design is locked in by tests.
- [ ] Add a test for "unknown exception, post-freeze, non-critical" so the Tier 2 default is explicit.

### 4.2 Be careful with what "contained" means

Freezing a mod after a failure can leave **half-registered state** (a block registered but its item missing, for example). Things to decide and document:

- [ ] What exactly does "Frozen" do? Disable listeners and ticks: yes. Unregister already-registered content: probably not possible. State this in the docs.
- [ ] What happens to blocks/items/entities from a frozen mod that already exist in a loaded world? (Placeholders? Kept as-is? Removed on next save?) Even if the answer is "not handled yet in 0.1", say so.
- [x] Wording: replace "World is SAFE" in the failure box with something like "Mod frozen. Vyne blocked further changes from it." Reserve strong wording for Tier 3/4 where you actually stop disk writes.

### 4.3 Snapshot / rollback

- [ ] Test that a startup snapshot really restores `level.dat` and `playerdata` (create world → snapshot → corrupt → restore → compare).
- [ ] Make restore instructions in the failure report copy-pasteable (exact path and command).

### 4.4 Guard without NeoForge emulation

- [ ] Make sure the Guard can be used on a **Fabric-only** instance with no NeoForge mods. It is your clearest, most immediate selling point and doesn't depend on emulation being finished.

---

## Phase 5 - User experience

The aim: a player installs Vyne, adds mods, and it works, with no special knowledge.

### 5.1 Install path (most important)

The README used to say: install Fabric Loader, drop `vyne-loader.jar` into `mods/`. But the runtime log shows the instance was launched with **`Main class: dev.stolym.vyne.core.VyneLauncher`** and a custom **"Vyne Loader" component** in Prism. That means users need launcher-level setup, not just a jar in `mods/`.

- [ ] **Decide the supported install path and make the README truthful.** Options:
  1. *Jar-in-`mods/` only* (best UX). Investigate whether Vyne's early hooks can be reached from a normal Fabric mod (pre-launch entrypoints, a mixin config plugin, or Fabric Loader's own plugin/hook mechanisms). Look at how other projects do early class transformation from inside `mods/` (for example the `preloading-tricks` project) for ideas. If some hooks are impossible this way, list exactly which ones and why.
  2. *Launcher component* (acceptable). Ship a Prism/MultiMC component (`patches/*.json` + jar) and an installer (your "GUI Installer" item), and document it step by step with screenshots.
- [ ] If you keep option 2, the README must not say "just drop the jar in mods/".
- [ ] Provide a one-page **"Install in Prism Launcher"** guide with the exact steps, and a **"Install in the vanilla launcher"** guide if supported.

### 5.2 Clear first-run behavior

- [ ] Print one short banner line on start: Vyne version, host loader version, Minecraft version, number of Fabric / NeoForge / Forge mods detected.
- [ ] If a required dependency is missing, say **which mod needs which dependency at which version**, in one line, before any stack trace.

### 5.3 Readable failures

Your failure box is good. Improve it:

- [ ] Put **what to do next** as the first thing users read (update mod X / install dependency Y / report at URL).
- [ ] For the CraftPresence case, the message should be something like: "CraftPresence needs UniLib 1.2.0+. UniLib 1.2.1 is installed but Vyne could not see it. This is a Vyne bug, please report it." (Distinguish **user error** from **Vyne bug** from **mod bug**; users care a lot about that.)
- [ ] Write reports to `.vyne/reports/` with a stable file name and print the path.

### 5.4 Log hygiene

The log shows every Vyne line printed two or three times:
`[INFO]: VYNE EARLY INIT » ...` and `[STDOUT]: [Vyne] VYNE EARLY INIT » ...`, and errors labelled `[INFO]` from `[STDERR]`.

- [ ] Use **one logger** (Log4j, which Fabric already routes) and stop `System.out.println` / `System.err.println` in Vyne code.
- [ ] Errors must log at ERROR level, warnings at WARN.
- [ ] Consider a `vyne.log` in `.vyne/` containing only Vyne lines, so bug reports don't need the whole game log.

### 5.5 Diagnostics for players

- [ ] `/vyne status` (exists): list every mod, its loader type, and its state (Running / Frozen / Failed).
- [ ] A `vyne-report.zip` command or button that bundles: latest log, mod list with versions, Vyne config, Vyne version. This is the single biggest quality-of-life feature for issue triage.

### 5.6 Performance

- [ ] Show how long Vyne's startup work took (indexing, remapping) in the banner. Then you can prove the pre-translation proposal actually helps.
- [ ] Cache remapped classes on disk (`.vyne/cache/`, keyed by mod hash + Vyne version) so second launches are fast. Invalidate on mod or Vyne version change.

---

## Phase 6 - Developer experience

### 6.1 Getting a contributor productive in 15 minutes

- [x] `CONTRIBUTING.md` gets a **"Set up a dev environment"** section: JDK 21, `./gradlew runClient`, where run configs live, how to attach a debugger, where logs land.
- [ ] A **`docs/architecture.md`** that is short (1-2 pages) and matches the code: boot phases, where the gate sits, where the Guard sits. Move the long diagrams into it.
- [ ] A **package map** (the README and CONTRIBUTING.md list some; make sure it matches reality, for example `dev.stolym.vyne.loaders`, `world`, `containment`, `vcl`).
- [ ] A **"How to add a missing NeoForge method"** walkthrough that uses a real example from your own bug list (`docs/guides.md` already links to this idea, make sure the example still works against the unified design).

### 6.2 Repo automation

- [x] **GitHub Actions**: build on JDK 21, run unit tests, upload the jar as an artifact (`.github/workflows/ci.yml`). Checkstyle still to do.
- [x] **Issue templates** (bug report, mod incompatibility, feature request). The mod-incompatibility template should ask for: mod name + version + download link, Vyne version, Minecraft version, `vyne.log`.
- [x] **Pull request template** with a checklist (tests added, docs updated, no new dependency without discussion).
- [ ] `CODEOWNERS` and labels (`loader`, `api`, `docs`, `packaging`, `good first issue`).

### 6.3 `vyne-api`

- [ ] Publish `vyne-api` to Maven with the version in a single place (it's `0.1.0-alpha` in three files now).
- [ ] Mark the API `@ApiStatus.Experimental` (or document the same) while it is unstable, so mod authors know what to expect.
- [ ] A sample mod repository: one Fabric mod using `vyne-api` to detect the loader, and one NeoForge mod showing the same call.

### 6.4 Code health

- [ ] Split the biggest classes. `RemappingClassLoader.java` is ~4,200 lines; `RegistryDatapackLookupFallback.java` is ~2,700. Aim for classes under ~800 lines with one job each.
- [ ] Keep Javadoc on public API only; internal comments should explain **why**, not what.
- [ ] Turn on `-Xlint` warnings gradually. Right now `-Xlint:-deprecation` and `-Xlint:-removal` are switched off, which hides upgrade problems.

---

## Phase 7 - Compatibility matrix and honest status

- [x] Replace the README percentages (Mod Detection 95%, NeoForge Emulation 82%, ...) with a **table of specific mods**:

```markdown
| Mod | Version | Loader | Status | Notes / issue |
|-----|---------|--------|--------|---------------|
| MouseTweaks | 2.26 | Fabric | Works | |
| UniLib | 1.2.1 | NeoForge | Works | library |
| CraftPresence | 2.7.1 | NeoForge | Broken | dependency shim, issue #NN |
```

- [ ] Statuses: **Works / Partial / Broken / Untested**, each with a Vyne version and date.
- [ ] Link the matrix from the README and the failure report ("see if your mod is listed").
- [ ] Add a **"Known limitations"** section that names concrete features not supported (services, capabilities, specific event types), not percentages.
- [x] Reconcile the docs: `docs/README.md` says Fabric is "Host: 100%", the main README says Fabric support is 90% in one table and 100% in another paragraph. Use one statement.

---

## Licensing

- [x] **Pick one license and use it everywhere.** Current state:
  - `README.md`: GPL-3.0
  - source headers: "Stolym Studios project, licensed under the GPL-3.0"
  - `CONTRIBUTING.md`: "contributions are made under the project's Apache 2.0 license"
- [x] Add the `LICENSE` file at the repo root (the README points to it).
- [x] Add a **`THIRD_PARTY_NOTICES.md`** listing bundled code and its license: Fabric Loader (Apache-2.0), MixinExtras, TypeTools (Apache-2.0), ASM, any Forge/NeoForge-derived code.
- [ ] Confirm that your `net.minecraftforge.*` and `net.neoforged.*` classes are **your own reimplementations of the API** and not copied source from Forge/NeoForge (those are licensed differently, so attribution and terms matter). If some are copied, keep the original headers and list them in the notices.
- [ ] Mojang mappings are downloaded at runtime (good). Keep it that way and don't bundle them.
- [ ] If you are unsure about compatibility between GPL and the bundled licenses, ask someone who knows software licensing. Treat this list as a to-do, not as legal advice.

---

## Docs consistency fixes

- [x] `README.md` architecture diagram shows `RemappingClassLoader` → gate → engine; update to the unified Knot design after Phase 1.
- [x] Install steps: make them match Phase 5.1 (README now says to use the installer, not `mods/`).
- [x] Contributing link: the README says `GUIDES.md`, the file is `docs/guides.md`.
- [x] Support links: one issue tracker URL, one site URL.
- [ ] Version strings: `0.1.0-alpha` appears in `build.gradle`, `gradle.properties`, docs; keep it in `gradle.properties` only and reference it.
- [x] Add a short **CHANGELOG.md** (Keep a Changelog format).

---

## Things not to do yet

- Don't add new shims unless there is a **failing mod** that needs them.
- Don't add new loader sandboxes (your `CONTRIBUTING.md` already says so; keep it).
- Don't promise "100%" anything. Prefer "tested with these mods".
- Don't take on big mods (Create, JEI) as goals until the golden set and the gate are stable. Keep them in Phase 5.2 of `tasks.md` as later targets.
- Don't rewrite the mapping system. It already composes 8,254 classes, so fix bugs in it instead.

---

## Suggested milestones

### Milestone A - "Clean and honest" (days)
Phase 0 complete, Phase 1 decision written and old path removed or marked deprecated, README/docs fixed (see "Docs consistency fixes").

### Milestone B - "Measurable" (1-2 weeks)
Phase 2 (gate scope rule + test), Phase 3.1 (both known bugs fixed with tests), Phase 4.1 (triage table test), CI running on every push.

### Milestone C - "Friendly" (2-4 weeks)
Phase 5.1 install path decided and documented, 5.2-5.4 done (banner, readable failures, single logger), `/vyne` report bundle, first compatibility matrix published.

### Milestone D - "Contributor-ready" (ongoing)
Phase 6 complete: dev setup guide, templates, `vyne-api` on Maven, sample mods. Then grow the matrix one mod at a time.

### Definition of done for a public 0.1.0-alpha release

- [ ] Fresh clone builds with one command
- [ ] Jar contains no mock or stray classes
- [ ] The golden mod set boots (or is honestly listed as Partial/Broken with an issue)
- [ ] One license, one repo URL, one install path, all documented
- [ ] Failure reports say what to do next and who is responsible (user / mod / Vyne)
- [ ] README says only what the code does today
