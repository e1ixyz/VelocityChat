# VelocityChat — Handoff

A Velocity proxy plugin that unifies Minecraft chat across a network: per-server,
network-wide, and staff-only channels, plus cross-server private messages and ignore lists.

_Last updated 2026-06-29._

## What it is
Single-purpose Velocity plugin. Players pick a "speaking" channel (`server`/`network`/`staff`)
and opt into "listening" feeds. `/msg` + `/r` carry private messages across backend servers.
All formatting/strings are configurable in `config.yml`. Targets **Java 17** and
**Velocity API `3.4.0`**.

## Run it
```bash
mvn clean package          # -> target/velocitychat-1.0.0-SNAPSHOT.jar (shaded)
```
No test suite. "Verify" = a green `mvn clean package`. To run live: drop the shaded jar
into a Velocity proxy's `plugins/` dir, start the proxy (generates `plugins/VelocityChat/config.yml`),
then `/velocity plugins reload velocitychat` to apply config edits.

Build env note: Maven runs on JDK 25 here; the pom pins `--release 17`, so it compiles fine.
The Guice/`sun.misc.Unsafe` warnings during build are harmless Maven internals.

## Architecture & file map
`src/main/java/com/velocitychat/`
- `VelocityChatPlugin.java` — `@Plugin` entry point. Wires events (`PostLogin`, `Disconnect`,
  `PlayerChat`), registers commands. Holds the signed-chat interception logic
  (`canInterceptSignedChat`, `suppressChat`).
- `chat/ChatManager.java` — all state: per-player channel prefs, ignore lists, last-conversation
  map (for `/r`). Dispatches channel/private/alert messages. Thread-safe via `ConcurrentHashMap`.
- `chat/ChatChannel.java` — `SERVER` / `NETWORK` / `STAFF` enum.
- `command/` — `ChatCommand` (`/chat …` subcommands), `MessageCommand` (`/msg`), `ReplyCommand` (`/r`).
- `config/VelocityChatConfig.java` — SnakeYAML loader, flattens `messages.*` keys, exposes formats/prefixes.
- `util/TextFormatter.java` — `{placeholder}` substitution + legacy `&` color codes via Adventure.

`src/main/resources/` — `config.yml` (default config) and `velocity-plugin.json` (plugin metadata,
kept in sync with the `@Plugin` annotation by hand).

Flow: `PlayerChatEvent` → look up speak channel → if NETWORK/STAFF and interceptable, suppress the
original packet and re-dispatch via `ChatManager` to listeners; otherwise fall through to vanilla server chat.

## Current state
Working and builds clean against Velocity 3.4.0. All documented commands implemented.
Permission gate is `velocitychat.staff` (staff features); base commands are ungated (everyone).

## Gotchas
- **Signed chat (MC 1.19.1+):** the proxy can't cancel signed chat packets on a stock install.
  NETWORK/STAFF channels only work if backend servers set `enforce-secure-profile=false`, or you
  run [SignedVelocity](https://modrinth.com/plugin/signedvelocity) + `force-channel-intercept: true`.
  Otherwise the plugin auto-falls-back to server chat. This is by design, not a bug.
- **Permission casing matters:** the node is lowercase `velocitychat.staff`. (It was mistakenly
  capitalized `Velocitychat.staff` before 2026-06-29 — case-sensitive backends silently denied staff.)
- **NETWORK is opt-in:** on login players listen to nothing extra; they only see network/staff chat
  after switching to or enabling that channel. Don't expect network messages to broadcast to everyone.
- `target/` is committed in this repo — rebuilding produces a diff there.

## Where to look next
Known cleanups flagged but not done (intentional, low priority):
- `VelocityChatPlugin.suppressChat()` uses `ChatResult.message("")` on the non-force path;
  `ChatResult.denied()` would be cleaner (empty-message replacement can surface a blank local line).
- `VelocityChatConfig.componentMessage(...)` is dead/unused.
