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
    public transient String skippedDepName; // which dependency let this candidate down

    // resolved from Modrinth API at plan-build time
    public transient String resolvedUrl;
    public transient String resolvedFilename;
    public transient String resolvedSha512;
    public transient long resolvedFileSize;
    public transient boolean resolvedOnline;

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
            if (expandedInstalled.contains(c.toLowerCase())) return true;
        }
        return false;
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
