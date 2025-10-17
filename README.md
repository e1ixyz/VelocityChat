# VelocityChat

VelocityChat is a Velocity proxy plugin that unifies chat across a network while still respecting server, network-wide, and staff-only channels. Players can quickly swap between destinations, send private messages that traverse servers, and manage ignore lists—all with configurable formatting.

## Features
- Channel subscriptions let players listen to server chat (always on) and optionally add network/staff feeds using `/chat listen <network|staff>`.
- Speaking channel can be switched with `/chat server`, `/chat network`, `/chat staff`, or by sending a message directly (e.g. `/chat network hello all`).
- `/msg`, `/r` (aliases `/reply`) support network-wide private messages with ignore protection and quick replies.
- `/chat ignore` manages personal ignore lists (staff cannot be ignored).
- `/chat alert <message>` broadcasts to the entire network for staff.
- Every prefix, format, and feedback line is configurable in `config.yml`.

## Commands
| Command | Description | Permission |
| --- | --- | --- |
| `/chat server` | Speak only to the local server chat. | `velocitychat.use` (default) |
| `/chat network [message]` | Switch your speaking channel to network; optional message sends immediately. | `velocitychat.use` (default) |
| `/chat staff [message]` | Switch your speaking channel to staff; optional message sends immediately. | `Velocitychat.staff` |
| `/chat listen <network\|staff> [on|off]` | Toggle or explicitly enable/disable viewing of extra channels. | `velocitychat.use` (default) for network, `Velocitychat.staff` for staff |
| `/chat ignore [player]` | Toggle ignoring a player (lists ignores when used without a name). | `velocitychat.use` (default) |
| `/chat alert <message>` | Send a network-wide alert. | `Velocitychat.staff` |
| `/msg <player> <message>` | Send a private message across servers. | `velocitychat.use` (default) |
| `/r <message>` | Reply to the last private message (alias: `/reply`). | `velocitychat.use` (default) |

> All players can use the base chat commands without extra permissions. Only staff members (holders of `Velocitychat.staff`) can access the staff channel and alert command.

## Configuration
After first launch, the plugin writes `plugins/VelocityChat/config.yml`. Key sections:
- `channels.default`: sets the channel players join on login (`SERVER`, `NETWORK`, or `STAFF`).
- `channels.prefixes` and `channels.formats`: customize how network and staff messages appear. Server chat uses vanilla formatting.
- `private-messages`: templates for outbound and inbound private chats.
- `messages`: every player-facing string, including ignore notifications and channel feedback.
- `settings.force-channel-intercept`: set to `true` if you are running a proxy patch (e.g. [SignedVelocity](https://modrinth.com/plugin/signedvelocity)) or have `enforce-secure-profile=false` on backend servers so VelocityChat can suppress the original signed chat packet. Leave `false` on stock installations to avoid disconnects.

Legacy `&` colour codes are supported. Placeholders available in templates include `{prefix}`, `{player}`, `{message}`, `{server}` (for channel messages) and `{sender}`, `{target}` for private messages.

### Signed chat / secure profiles
Minecraft 1.19.1+ signs chat messages. To cancel or modify a signed packet you must either:

1. Disable secure profiles on every backend server (`enforce-secure-profile=false` in `server.properties`), **or**
2. Install a proxy-side patch such as [SignedVelocity](https://modrinth.com/plugin/signedvelocity) and set `force-channel-intercept: true` in the VelocityChat config.

Without one of these options VelocityChat automatically falls back to server chat because the proxy is not allowed to stop signed messages.

## Building
This project targets Java 17 and Velocity API `3.3.0-SNAPSHOT`.

```bash
mvn clean package
```

The shaded jar is produced at `target/velocitychat-1.0.0-SNAPSHOT.jar`.

## Installation
1. Build or download the plugin jar.
2. Copy it into your Velocity proxy `plugins/` directory.
3. Start or reload the proxy to generate the default configuration.
4. Adjust `plugins/VelocityChat/config.yml` as needed, then run `/velocity plugins reload velocitychat` or restart to apply changes.
