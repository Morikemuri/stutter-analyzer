package com.stutteranalyzer.optimize;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class OptimizeMod {

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
        if (normalizedInstalled.contains(id.toLowerCase())) return true;
        if (aliases != null) {
            for (String alias : aliases) {
                if (normalizedInstalled.contains(alias.toLowerCase())) return true;
            }
        }
        return false;
    }
}
