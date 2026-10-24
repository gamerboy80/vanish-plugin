# Version compatibility

Vanish ships four artifacts: the Bukkit plugin for **1.8.8 through 26.x**, and Fabric, Forge and
NeoForge mods for **1.21.11**. The Bukkit jar is compiled against the Spigot 1.8.8 API for Java 8 and
depends on PacketEvents 2.14 for protocol abstraction. Nothing below is a hard link to a newer
Bukkit class: newer APIs are reached by reflection or optional event registration. The mod jars use
the shared policy core with loader adapters and Mojang official mappings.

## 1.8.8-1.8.9

- Tab list uses the legacy `PLAYER_INFO` packet. The filter removes hidden entries from
  `ADD_PLAYER`/display-name updates but **never** blocks `REMOVE_PLAYER`; blocking the removal was a
  bug that kept vanished staff in tab.
- `Player#hidePlayer(Plugin, Player)` does not exist. Vanish calls it reflectively when present and
  otherwise uses the deprecated one-argument method.
- There is no per-entity silent flag, collision setter or spawning setter. Those options no-op
  instead of failing; name/entity packets, targeting and gameplay guards still apply.
- Action bars are sent as PacketEvents title/action-bar packets, not the modern Paper API.
- Paper's `EntityPickupItemEvent` and `PlayerAttemptPickupItemEvent` do not exist; the legacy
  `PlayerPickupItemEvent` is used.
- Mob "looking" AI goals are not available on the 1.8 API; targeting is still cleared/cancelled.

## 1.9-1.12.2

- Collision, glide and Paper spawning setters appear and are used through reflection.
- Elytra glide, raid and advancement events are registered only when the server exposes them.
- The plugin still uses the 1.8 command API and legacy chat event.

## 1.13-1.15

- Containers implement `InventoryHolder`; the inspector deliberately uses that interface because
  `org.bukkit.block.Container` does not exist on 1.8.
- Block-state flattening is handled by PacketEvents; Vanish does not hardcode numeric block ids.

## 1.16-1.18.2

- Chat packets carry sender UUIDs; the mention filter handles both `CHAT_MESSAGE` and
  `SYSTEM_CHAT_MESSAGE`.
- 1.17+ game events/vibrations are handled by reflectively registered events. On older servers the
  option is simply inactive.
- World height and mob-goal changes need no special handling because all entity work goes through
  PacketEvents and the Bukkit entity id.

## 1.19-1.19.2

- Player info moves to `PLAYER_INFO_UPDATE`; removal uses `PLAYER_INFO_REMOVE` (not filtered).
- Server messages use system chat. Vanish sends private staff chat and fake join/quit notices as
  `SYSTEM_CHAT_MESSAGE` on 1.19+ and legacy chat below 1.19, always via `sendPacketSilently` so the
  mention filter cannot cancel them.
- Signed chat: the plugin cancels the legacy chat event before broadcast and sends staff messages
  itself, so a hidden player's signing session cannot break client chat validation.
- `AsyncPlayerChatEvent` is still the cross-version hook. If a future Paper build stops firing it,
  the outgoing packet filter still hides mentions; private-mode audience selection then falls back to
  packet suppression.

## 1.19.3-1.20.4

- `PLAYER_INFO_REMOVE` and the action-set player-info update are handled separately.
- Scoreboard team/objective/score wrappers are version-normalized by PacketEvents; cached originals
  are retained per connection.

## 1.20.5-1.21.x

- Data components and current PacketEvents wrappers are used for names, maps and score formats.
- Action bars use the PacketEvents action-bar wrapper, which also works on legacy servers.

## 26.x

- PacketEvents resolves the current server mappings automatically. The plugin logs the detected
  version at enable.
- Plugin metadata declares `api-version: 1.13`. This is ignored by 1.8 servers and prevents legacy
  plugin warnings on modern Paper.

## Mod loaders (1.21.11)

- Fabric, Forge and NeoForge share `common/` and the mod service under `modloader/`; only the loader
  lifecycle, commands and config path differ.
- Operators (command level 2) are the staff group. `Permissions.COMMANDS_MODERATOR` gates vanish
  toggles, `status`, `list` and `reload`.
- Visibility uses the server's own entity tracker (`ChunkMap.TrackedEntity`), so vanish sends the
  same spawn, metadata, equipment and tab-list bundle vanilla uses, and reveal restores it exactly.
  A packet destroy/remove + player-info/spawn fallback keeps visibility correct if tracker internals
  change.
- The outbound PacketEvents handler is inserted before the vanilla `encoder` and after packet
  serialization. Silent scoreboard/map replays are written at that handler, so they skip the filter
  and still pass through compression and framing.
- Fabric resolves the intermediary field/method names for the tracker reflection through
  `MappingResolver`; Forge and NeoForge run under Mojang names.
- `config/vanish/config.json` validates every switch and creates documented defaults.
  `config/vanish/vanish.json` stores schema-2 state atomically before visibility changes.
- Flight baselines are captured and restored; the remaining baseline fields are stored for schema
  compatibility with the Bukkit state file.
- The mod jars are server-side only and bundle relocated PacketEvents, Adventure and Gson. No
  separate PacketEvents mod is installed.

## Protocol translation

- Vanish declares `softdepend` for ViaVersion, ViaBackwards, ViaRewind, ProtocolSupport and
  Geyser-Spigot, so PacketEvents sees those plugins when it initializes and binds their
  per-connection protocol versions. Floodgate is recognized alongside Geyser.
- Filtering happens on outgoing server-version packets in the Netty pipeline before the encoder.
  Translators only receive the packets Vanish lets through, and reveal restores them in the server's
  own protocol. Tab/entity, scoreboard, map and chat filtering therefore applies unchanged to
  translated connections.
- Newer clients on older servers (ViaVersion), older clients on newer servers (ViaBackwards,
  ViaRewind), ProtocolSupport connections and Bedrock clients through Geyser-Spigot all use the same
  server-side vanish state, permissions and `/vanish` command. No extra configuration is required.
- Geyser translates the Java packets Vanish lets through, so tab and entity removal reach Bedrock
  clients through Geyser's normal translation. `/vanish info` reports the online Bedrock session
  count when Geyser's API is available; the hook fails soft when Geyser is absent or not ready.

## Deliberate limitations

- The server-list player count can only be lowered on Paper forks exposing
  `ServerListPingEvent#setNumPlayers`. On 1.8 Spigot the listed player sample is still filtered when
  the iterator supports removal; the numeric count may include vanished staff.
- Query/GS4 count filtering is only available through Paper's query event; it is not hard-linked so
  old servers still load. Packet-level visibility is unaffected.
- Vanilla map player arrows are anonymous. While any hidden player is online, player markers are
  conservatively removed for ordinary viewers; exact attribution is impossible without the sender's
  plugin context.
- Plugins that send packets with `sendPacketSilently`, custom scoreboard holders or web/Discord
  exports bypass any generic packet filter. Audit those against a real ordinary account.
- 1.8 lacks collision/silent/spawning property setters and the Paper mob-goal API, so those specific
  cosmetic guards are unavailable there by design.
