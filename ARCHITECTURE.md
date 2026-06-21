# ServerGuard — Architecture

## Overview

ServerGuard is a server security and monitoring plugin for Minecraft (API 1.21+). It logs player commands, block changes, and container access, provides a real-time alert and watch system, audits admin actions, and offers an in-game GUI panel for reviewing all data. Logs are stored in a database with configurable retention.

**Main class:** `pl.serverguard.ServerGuard` (extends `JavaPlugin`)

---

## Core Systems

### Logging System

ServerGuard records three categories of player activity through dedicated listeners:

| Listener | What it logs |
|---|---|
| `CommandListener` | Player commands (chat commands executed in-game) |
| `ContainerListener` | Container interactions (chests, barrels, hoppers, etc.) |
| `BlockListener` | Block placement and destruction |
| `ConsoleCommandListener` | Commands executed from the server console |

Additional listeners handle supplementary events:

- `PlayerListener` — join/quit, general player activity.
- `ModerationListener` — moderation-related events (kicks, bans, mutes).
- `AdminChatListener` — admin chat channel messages.

All log entries are persisted by `DatabaseManager`.

### Watch System (`WatchManager`)

- Lets admins set a *watch* on a specific player to receive detailed real-time tracking of their actions.
- Config-reloadable via `watchManager.reload()`.

### Alert System (`AlertManager`)

- Broadcasts alerts to online staff (players with `serverguard.notify`) when monitored events occur (e.g. a non-admin attempts a restricted command).
- Config-reloadable via `alertManager.reload()`.

### Admin Audit (`AdminAuditManager`)

- Tracks and logs actions performed by administrators themselves — who ran what command and when.
- Players with `serverguard.audit.bypass` are excluded.
- Config-reloadable via `adminAudit.reload()`.

### Admin GUI (`AdminGuiService`)

- In-game inventory-based panel opened with `/sg gui`.
- Provides paginated views of command logs, container logs, block logs, player history, and search results.
- GUI click events handled by `GuiListener`.

---

## Database (`DatabaseManager`)

- Initialised on startup with `db.initialize()`; cleanly closed on shutdown with `db.close()`.
- Supports automatic **retention**: a scheduled async task (`scheduleRetention()`) periodically purges entries older than the configured number of days.
  - Configurable via `database.retention.enabled`, `database.retention.days`, `database.retention.interval-hours`, and `database.retention.initial-delay-minutes` in `config.yml`.

---

## Configuration & Localisation

- `config.yml` — main settings (database, retention, alert rules, watch rules, audit settings).
- `lang/pl.yml` and `lang/en.yml` — bundled language files, loaded by `LangManager`.
- `ConfigListManager` — manages config-driven lists (e.g. blocked commands, watched phrases).
- Hot-reload with `/sg reload` — triggers `reloadAll()` which reloads config, language, audit, alerts, watch, player causes, and re-schedules retention.

---

## Commands

| Command | Description | Permission |
|---|---|---|
| `/sg gui` | Open the admin GUI panel | `serverguard.admin` |
| `/sg reload` | Reload all configuration | `serverguard.admin` |
| `/sg help` | Show help | `serverguard.admin` |
| `/sghistory <player> [type] [count]` | View a player's logged history (commands, containers, blocks) | `serverguard.admin` |
| `/sgsearch <phrase>` | Full-text search across all logs | `serverguard.admin` |

### Permissions

| Permission | Description | Default |
|---|---|---|
| `serverguard.admin` | Access to all ServerGuard commands and GUI | op |
| `serverguard.notify` | Receive real-time alert notifications | op |
| `serverguard.bypass` | Bypass command logging for this player | false |
| `serverguard.audit.bypass` | Bypass admin audit tracking | false |

---

## Lifecycle

1. **Enable** — print banner → save default config & lang files → init `LangManager` → init `DatabaseManager` → init managers (`AlertManager`, `AdminAuditManager`, `WatchManager`, `ConfigListManager`, `AdminGuiService`) → register all listeners → register commands → schedule retention task.
2. **Reload** — reload config → reload lang → reload audit/alert/watch managers → reload player causes → re-schedule retention.
3. **Disable** — cancel retention task → close database connection.

---

## Package Layout

```
pl.serverguard
├── ServerGuard            # Plugin entry point
├── commands/              # SGCommand, SGHistoryCommand, SGSearchCommand
├── config/                # LangManager
├── gui/                   # AdminGuiService
├── listeners/             # CommandListener, ContainerListener, BlockListener,
│                          # PlayerListener, ModerationListener, ConsoleCommandListener,
│                          # GuiListener, AdminChatListener
└── managers/              # DatabaseManager, AlertManager, AdminAuditManager,
                           # WatchManager, ConfigListManager
```
