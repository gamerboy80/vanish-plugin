# Vanish

Staff vanish for **Spigot/Paper 1.8.8 through 26.x**, plus **Fabric, Forge and NeoForge 1.21.11**.
One shared policy core drives every platform, with a small adapter per loader and no separate
PacketEvents installation.

## Features

- Server-side visibility for tab, entity spawns/updates/equipment/effects/sounds, passengers and
  leashes, command suggestions, scoreboard teams/objectives/scores and map markers.
- Per-recipient packet filtering, with cached scoreboard/map originals reprojected on reveal or
  permission changes.
- Atomic persisted state and player-property baselines that survive restarts and crashes.
- Private/public staff chat, fake join/quit notices, server-list count/sample filtering and
  Placeholder-free `/list` handling on Bukkit.
- Optional privacy guards for damage, hunger, XP, mob targeting, collisions, spawning, sleep,
  gliding, raids, vibrations, block sounds, break particles, block animations and silent container
  inspection on Bukkit.
- Protocol translation through ViaVersion, ViaBackwards, ViaRewind, ProtocolSupport and
  Geyser-Spigot: hiding is applied to server-version packets before translation, so every connected
  client protocol sees the same tab and entity state.
- Optional Essentials and dynmap visibility claims through their public APIs.
- The mod loaders use the same immutable visibility policy: tab/entity hiding, operator toggles,
  flight handling, join/quit notices and restart persistence.

## Supported platforms

| Platform | Versions | Artifact |
| --- | --- | --- |
| Spigot / Paper | 1.8.8 through 26.x | `Vanish-<version>.jar` |
| Fabric | 1.21.11 | `Vanish-Fabric-<version>.jar` |
| Forge | 1.21.11 | `Vanish-Forge-<version>.jar` |
| NeoForge | 1.21.11 | `Vanish-NeoForge-<version>.jar` |

The Bukkit artifact is compiled against the **Spigot 1.8.8 API to Java 8 bytecode** and uses
PacketEvents to abstract protocol differences. Newer Bukkit APIs are reached through reflection and
optional event registration, never hard-linked.

| Server | Status | Notes |
| --- | --- | --- |
| 1.8.8, 1.8.9 | Tested | Legacy player-info packets; no collision/silent/spawning setters; `hidePlayer` fallback |
| 1.9-1.12.2 | Supported | Collision, glide and Paper spawning APIs become available |
| 1.13-1.15 | Supported | Flattening and modern block states; chat remains legacy |
| 1.16-1.18.2 | Supported | UUID chat, system messages, game events/vibrations |
| 1.19-1.19.2 | Supported | Signed chat, system-chat packets, modern player-info update |
| 1.19.3-1.20.4 | Supported | Separated player-info remove/update packets |
| 1.20.5-26.x | Supported | Data components, current PacketEvents mappings, Java 25 runtimes |

Tested on Paper 1.8.8 build 445 with Java 8 and Paper 1.21.4 build 232 with Java 21 using real
Minecraft clients, including a 1.8.8 client translated by ViaVersion, ViaBackwards and ViaRewind.
See [docs/compatibility.md](docs/compatibility.md) for the per-version details.

Modded servers have no permission nodes; **operators (command level 2)** may vanish, see vanished
players and run the command. Vanish state lives in `config/vanish/vanish.json` and is written
atomically before any visibility change. The JSON store accepts missing baselines, so an empty or
older document loads cleanly.

## Build

```text
./gradlew build
```

Run Gradle itself on **JDK 25** (ForgeGradle 7 requires it). The build uses a JDK 21 toolchain for
Minecraft code and provisions it automatically through Gradle's Foojay resolver. The first build
downloads the Minecraft, Fabric, Forge and NeoForge toolchains; later builds are incremental.

Release artifacts:

```text
build/libs/Vanish-<version>.jar                  Bukkit (Spigot + Paper)
fabric/build/libs/Vanish-Fabric-<version>.jar    Fabric (remapped mod jar)
forge/build/libs/Vanish-Forge-<version>.jar      Forge
neoforge/build/libs/Vanish-NeoForge-<version>.jar NeoForge
```

`./gradlew :fabric:remapJar :forge:shadowJar :neoforge:shadowJar` produces the mod jars without
running the core tests. PacketEvents, Adventure and Gson are shaded and relocated to
`dev.vanish.libs.*` in every artifact, so a server can run another PacketEvents consumer without
classloader conflicts.

## Install

**Bukkit:** drop `Vanish-<version>.jar` in `plugins/`. A separate PacketEvents plugin is not needed.
Grant `vanish.use` (or `vanish.auto`) to staff and `vanish.see` to observers.

**Fabric:** drop `Vanish-Fabric-<version>.jar` in `mods/` with Fabric Loader 0.20.1+ and Fabric API
for 1.21.11.

**Forge:** drop `Vanish-Forge-<version>.jar` in `mods/` on Forge 61.2.4+.

**NeoForge:** drop `Vanish-NeoForge-<version>.jar` in `mods/` on NeoForge 21.11.48+.

The mod jars are server-side only.

ViaVersion, ViaBackwards, ViaRewind, ProtocolSupport and Geyser-Spigot (with or without Floodgate)
need no extra configuration. Vanish loads after them and filters packets on the server side, before
translation. `/vanish info` also reports how many online sessions are Bedrock players when Geyser's
API is available.

