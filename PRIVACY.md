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

## Automatic upload

**Automatic upload is disabled by default.**

The config option `enable_automatic_unknown_freeze_upload` defaults to `false`.

Reports are never uploaded without your explicit action.

## Manual submission

When you run `/sa submit last`, the default behavior (`submission_target = local`) is:

1. Show the local file path for the saved report
2. Show the GitHub issue URL
3. Ask you to review the file manually
4. **Not contact any external service**

To enable Gist upload, set `submission_target = github` in `stutteranalyzer-common.toml`.
Even with upload enabled, only the sanitized report is sent - never raw logs, usernames, or tokens.

## What is sanitized before upload

- Usernames in file paths replaced with `<username>`
- IP addresses replaced with `<ip-address>`
- Absolute file paths shortened to relative paths

## Third-party services

If `submission_target = github` is set, reports are uploaded to GitHub Gist anonymously.
No authentication token is used. No account is required.
GitHub Gist is a third-party service subject to GitHub's own privacy policy.

## Contact

GitHub issues: https://github.com/Morikemuri/stutter-analyzer/issues
