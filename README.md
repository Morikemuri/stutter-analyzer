# StutterAnalyzer

**A Minecraft 1.20.4 mod** - available for **Forge** and **Fabric**

Catch game freezes. Figure out why they happen. Fix them.

---

## What is this?

You are playing Minecraft. The game freezes for a second. Then it continues like nothing happened.

What caused it? Chunks loading? A slow mod? Garbage collection? Memory running low?

**StutterAnalyzer watches in the background**, detects freezes, classifies what caused them, and saves a report so you can get help or fix it yourself.

No setup needed. Install, launch, play.

---

## What it does NOT do

- **Does not boost FPS directly.** It is a diagnostic tool, not an optimizer.
- **Does not upload anything without your action.** Reports are saved locally by default.
- **Does not require a token or account.** Everything works without signing in anywhere.
- **Does not modify game rendering.** The Emergency Guard system warns and reports, it does not patch mods.

---

## Versions

| Loader | Minecraft | Status |
|--------|-----------|--------|
| Forge 49.x | 1.20.4 | Stable |
| Fabric | 1.20.4 | Stable |

Both versions share the same feature set and command interface.

---

## Features

- **Automatic freeze detection** - catches lag spikes you did not even notice
- **Smart classification** - tells you if it was GC, chunk loading, server lag, or something unknown
- **Freeze reports** - every freeze gets a `.md` and `.json` report in `config/stutter-analyzer/reports/`
- **Report submission** - `/sa submit` sends the report to the Stutter Analyzer server (anonymous, no account needed)
- **F3 status line** - tiny status indicator in the debug screen so you always know the mod is running
- **Emergency Guard system** - detects known crash patterns and writes hints (warn-only by default)
- **Russian language support** - works in English and Russian automatically based on your game language
- **Dedicated server support** - server-side MSPT tracking and commands work on console

---

## Installation

### Forge

1. Install [Forge 49.x for Minecraft 1.20.4](https://files.minecraftforge.net/)
2. Put the StutterAnalyzer Forge `.jar` into your `.minecraft/mods/` folder
3. Launch Minecraft

### Fabric

1. Install [Fabric Loader for Minecraft 1.20.4](https://fabricmc.net/)
2. Put the StutterAnalyzer Fabric `.jar` into your `.minecraft/mods/` folder
3. Launch Minecraft

---

## How to use

Once you are in a world, StutterAnalyzer is already running. If the game freezes, a report is saved automatically.

### Basic commands

| Command | What it does |
|---------|--------------|
| `/sa status` | Full analyzer status: side, last spike, stutter counts, quiet mode, submission mode |
| `/sa version` | Show mod version, loader, and feature summary |
| `/sa privacy` | Show report privacy info |
| `/sa last` | Show the last detected freeze |
| `/sa help` | Show all available commands |

### Viewing events

| Command | What it does |
|---------|--------------|
| `/sa reports` | List all saved freeze reports |
| `/sa show <time>` | Show medium/severe/extreme events from the last `5m`, `10m`, `30m`, or `1h` |

### Alerts

| Command | What it does |
|---------|--------------|
| `/sa alerts status` | Show current alert settings |
| `/sa alerts minor` | Show all stutters (noisy) |
| `/sa alerts medium` | Show medium and above |
| `/sa alerts severe` | Show severe and extreme |
| `/sa alerts extreme` | Show only extreme freezes |
| `/sa alerts off` | Disable chat alerts |
| `/sa alerts cooldown <seconds>` | Set alert cooldown (5-600s) |
| `/sa alerts test` | Inject test alerts for all severity levels |

### Submitting a freeze report

If you see an unknown freeze and want to help improve the mod:

1. Run `/sa preview` to see what would be sent
2. Run `/sa submit` to upload the report
3. The report is stored anonymously on the Stutter Analyzer server - no account needed

Additional submit commands:

| Command | What it does |
|---------|--------------|
| `/sa preview` | Preview the report that would be submitted |
| `/sa submit preview` | Same as `/sa preview` |
| `/sa submit status` | Show submission mode, endpoint, last upload result |
| `/sa submit health` | Check the report server health |

---

## F3 status line

Press **F3** in-game. You will see a StutterAnalyzer status line.

- **Green** - everything is fine
- **Yellow** - a subsystem is slow or degraded
- **Red** - something broke (check the log file)

Toggle with:
```
/sa f3 on
/sa f3 off
/sa f3 status
```

---

## Optimization suggestions

StutterAnalyzer can scan your mod list and suggest compatible performance mods:

| Command | What it does |
|---------|--------------|
| `/sa optimize suggest` | Scan in background and suggest compatible optimization mods |
| `/sa optimize install` | Install the suggested mods (shows warning, requires confirmation) |

---

## Config

The config file is at `config/stutteranalyzer-common.toml`. Open it with any text editor.

Key settings:

| Setting | Default | What it does |
|---------|---------|--------------|
| `enabled` | `true` | Turn the whole mod on or off |
| `enable_f3_status_line` | `true` | Show the F3 status line |
| `enable_manual_submission` | `true` | Allow `/sa submit` commands |
| `guard_enabled` | `true` | Enable the Emergency Guard system |
| `show_startup_message` | `true` | Show a message when you join a world |

---

## Submission and privacy

StutterAnalyzer does not store any account tokens.

`/sa submit` uploads the freeze report anonymously to the Stutter Analyzer report server via Cloudflare. No account is required.

If upload is not configured, reports are saved locally in `config/stutter-analyzer/reports/`.

See [PRIVACY.md](PRIVACY.md) for full details on what is collected and what is not.

---

## Requirements

| Loader | Minecraft | Java |
|--------|-----------|------|
| Forge 49.x | 1.20.4 | 17 |
| Fabric | 1.20.4 | 17 |

---

## For server owners

StutterAnalyzer works on dedicated servers. The server-side commands monitor server tick performance, memory, GC, and chunk events. No client classes are loaded on the server.

---

## Questions and bug reports

Open an issue at: https://github.com/Morikemuri/stutter-analyzer/issues

When reporting a problem with a freeze, run `/sa preview` to see the report summary, then run `/sa submit` to upload it and include the upload ID in your issue.

---

**Author:** [Morikemuri](https://github.com/Morikemuri)
Minecraft 1.20.4 - Forge 49.x and Fabric
