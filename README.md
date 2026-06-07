# StutterAnalyzer

**A Forge mod for Minecraft 1.20.4**

Catch game freezes. Figure out why they happen. Fix them.

---

## What is this?

You are playing Minecraft. The game freezes for a second. Then it continues like nothing happened.

What caused it? Chunks loading? A slow mod? Garbage collection? Memory running low?

**StutterAnalyzer watches in the background**, detects freezes, classifies what caused them, and saves a report so you can get help or fix it yourself.

No setup needed. Install, launch, play.

---

## Features

- **Automatic freeze detection** - catches lag spikes you did not even notice
- **Smart classification** - tells you if it was GC, chunk loading, server lag, or something unknown
- **Freeze reports** - every freeze gets a `.md` and `.json` report in `config/stutter-analyzer/reports/`
- **One-command bug submission** - `/sa submit last` uploads your report to GitHub Gist and opens the issue form in your browser. No account needed - uploads are anonymous
- **F3 status line** - tiny status indicator in the debug screen so you always know the mod is running
- **Emergency Guard system** - optional safeguards for serious and repeating freeze patterns
- **Russian language support** - works in English and Russian automatically based on your game language

---

## Installation

1. Install [Forge 49.2.0 for Minecraft 1.20.4](https://files.minecraftforge.net/)
2. Put `StutterAnalyzer-1.0.0.jar` into your `.minecraft/mods/` folder
3. Launch Minecraft
4. Type `/sa selfcheck` in chat to confirm everything is working

That is it. Your game is now being monitored.

---

## How to use

Once you are in a world, StutterAnalyzer is already running. If the game freezes, a report is saved automatically.

### Basic commands

| Command | What it does |
|---------|--------------|
| `/sa status` | See if the mod is running and what it is monitoring |
| `/sa last` | Show the last detected freeze |
| `/sa health` | Check if all parts of the mod are working |
| `/sa selfcheck` | Full self-test - good to run after installation |
| `/sa help` | Show all available commands |

### Viewing reports

| Command | What it does |
|---------|--------------|
| `/sa list` | List all saved freeze reports |
| `/sa show <id>` | Show details of a specific report |
| `/sa export` | Show where the report files are saved |

### Submitting a bug report

If you see an **unknown freeze** and want help:

1. Run `/sa submit last` in chat
2. Your report is uploaded to GitHub Gist anonymously (no account, no token)
3. A GitHub issue form opens in your browser with the report already attached
4. Describe what you were doing and submit

No token is stored in the mod. Uploads go to anonymous GitHub Gists and do not expire.

---

## F3 Overlay

Press **F3** in-game. You will see a StutterAnalyzer status line at the top left.

```
SA: ACTIVE | Last: none | Reports: 0
```

- **Green** - everything is fine
- **Yellow** - a subsystem is slow or degraded (run `/sa health` to see which one)
- **Red** - something broke (check the log file)

Toggle the overlay on or off:
```
/sa f3 on
/sa f3 off
/sa f3 toggle
```

---

## Unknown Freeze notification

When StutterAnalyzer detects a freeze it cannot classify, it will send you a chat message:

```
[SA] Unknown freeze detected (312 ms). Report saved. Use /sa submit last to send it.
```

This means the mod saved a full freeze report but could not determine the cause. Submitting it helps improve the classifier.

---

## Config

The config file is at `config/stutteranalyzer-common.toml`. Open it with any text editor.

Key settings:

| Setting | Default | What it does |
|---------|---------|--------------|
| `enabled` | `true` | Turn the whole mod on or off |
| `debugHudEnabled` | `true` | Show the F3 status line |
| `enableManualSubmission` | `true` | Allow `/sa submit` to upload reports |
| `guardEnabled` | `true` | Enable the Emergency Guard system |
| `showStartupMessage` | `true` | Show a message when you join a world |

---

## Crash analysis

If Minecraft crashes and you want to analyze it:

```
/sa crash last     - show last imported crash
/sa crash list     - list all imported crashes
/sa crash show <id>
/sa submit crash last
```

---

## Requirements

- Minecraft 1.20.4
- Forge 49.2.0 or newer
- Java 17

---

## For server owners

StutterAnalyzer works on dedicated servers too. The server-side commands monitor server tick performance, memory, GC, and chunk events.

Permission level 0 (all players): status, health, last, list, show, selfcheck, help
Permission level 2 (operators): delete, config reload, guard enable/disable, debug commands

---

## Questions and bug reports

Open an issue at: https://github.com/Morikemuri/stutter-analyzer/issues

When reporting a problem with a freeze, use `/sa submit last` inside the game - it uploads the freeze report automatically and opens the issue form for you.

---

**Author:** [Morikemuri](https://github.com/Morikemuri)
Minecraft 1.20.4 - Forge 49.2.0
