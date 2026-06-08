package com.stutteranalyzer.submission;

public class ReportSanitizer {

    private static final String[] BLOCK_MARKERS = {
        "access_token", "session-token", "authorization:", "cookie:",
        "password=", "x-auth-key", "cf_api_token", "cloudflare_api_token",
        "github_token", "__secure-next-auth", "bearer cfut_", "bearer ghp_",
        "github_pat_",
    };

    public record SanitizeResult(String text, boolean hadSensitiveData, boolean wasModified) {}

    public static SanitizeResult sanitize(String input) {
        if (input == null || input.isBlank()) return new SanitizeResult("", false, false);

        boolean hadSensitive = hasSensitiveData(input);
        String s = input;
        boolean modified = false;

        // Redact Windows user paths: C:\Users\Username\ -> <redacted-user>\
        String afterWin = s.replaceAll(
            "(?i)[A-Za-z]:\\\\(?:Users|Benutzer|Utilisateurs|Usuarios)\\\\[^\\\\]+\\\\",
            "<redacted-user>\\\\");
        if (!afterWin.equals(s)) { s = afterWin; modified = true; }

        // Redact Linux/macOS user paths: /home/username/ or /Users/username/
        String afterLinux = s.replaceAll("/(?:home|Users)/[^/]+/", "<redacted-user>/");
        if (!afterLinux.equals(s)) { s = afterLinux; modified = true; }

        // Redact IPv4 addresses
        String afterIp = s.replaceAll(
            "\\b(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\b",
            "<redacted-ip>");
        if (!afterIp.equals(s)) { s = afterIp; modified = true; }

        return new SanitizeResult(s, hadSensitive, modified);
    }

    public static boolean hasSensitiveData(String input) {
        if (input == null) return false;
        String lower = input.toLowerCase();
        for (String marker : BLOCK_MARKERS) {
            if (lower.contains(marker)) return true;
        }
        return false;
    }
}
