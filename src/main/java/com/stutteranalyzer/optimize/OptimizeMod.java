package com.stutteranalyzer.optimize;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class OptimizeMod {

    /** Strip hyphens, underscores and spaces, then lowercase - for fuzzy ID matching */
    public static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[\\s_\\-]", "");
    }

    public String id;
    public String displayName;
    public List<String> loaders = new ArrayList<>();
    public String environment; // "client", "server", "client_server"
    public String modrinthSlug;
    public List<String> aliases = new ArrayList<>();
    public List<String> conflicts = new ArrayList<>();
    public String reason;
    public int priority;
    public boolean safeDefault;
    public boolean primarySuggestion; // false for dep-only entries (priority 0)
    public List<String> installRequires = new ArrayList<>();

    public transient String depForMod;
    public transient String skipReason;
    public transient String skipConflictWith;  // display name of the mod we refused to coexist with
    public transient String skipMissingDep;    // display name of the dep we could not bring along

    // resolved from Modrinth API at plan-build time
    public transient String resolvedUrl;
    public transient String resolvedFilename;
    public transient String resolvedSha512;
    public transient long resolvedFileSize;
    public transient boolean resolvedOnline;
    public transient String resolvedVersion;   // Modrinth version_number, e.g. "mc1.21.4-0.6.13"

    public boolean supportsLoader(String loader) {
        if (loaders == null || loaders.isEmpty()) return true;
        for (String l : loaders) {
            if (l.equalsIgnoreCase(loader)) return true;
        }
        return false;
    }

    public boolean supportsEnvironment(boolean isServer) {
        if (environment == null || environment.isEmpty()) return true;
        if (isServer) return !"client".equalsIgnoreCase(environment);
        return !"server".equalsIgnoreCase(environment);
    }

    public boolean conflictsWith(Set<String> expandedInstalled) {
        if (conflicts == null) return false;
        for (String c : conflicts) {
            // Plain tokens only here - versioned ones ("sodium>=0.6") get the
            // full treatment in OptimizeAssistant.validatePlanSet
            if (expandedInstalled.contains(c.toLowerCase())) return true;
        }
        return false;
    }

    /** True if this mod answers to the given id (own id, slug, or any alias). */
    public boolean matchesId(String targetId) {
        String norm = normalize(targetId);
        if (normalize(id).equals(norm)) return true;
        if (modrinthSlug != null && normalize(modrinthSlug).equals(norm)) return true;
        if (aliases != null) {
            for (String a : aliases) {
                if (normalize(a).equals(norm)) return true;
            }
        }
        return false;
    }

    // --- Versioned conflict support ---------------------------------------
    // Conflict tokens are either plain ids ("embeddium") or carry a range
    // ("sodium>=0.6"). The second kind exists because Cull Less Leaves once
    // partied with Sodium 0.8 and the game refused to wake up afterwards.

    private static final java.util.regex.Pattern RANGE_TOKEN =
        java.util.regex.Pattern.compile("^([a-zA-Z0-9_\\-]+?)\\s*(>=|<=|>|<|==?)\\s*([\\w.\\-+]+)$");

    /** Mod id part of a conflict token: "sodium>=0.6" -> "sodium". */
    public static String conflictTargetId(String token) {
        java.util.regex.Matcher m = RANGE_TOKEN.matcher(token.trim());
        return m.matches() ? m.group(1).toLowerCase() : token.trim().toLowerCase();
    }

    /** True if the token carries a version restriction. */
    public static boolean isVersionedConflict(String token) {
        return RANGE_TOKEN.matcher(token.trim()).matches();
    }

    /**
     * Does the target's version fall inside the conflicting range?
     * Unknown or unparseable versions count as conflicting - a skipped
     * leaf-culling mod is annoying, a crash on boot is a support ticket.
     */
    public static boolean versionConflicts(String token, String targetVersion) {
        java.util.regex.Matcher m = RANGE_TOKEN.matcher(token.trim());
        if (!m.matches()) return true; // plain token: presence alone is enough
        String op = m.group(2);
        String bound = m.group(3);
        String cleaned = cleanVersion(targetVersion);
        if (cleaned.isEmpty() || !Character.isDigit(cleaned.charAt(0))) return true;
        int cmp = compareVersions(cleaned, cleanVersion(bound));
        switch (op) {
            case ">=": return cmp >= 0;
            case ">":  return cmp > 0;
            case "<=": return cmp <= 0;
            case "<":  return cmp < 0;
            default:   return cmp == 0; // "=" or "=="
        }
    }

    /** Strip build metadata, mc-version tags and loader names: "mc1.21.4-0.8.12-neoforge" -> "0.8.12" */
    public static String cleanVersion(String raw) {
        if (raw == null) return "";
        String v = raw.trim().toLowerCase();
        int plus = v.indexOf('+');
        if (plus >= 0) v = v.substring(0, plus);
        v = v.replaceAll("mc\\d+(\\.\\d+)*", "");
        v = v.replaceAll("(fabric|neoforge|forge|quilt)", "");
        v = v.replaceAll("^[^0-9]*", "").replaceAll("[-._]+$", "");
        return v;
    }

    /** Segment-wise version comparison; numeric segments compare numerically. */
    public static int compareVersions(String a, String b) {
        String[] pa = a.split("[.\\-]");
        String[] pb = b.split("[.\\-]");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            String sa = i < pa.length ? pa[i] : "";
            String sb = i < pb.length ? pb[i] : "";
            if (sa.equals(sb)) continue;
            boolean na = sa.matches("\\d+");
            boolean nb = sb.matches("\\d+");
            if (na && nb) {
                int c = Long.compare(Long.parseLong(sa), Long.parseLong(sb));
                if (c != 0) return c;
            } else if (na || nb) {
                // "0.8.12" vs "0.8.12-alpha": the one with the numeric tail wins
                return na ? 1 : -1;
            } else {
                int c = sa.compareTo(sb);
                if (c != 0) return c;
            }
        }
        return 0;
    }

    public boolean alreadyInstalled(Set<String> normalizedInstalled) {
        // Exact lowercase match
        if (normalizedInstalled.contains(id.toLowerCase())) return true;
        // Modrinth slug exact match
        if (modrinthSlug != null && normalizedInstalled.contains(modrinthSlug.toLowerCase())) return true;
        // Aliases exact match
        if (aliases != null) {
            for (String alias : aliases) {
                if (normalizedInstalled.contains(alias.toLowerCase())) return true;
            }
        }
        // Normalized match: strip hyphens/underscores/spaces
        String idNorm = normalize(id);
        if (normalizedInstalled.contains(idNorm)) return true;
        if (modrinthSlug != null && normalizedInstalled.contains(normalize(modrinthSlug))) return true;
        if (aliases != null) {
            for (String alias : aliases) {
                if (normalizedInstalled.contains(normalize(alias))) return true;
            }
        }
        return false;
    }
}
