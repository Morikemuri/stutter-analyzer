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

## What it does NOT do

- **Does not boost FPS directly.** It is a diagnostic tool, not an optimizer.
- **Does not upload anything automatically.** Reports are saved locally by default.
- **Does not require a token or account.** Everything works without signing in anywhere.
- **Does not modify game rendering.** The Emergency Guard system warns and reports, it does not patch mods.

If you installed 47 performance mods and the game got slower, this mod will not judge you. It will simply produce evidence.

---

## Features

- **Automatic freeze detection** - catches lag spikes you did not even notice
- **Smart classification** - tells you if it was GC, chunk loading, server lag, or something unknown
- **Freeze reports** - every freeze gets a `.md` and `.json` report in `config/stutter-analyzer/reports/`
- **Privacy-first submission** - `/sa submit last` shows you the file path and the issue URL, does not upload anything unless you opt in
- **F3 status line** - tiny status indicator in the debug screen so you always know the mod is running
- **Emergency Guard system** - detects known crash patterns and writes hints (warn-only by default)
- **Previous crash importer** - scans `crash-reports/` on startup and detects known crash causes
- **Russian language support** - works in English and Russian automatically based on your game language
- **Dedicated server support** - server-side MSPT tracking and commands work on console

---

## Installation

1. Install [Forge 49.2.0 for Minecraft 1.20.4](https://files.minecraftforge.net/)
2. Put `StutterAnalyzer-0.1.0-beta.jar` into your `.minecraft/mods/` folder
3. Launch Minecraft
4. Type `/sa selfcheck` in chat to confirm everything is working

That is it. Your game is now being monitored.

---

## How to use

Once you are in a world, StutterAnalyzer is already running. If the game freezes, a report is saved automatically.

### Basic commands

| Command | What it does |
|---------|--------------|
| `/sa status` | See the current state: side, trackers, last event, report count, unknown freezes, submission mode |
| `/sa last` | Show the last detected freeze |
| `/sa health` | Check if all subsystems are working |
| `/sa selfcheck` | Full self-test - good to run after installation |
| `/sa help` | Show all available commands |

### Viewing reports

| Command | What it does |
|---------|--------------|
| `/sa list` | List all saved freeze reports |
| `/sa show <id>` | Show details of a specific report |
| `/sa export` | Show where the report files are saved |

### Submitting a bug report

If you see an unknown freeze and want help:

1. Run `/sa submit last` in chat
2. The mod creates three files in `config/stutter-analyzer/submissions/`:
   - `<id>.md` - the full freeze report
   - `<id>.json` - machine-readable version
   - `<id>-github-issue-body.md` - pre-filled GitHub issue text
3. On the client, the issue body is copied to your clipboard and the GitHub issue page opens automatically
4. Paste the issue body and attach the `.md` file - done

**No upload is performed.** The mod never contacts any external service.

See [PRIVACY.md](PRIVACY.md) for full details on what is collected and what is not.

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

## Unknown Freeze

When StutterAnalyzer detects a freeze it cannot classify, it will send you a chat message:

```
[SA] Unknown freeze detected (312 ms). Report saved. Use /sa submit last to send it.
```

**UNKNOWN_FREEZE means the analyzer did not have enough evidence to assign a cause.** This is honest behavior, not a bug. The mod refuses to make things up.

Submitting the report helps improve the classifier for future versions.

---

## Previous crash import

If Minecraft crashed before, StutterAnalyzer scans `crash-reports/` on startup:

```
/sa crash last     - show last imported crash
/sa crash list     - list all imported crashes
/sa crash show <id>
/sa submit crash last
```

Known crash patterns are detected automatically. For example, the Rubidium lava fluid render crash is recognized if there is evidence for it in the crash file.

**Pattern detection requires evidence.** Having Rubidium installed alone is not enough.

---

## Emergency Guard

The Emergency Guard system watches for known problematic patterns and writes a diagnostic hint when one is detected.

By default, guards are in **warn-only or report-only mode**. They do not patch game code or modify rendering.

Check guard status:
```
/sa guard status
/sa guard list
/sa guard info rubidium_lava_fluid_render
```

If a guard is listed as `warn-only`, it means:
- The pattern was detected
- A hint was written to the log and to a report file
- No automatic fix was applied

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
| `copy_issue_body_to_clipboard` | `true` | Copy the prepared issue body to clipboard after `/sa submit last` |
| `open_issue_url_on_client` | `true` | Open the GitHub issue page in the browser after `/sa submit last` |

---

## Submission and privacy

StutterAnalyzer does not store GitHub tokens.

Public builds do not upload reports automatically.

`/sa submit last` prepares local files only. You review them and manually paste or attach them to a GitHub issue.

This is intentional. Minecraft mods are not a safe place to store API tokens.

See [PRIVACY.md](PRIVACY.md) for full details.

---

## Requirements

- Minecraft 1.20.4
- Forge 49.2.0 or newer
- Java 17

---

## For server owners

StutterAnalyzer works on dedicated servers. The server-side commands monitor server tick performance, memory, GC, and chunk events. No client classes are loaded on the server.

Default permission levels:
- Level 0 (all players): status, health, last, list, show, selfcheck, help
- Level 2 (operators): server report, list, show
- Level 3 (operators): delete, config reload, submit, guard enable/disable
- Level 4 (operators): debug commands

Debug commands are disabled by default and require both operator level 4 and `debug = true` in config.

---

## Known limitations

- The Emergency Guard system detects patterns and writes hints; it does not patch game rendering by default
- Report count in `/sa status` resets on each session (not persisted across restarts)
- `/sa submit confirm <prepared_id>` shows a "disabled in public builds" message; direct upload is intentionally not available

---

## Questions and bug reports

Open an issue at: https://github.com/Morikemuri/stutter-analyzer/issues

When reporting a problem with a freeze, run `/sa submit last` to get the local report file path, then paste the report contents into your issue.

---

**Author:** [Morikemuri](https://github.com/Morikemuri)
Minecraft 1.20.4 - Forge 49.2.0
