# Changelog

## 0.1.0-beta

Initial public beta release.

### Features

- Client-side frame time stutter detection with configurable thresholds
- Server-side MSPT/TPS spike detection
- Freeze classification with confidence scoring
- UNKNOWN_FREEZE category for events the analyzer cannot classify with enough evidence
- F3 debug screen status line (green/yellow/red)
- Reports saved as Markdown and JSON in `config/stutter-analyzer/reports/`
- Previous crash report importer (scans `crash-reports/` on startup)
- Known crash pattern detection (Rubidium lava crash, C2ME deadlock, Distant Horizons, etc.)
- Emergency Compatibility Guard system (warn-only and report-only modes by default)
- `/sa selfcheck` - full self-test showing subsystem status
- `/sa status` - live state: side, trackers, last event, report count, unknown freezes, emergency mode, submission mode
- `/sa health` - detailed subsystem health report
- `/sa submit last` - local-only by default, shows file path and GitHub issue URL, no automatic upload
- Russian (ru_ru) language support
- Dedicated server support - no client classloading on server

### Privacy

- Automatic upload disabled by default
- Manual submission defaults to local-only (no network contact)
- Usernames and IP addresses redacted from reports by default
- Reports stored locally only unless the user explicitly opts in to GitHub Gist upload

### Known limitations

- `/sa debug freeze client <ms>` not yet wired to generate a report on the client side
- `/sa debug generate-test-report` not yet implemented
- `/sa client` subcommands are stubbed for future use
- The Emergency Guard system detects known patterns and writes hints but does not patch game rendering by default
