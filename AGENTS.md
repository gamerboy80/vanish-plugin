# Vanish repository guide

This repository is the standalone staff-vanish plugin extracted from EpicSmpPlugin. It is no longer
the CrimsonWarpedcraft example template.

## Architecture

```text
src/     Spigot + Paper loader (root project)
  VanishPlugin         JavaPlugin entry point, shaded PacketEvents lifecycle
  VanishService        main-thread toggle/persistence/visibility API
  VanishListener       1.8-compatible event isolation + silent inspection
  VanishCommand        CommandExecutor/TabCompleter for /vanish
  VanishStore          atomic schema-2 vanish.yml (schema-1 readable)
  VanishSettings       config snapshot, ampersand color messages
  VanishIntegrations   dynmap/Essentials reflective hooks
  VanishPlaceholders   optional PlaceholderAPI expansion (compile-only dep)
  event/               pre-change cancellable and post-change vanish events
  compat/              reflection for APIs added after 1.8.8 and raw messages
  OptionalEvents       by-name registration for newer Spigot/Paper events

common/  platform-independent policy, no server API dependency
  VanishAPI            static facade for external plugins (Provider interface)
  VanishOptions        validated immutable config switches
  VanishView           immutable packet-thread visibility/mention policy
  VanishState          persisted hidden UUIDs + property baselines
  VanishBaseline       pre-vanish flight/collision/silent/sleep/spawning/mode
  VanishActions        short-lived silent block-action markers
  VanishScoreboard     per-connection team/objective/score projection
  VanishPackets        PacketEvents send-side firewall shared by every loader

modloader/  shared mod service, compiled into each mod loader module
  ModPlatform          the only adapter surface (server, paths, logger, messages, mappings)
  SimpleModPlatform    default adapter implementation; Fabric adds mapping resolution
  ModVanish            main-thread lifecycle, persistence, view projection, visibility sync
  ModVisibility        entity-tracker add/remove with a packet fallback
  ModPacketBridge      private PacketEvents Netty platform and per-player encoder injection
  ModPacketEncoder     outbound ByteBuf handler feeding VanishPackets
  ModConfig            validated config.json snapshot
  ModStateStore        atomic schema-2 vanish.json
  ModCommand/ModCommands  shared command behaviour and Brigadier tree

fabric/   Fabric 1.21.11 adapter: lifecycle, join/quit, tick and config path
forge/    Forge 1.21.11 adapter: lifecycle, join/quit, tick and config path
neoforge/ NeoForge 1.21.11 adapter: lifecycle, join/quit, tick and config path
```

The Bukkit loader and `common` target Java 8 bytecode; the mod loaders target Java 21 and
Minecraft 1.21.11. Do not move Bukkit-only calls into `common` or `modloader`. Loader adapters
should stay thin: event wiring, command dispatch and paths only. Put shared behaviour in
`modloader` and shared policy in `common`.

## Build constraints

- `common` and `src/` compile with `options.release = 8`; the Bukkit jar must load on 1.8.8 through
  26.x. Compile against `org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT`; reach newer Bukkit APIs by
  reflection or optional events.
- `modloader`, `fabric`, `forge` and `neoforge` compile with `options.release = 21` against
  Minecraft 1.21.11 official mappings.
- Run Gradle on JDK 25. ForgeGradle 7 requires it; the Foojay resolver provisions the JDK 21
  toolchain used by Minecraft modules.
- PacketEvents 2.14.0 is shaded and relocated to `dev.vanish.libs.*`, with Adventure and Gson, in
  every artifact. Never depend on a server-installed PacketEvents plugin or mod.
- Release artifacts: `build/libs/Vanish-*.jar`, `fabric/build/libs/Vanish-Fabric-*.jar`,
  `forge/build/libs/Vanish-Forge-*.jar`, `neoforge/build/libs/Vanish-NeoForge-*.jar`.
- Forge uses `forge/build.gradle` (Groovy) for ForgeGradle 7; the other modules use Kotlin DSL.
- `modloader/` is a source directory, not a Gradle project. Each mod module adds it through
  `sourceSets.main.java.srcDir("../modloader/src/main/java")`.

## Tests and verification

- `./gradlew build` runs the common policy/scoreboard tests, the Bukkit state-store tests and the
  shared mod config/state tests under `fabric/src/test`, then builds all four artifacts.
- The common test `TestPacketEvents` replaces PacketEvents with Mockito so real wrappers and version
  constants are exercised.
- Runtime verification used Paper 1.8.8 (Java 8) and Paper 1.21.4 (Java 21) with real mineflayer
  clients. Keep `/vanish`, tab/entity hiding, private/public chat and restart persistence working on
  both ends of the range when changing Bukkit packet code.
- Protocol translation is exercised with ViaVersion, ViaBackwards and ViaRewind on Paper 1.21.4,
  including a 1.8.8 client. Packet wrappers must always be built for the server protocol version;
  translators rewrite them for the client afterwards. Never select a wrapper from
  `User#getClientVersion()` (it reports the translated client version).
- Geyser-Spigot is loaded on Paper 1.21.4 and its reflective session hook is exercised through
  `/vanish info`; a live Bedrock client has not been connected in this repository yet.
- The mod loaders compile and package but have not yet been run on live Fabric/Forge/NeoForge
  servers. Verify entity-tracker add/remove and the PacketEvents encoder insertion there before
  claiming them as tested.
- `OptionalEvents` must stay fail-soft: missing classes or methods mean that guard is unavailable,
  not that the plugin fails to load.

## Agent instructions

1. Canonical skills live in `.agents/skills/`; `.claude/skills/` is generated.
2. Do not edit `CLAUDE.md` or files under `.claude/skills/`; hooks sync them from `AGENTS.md`.
3. On Windows, use the bounded Gradle procedure in `.agents/skills/run-plugin/SKILL.md`.
4. Keep README, `docs/compatibility.md`, `docs/usage.md`, `plugin.yml`, the mod metadata files and
   all four loader modules in sync.
5. Describe current features positively. Do not mention removed or excluded features in product
   copy, commands, menus, logs, documentation or release summaries unless the user explicitly asks
   for that comparison or history. Remove obsolete references when scope changes; do not add
   absence claims or disclaimers about features that were removed.
