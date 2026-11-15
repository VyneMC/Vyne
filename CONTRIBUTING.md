# Contributing to Vyne

Thanks for your interest in Vyne! It's an early-alpha project, so there are plenty of ways to help, and not all of them involve code.

## Ways to help

- **Test mods and report results.** The most valuable contribution right now. Use the [mod incompatibility form](https://github.com/VyneMC/Vyne/issues/new/choose) and attach `logs/latest.log` plus anything in `.vyne/reports/`.
- **Fix bugs**, ideally with a regression test or a smoke scenario that proves the fix.
- **Add tests**, especially for the Guard (`containment/`), the transformation gate and the mapping code.
- **Improve the docs.** If something confused you, it will confuse the next person too.

For anything bigger than a small fix, **open an issue first** and describe what you want to change and why. If it doesn't fit the direction of the project, it's better to find out before you've written the code.

## Setting up a dev environment

Requirements: **JDK 21** and Git. Gradle is provided by the wrapper.

```bash
git clone https://github.com/VyneMC/Vyne.git
cd Vyne
./gradlew build       # compile, unit tests, class-load check, builds build/libs/vyne-loader-<version>.jar
./gradlew runServer   # boot a dev dedicated server from run/ with the built jar
```

The first launch downloads Minecraft, the mappings and the NeoForge API jar, then remaps them, so it is slow. Later launches use the cache under `.vyne/cache/`.

Any IDE with Gradle support works. In IntelliJ IDEA, open the folder and import it as a Gradle project.

### Tests

| Command | What it does | Time |
|---|---|---|
| `./gradlew test` | Unit tests | seconds |
| `./gradlew check` | Unit tests + `classLoadCheck` (loads every class in the built jar) | ~1 min |
| `./gradlew smokeServer_<scenario>` | Boots a real dedicated server with a mod set and checks the log | minutes |
| `./gradlew smokeTest` | All smoke scenarios | ~15 min |

Smoke scenarios need third-party mod jars, which aren't part of the repository: put them in `smoke/scenarios/<name>/mods/` (scenario names are listed in the [playtesting guide](docs/playtest.md#7-automated-testing-for-contributors)).

CI runs `./gradlew build` on every pull request, and it must pass before merging.

### Debugging tips

- `-Dvyne.debug.loaderAudit=true` logs which class loader defines each class.
- `-Dvyne.gate.failOpen=false` makes a failed bytecode transform fail loudly instead of passing the original bytes through.
- Deleting `.vyne/` in the run folder resets all Vyne caches and state.

More properties are listed in the [playtesting guide](docs/playtest.md#6-useful-system-properties-put-before--jar).

## Where things live

| Path | Contents |
|---|---|
| `dev.stolym.vyne.core` | Launcher and boot sequence |
| `dev.stolym.vyne.vcl` | Transformation gate, remapping, metadata index |
| `dev.stolym.vyne.loaders` | Mod discovery and per-loader handling |
| `dev.stolym.vyne.registry` | Registry bridging between NeoForge and the Fabric host |
| `dev.stolym.vyne.containment` | The Guard: execution gate, triage, mod state |
| `dev.stolym.vyne.world` | World snapshots and world-safety checks |
| `net.fabricmc.loader` | Patched Fabric Loader fork; keep changes minimal and well-commented |
| `net.neoforged`, `net.minecraftforge` | Hand-written (Neo)Forge seams; most of the API comes from the real NeoForge jar |

Design notes and roadmaps are in [`docs/dev/`](docs/dev).

## Code style

- Java 21, 4-space indentation, no tabs. The vendored Fabric Loader code keeps its upstream tab style; don't reformat it.
- Keep methods focused and short. Don't add an interface or abstraction unless something actually needs it.
- Keep source files free of comments: let clear names and small methods carry the meaning, and put design explanations in `docs/dev/`. The exceptions are license headers, which must stay, and Javadoc on the public `vyne-api` surface.
- Log through the logger, not `System.out`/`System.err`, and use the right level (`ERROR` for errors, `WARN` for warnings).
- Don't add a dependency without discussing it in an issue first.

## Commits and pull requests

- Keep commits focused, with short, descriptive messages in the imperative: `Add TOML parser for NeoForge metadata detection`, not `fix stuff`.
- Fill in the pull request template: what changed, how you tested it.
- Update docs when user-facing behaviour changes.

## Not accepted right now

- New loader targets beyond Fabric, NeoForge and Forge.
- Changes to the public `vyne-api` surface without prior discussion (it isn't stable yet).

## License

By contributing, you agree that your contributions are licensed under the project's [GPL-3.0](LICENSE) license.