## Commands

Bukkit:

| Command | Purpose |
| --- | --- |
| `/vanish` / `/v` | Toggle your visibility |
| `/vanish on\|off\|toggle\|status [player]` | Explicit self/other control |
| `/vanish list` | List vanished staff |
| `/vanish pickup` | Toggle item pickup while vanished |
| `/vanish inspect` | Read-only container snapshot (also right-click while vanished) |
| `/vanish chat <private\|public\|toggle\|status>` | Staff chat channel |
| `/vanish options` / `/vanish option <name> <on\|off\|toggle>` | Inspect/change saved switches |
| `/vanish interactions <on\|off\|toggle\|status>` | Flip the whole interaction guard group |
| `/vanish info` | Show version, server, PacketEvents, translator and vanish counts |
| `/vanish doctor` | Re-check tab/entity hiding and replay scoreboard/map projections |
| `/vanish reload` | Reload `config.yml` |

Bukkit permissions: `vanish.use`, `vanish.auto`, `vanish.see`, `vanish.others`, `vanish.inspect`,
`vanish.admin`. The defaults are in `src/main/resources/plugin.yml`. The doctor command is gated by
`vanish.admin`; it repairs tab/entity visibility pairs that drifted from the policy and replays
cached scoreboard/map projections for every viewer.

Mod loaders:

| Command | Purpose |
| --- | --- |
| `/vanish` / `/v` | Toggle your visibility |
| `/vanish on\|off\|toggle\|status [player]` | Explicit self/other control |
| `/vanish list` | List vanished staff |
| `/vanish info` | Show loader, server, vanish counts and data path |
| `/vanish doctor` | Re-check tab/entity hiding and replay scoreboard/map projections |
| `/vanish reload` | Reload `config/vanish/config.json` |

## Configuration and state

Every toggle is also written to the server log (`<player> is now vanished.` or `is visible again.`) so
staff changes stay auditable. Bukkit `config.yml` contains the `vanish.*` switches, a complementary
color `palette` and the `messages` templates. Unknown option names are rejected; missing keys keep
the documented default. Option edits validate the whole document before an atomic config
replacement.

### Palette and message formatting

The default theme is a light-purple / lime complementary pair:

```yaml
palette:
  primary: "&d"
  secondary: "&5"
  accent: "&a"
  success: "&a"
  warning: "&e"
  error: "&c"
  muted: "&8"
  text: "&f"
  reset: "&r"
```

Change those nine values and run `/vanish reload` to retheme the prefix, command feedback, status,
list, notices and doctor output everywhere. Palette values use legacy `&` codes so they render on
1.8.8 as well as current servers. Every message accepts the palette tokens `{primary}`,
`{secondary}`, `{accent}`, `{success}`, `{warning}`, `{error}`, `{muted}`, `{text}` and `{reset}`,
plus the named placeholders `{player}`, `{state}`, `{count}`, `{players}`, `{option}` and
`{permission}`.

Example custom message:

```yaml
messages:
  prefix: "{muted}[{primary}Vanish{muted}]{reset} "
  enabled: "{accent}You are now vanished."
  status-vanished: "{muted}{player} {text}is {accent}vanished{text}."
  list: "{muted}Vanished ({count}{muted}): {accent}{players}"
```

The mod loaders write the same palette object and message templates to `config/vanish/config.json`,
so both platforms share one vocabulary.

Bukkit `vanish.yml` uses schema 2:

```yaml
schema: 2
vanished:
- 00000000-0000-0000-0000-000000000000
baselines:
  00000000-0000-0000-0000-000000000000:
    flight: false
    flying: false
    collision: true
    silent: false
    sleeping: false
    spawning: true
    mode: SURVIVAL
```

Mod loaders write the same schema-2 shape as JSON. `config/vanish/config.json` is created on first
start with every switch and message, plus `auto-vanish-staff`. Unknown switches fail validation
instead of silently disabling a guard.

## Modules

```text
src/        Bukkit loader: Spigot + Paper lifecycle, commands, YAML storage, config, compat shims,
            optional event registration and the shaded Bukkit release jar.
common/     Platform-independent policy: immutable VanishView, options, state, silent-action
            markers, scoreboard projection and the PacketEvents filter.
modloader/  Shared mod service compiled into each loader: JSON config/state, vanish lifecycle,
            entity-tracker visibility, PacketEvents Netty bridge and Brigadier command tree.
fabric/     Fabric adapter: lifecycle/join/quit/tick events, config path, mapping resolver.
forge/      Forge adapter: lifecycle/join/quit/tick events, config path.
neoforge/   NeoForge adapter: lifecycle/join/quit/tick events, config path.
```

Only the adapters know loader classes. `modloader/` is a source directory included by all three mod
modules, so the service and packet bridge are compiled once per loader against the same official
Minecraft mappings.

## Verification

`./gradlew build` runs the common policy/scoreboard tests, the Bukkit state-store tests and the
shared mod config/state tests, then builds all four artifacts. The repository was previously
exercised with real 1.8.8 and 1.21.4 clients for the Bukkit features: tab/entity visibility, vanish
status and list, private/public staff chat, fade/reveal, and state persistence across a restart.

The Fabric, Forge and NeoForge modules compile and package against 1.21.11; runtime verification on
live modded servers is the remaining step before advertising them as tested.
