# Privacy

StutterAnalyzer collects performance data locally on your device only.

## What the mod collects

- Frame time measurements (client-side)
- Server tick duration (MSPT) measurements
- Memory and garbage collection timing
- Recent in-game event timeline (chunk loads, entity spawns, etc.)
- Installed mod list (for known pattern detection)
- System information (Java version, OS, CPU, RAM)
- Crash report files from `crash-reports/` directory

All data is stored locally in `config/stutter-analyzer/reports/`.

## What is NOT collected

- Usernames (redacted by default)
- IP addresses (redacted by default)
- Server addresses
- File paths containing usernames (redacted by default)
- Authentication or session tokens
- Any data sent automatically or silently

## No token storage

StutterAnalyzer does not store GitHub tokens.

No token field exists in the config file.

No token is shipped inside the jar.

No token is written to logs, reports, crash reports, or chat messages.

If you ever see a mod asking you to put a GitHub token into a Minecraft config file, do not do it.

## No silent upload

**Nothing is uploaded automatically.**

The config option `enable_automatic_unknown_freeze_upload` defaults to `false` and there is no upload path enabled in public builds.

## Manual submission

When you run `/sa submit last`, the mod:

1. Creates three files in `config/stutter-analyzer/submissions/`:
   - `<id>.md` - the freeze report
   - `<id>.json` - machine-readable version
   - `<id>-github-issue-body.md` - pre-filled GitHub issue template
2. Shows the file paths in chat
3. On a client: copies the issue body to clipboard and opens the GitHub issue page in the browser
4. On a dedicated server: prints the paths and issue URL to console

**No external service is contacted. No upload is performed.**

You review the files yourself and decide what to include when opening a GitHub issue.

## What is sanitized

Before files are written, the following is redacted (configurable):

- Usernames in file paths replaced with `<username>`
- IP addresses replaced with `<ip-address>`
- Absolute file paths shortened to relative paths

## Update checks

StutterAnalyzer includes an optional update checker that downloads a public `version.json` file from GitHub.

What the update checker does:
- Sends a single GET request to `https://raw.githubusercontent.com/Morikemuri/stutter-analyzer/main/version.json`
- Runs in a background thread after a configurable delay (default: 10 seconds after startup)
- Times out after 5 seconds if the server is unreachable

What the update checker does NOT send:
- No reports
- No mod list
- No username
- No logs
- No system details

The only outbound network request is the GET to `version.json`. No data is included in that request beyond the standard HTTP connection (your IP is visible to GitHub's CDN as with any normal web request).

Update checks can be disabled entirely:

```toml
[updates]
check_for_updates = false
```

The cached result is stored in `config/stutter-analyzer/update-cache.json`. This file contains only the latest version string and a timestamp. It does not contain any personal data.

## No third-party services

Public builds of StutterAnalyzer do not contact GitHub, GitHub Gist, or any other external service, except for the optional update checker described above.

The GitHub issue URL (default: `https://github.com/Morikemuri/stutter-analyzer/issues/new`) is only opened in your browser if you have `open_issue_url_on_client = true` in config (default: true). No data is sent to that URL automatically.

## Contact

GitHub issues: https://github.com/Morikemuri/stutter-analyzer/issues
