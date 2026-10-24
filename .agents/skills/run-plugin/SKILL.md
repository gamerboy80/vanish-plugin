---
name: run-plugin
description: Build, test, and release Vanish for Bukkit, Fabric, Forge and NeoForge. Use when asked to build the plugin or mods, run tests, run a single test class, or produce release JARs.
---

Vanish produces four artifacts from one build: the Spigot/Paper plugin and Fabric, Forge and NeoForge
1.21.11 mods. "Running" it locally means compiling every module, executing the test suites, and
producing the shaded jars.

## Commands

### Windows agent terminal: prevent recurring Gradle hangs

Direct Gradle calls through the agent terminal have repeatedly hung, including after Gradle reports
completion. Apply this procedure to **every Gradle invocation**, including diagnostic commands. Run
a separate process with `--no-daemon --console=plain`, close stdin, redirect both output streams to a
temporary log, and enforce a process deadline shorter than the tool timeout. Do not use an unbounded
`-Wait` or `WaitForExit()`.

PowerShell example (set the terminal tool timeout to at least 120 seconds):

```powershell
$workDir = (Get-Location).Path
$logDir = Join-Path $env:TEMP "opencode"
if (-not (Test-Path -LiteralPath $logDir)) { throw "Missing temporary log directory: $logDir" }
$log = Join-Path $logDir ("gradle-" + [guid]::NewGuid().ToString("N") + ".log")
# Replace build with the requested tasks and options.
$arguments = '/d /s /c ""{0}\gradlew.bat" build --no-daemon --console=plain <NUL >"{1}" 2>&1"' -f $workDir, $log
$process = Start-Process -FilePath $env:ComSpec -ArgumentList $arguments -WorkingDirectory $workDir -WindowStyle Hidden -PassThru
if (-not $process.WaitForExit(90000)) {
    taskkill.exe /PID $process.Id /T /F
    throw "Gradle exceeded 90 seconds; inspect $log before retrying."
}
$process.Refresh()
[pscustomobject]@{ ExitCode = $process.ExitCode; Log = $log }
if ($process.ExitCode -ne 0) { throw "Gradle failed; inspect $log" }
```

Read the log with the file-reading tool and check the exit code before reporting success. Choose a
longer bounded deadline in advance for cold builds: the first build downloads the Minecraft,
Fabric, Forge and NeoForge toolchains and can take several minutes. After a timeout, inspect the log
and remaining child processes before retrying.

### Java and toolchains

Run Gradle on **JDK 25**. ForgeGradle 7 requires it. The build declares a JDK 21 toolchain for
Minecraft modules; `settings.gradle.kts` enables the Foojay resolver so Gradle provisions it when
only JDK 25 is installed. Bukkit and `common` compile with `--release 8`; mod modules with
`--release 21`.

```bash
# Everything: tests plus all four artifacts
./gradlew build

# Tests only (common policy, Bukkit store, shared mod config/state)
./gradlew test

# One module
./gradlew :common:test
./gradlew :fabric:test

# A single test class or method
./gradlew :fabric:test --tests "dev.vanish.mod.ModStateStoreTest"
./gradlew :common:test --tests "dev.vanish.common.VanishViewTest"
```

### Release artifacts

```text
build/libs/Vanish-<version>.jar                   Bukkit (Spigot + Paper)
fabric/build/libs/Vanish-Fabric-<version>.jar     Fabric (remapped)
forge/build/libs/Vanish-Forge-<version>.jar       Forge
neoforge/build/libs/Vanish-NeoForge-<version>.jar NeoForge
```

The Fabric unremapped intermediate lives under `fabric/build/tmp/shadow/`; never ship it.

Release tags:

```bash
./gradlew -Pver="v1.0.0" release
```

Versioning (`build.gradle.kts`):
- No `-Pver` → `yyMMdd-HHmm-SNAPSHOT`
- `-Pver=vX.Y.Z-RC-N` → `X.Y.Z-RC-N-SNAPSHOT`
- `-Pver=vX.Y.Z` → `X.Y.Z` (the Bukkit jar is renamed to `build/libs/Vanish.jar`; loader jars keep
  the version)

Quote the `-Pver` value so the shell does not mangle `=`.

## Layout rules

- Bukkit sources are at the repository root in `src/`; the root project produces the Bukkit jar.
- `common/` is the platform-neutral policy module. It must not import Bukkit or Minecraft.
- `modloader/` is a shared **source directory**, included by each mod module with
  `sourceSets.main.java.srcDir("../modloader/src/main/java")`. It is not a Gradle project and is
  compiled once per loader against Minecraft 1.21.11 official mappings.
- Loader adapters stay thin: lifecycle events, join/quit, tick, commands and paths. Put behaviour
  in `modloader` and policy in `common`.
- `fabric` uses Loom, `neoforge` uses ModDevGradle, `forge` uses ForgeGradle 7 and therefore
  `forge/build.gradle` (Groovy).

## Packaging rules

- PacketEvents, Adventure and Gson are bundled and relocated to `dev.vanish.libs.*` in every
  artifact. Add a matching `relocate` entry when adding a shaded library.
- Each mod module declares a dedicated `bundled` configuration; it excludes `io.netty` and
  `org.slf4j`, which the server provides. Do not widen it to `runtimeClasspath` or Minecraft will be
  packaged.
- Fabric applies Shadow first and then remaps with `remapJar`; keep `remapJar.inputFile` pointed at
  the shaded jar.
- Never require a server-installed PacketEvents plugin or mod.

## Maintenance

Keep these in sync with the current project state:

- **`AGENTS.md`** — architecture, build constraints, verification status
- **`README.md`**, **`docs/compatibility.md`**, **`docs/usage.md`** — feature list, install paths,
  commands, permissions, config/state
- **`src/main/resources/plugin.yml`** and the mod metadata:
  `fabric/src/main/resources/fabric.mod.json`,
  `forge/src/main/resources/META-INF/mods.toml`,
  `neoforge/src/main/resources/META-INF/neoforge.mods.toml`
- **`.agents/skills/run-plugin/SKILL.md`** (this file) — build commands, artifact paths, packaging

## Gotchas

- **ForgeGradle needs JDK 25** — a 21 Gradle JVM fails while configuring ForgeGradle's mavenizer,
  even though the mod compiles for Java 21.
- **ModDevGradle needs a JDK 21 toolchain** — without the Foojay resolver in `settings.gradle.kts`,
  `createMinecraftArtifacts` fails with "Cannot find a Java installation".
- **Fabric Loom 1.16.3** is the pinned version because newer Loom releases require a JDK 25 Gradle
  JVM, while Forge tooling still needs a separate 21 toolchain.
- **The same shared sources compile three times** — a change in `modloader/` must build in all of
  `:fabric`, `:forge` and `:neoforge`, not just one.
- **Reflection names differ on Fabric** — runtime uses intermediary names. Use
  `ModPlatform.mapFieldName`/`mapMethodName`; Fabric maps through `MappingResolver`, the Forge
  family keeps Mojang names.
- **OneDrive build lock** — if `build/` is synced, Gradle may fail to delete a test-results
  directory; remove that directory and retry.
