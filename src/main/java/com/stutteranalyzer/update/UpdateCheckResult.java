package com.stutteranalyzer.update;

public record UpdateCheckResult(
    boolean success,
    String latestVersion,
    boolean updateAvailable,
    String githubPage,
    String curseforgeUrl,
    String changelogUrl,
    String message,
    boolean critical,
    String errorReason,
    long checkedAtMs
) {
    public static UpdateCheckResult error(String reason) {
        return new UpdateCheckResult(false, null, false, null, null, null, null, false, reason,
            System.currentTimeMillis());
    }
}
