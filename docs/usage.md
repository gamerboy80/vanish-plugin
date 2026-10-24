# Usage

See the repository [README](../README.md) for installation, commands, permissions and
configuration, and [compatibility.md](compatibility.md) for the 1.8.8-26.x behavior matrix.

Quick start (Spigot/Paper):

1. Install `Vanish-<version>.jar`. PacketEvents is bundled; do not add it separately.
2. Give staff `vanish.use` (and optionally `vanish.auto`) plus `vanish.see`.
3. Run `/vanish`. Use `/vanish options` to inspect or change saved switches and
   `/vanish interactions on|off` to flip the whole interaction guard group.
4. State is saved atomically to `plugins/Vanish/vanish.yml` before visibility changes.
5. Edit `palette` and `messages` in `config.yml`, then run `/vanish reload` to retheme every
   message. Messages accept `{primary}`, `{accent}`, `{muted}` and the other palette tokens plus
   `{player}`, `{state}`, `{count}` and `{players}`.

Quick start (Fabric/Forge/NeoForge):

1. Install `Vanish-Fabric`, `Vanish-Forge` or `Vanish-NeoForge` in `mods/`.
2. Operate the server (command level 2). Operators may vanish and see vanished players.
3. Run `/vanish` or `/v`, then `/vanish list` to inspect hidden staff.
4. `config/vanish/config.json` is created on first start; `/vanish reload` re-reads it. The same
   `palette` tokens and message templates control modded messages. State is written atomically to
   `config/vanish/vanish.json` before visibility changes.
