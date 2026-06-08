package com.stutteranalyzer.update;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SemanticVersion implements Comparable<SemanticVersion> {

    private static final Pattern PATTERN = Pattern.compile(
        "(\\d+)\\.(\\d+)\\.(\\d+)(?:-([a-zA-Z]+)(\\d*))?"
    );

    private final int major, minor, patch;
    private final String preType; // null = release
    private final int preNum;

    private SemanticVersion(int major, int minor, int patch, String preType, int preNum) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.preType = preType;
        this.preNum = preNum;
    }

    public static SemanticVersion parse(String version) {
        if (version == null) return null;
        Matcher m = PATTERN.matcher(version.trim());
        if (!m.matches()) return null;
        int major = Integer.parseInt(m.group(1));
        int minor = Integer.parseInt(m.group(2));
        int patch = Integer.parseInt(m.group(3));
        String preType = m.group(4);
        int preNum = 0;
        if (m.group(5) != null && !m.group(5).isEmpty()) {
            try { preNum = Integer.parseInt(m.group(5)); } catch (NumberFormatException ignored) {}
        }
        return new SemanticVersion(major, minor, patch, preType, preNum);
    }

    private int preOrder() {
        if (preType == null) return 100;
        return switch (preType.toLowerCase()) {
            case "alpha" -> 1;
            case "beta"  -> 2;
            case "rc"    -> 3;
            default      -> 0;
        };
    }

    @Override
    public int compareTo(SemanticVersion other) {
        int c;
        if ((c = Integer.compare(major, other.major)) != 0) return c;
        if ((c = Integer.compare(minor, other.minor)) != 0) return c;
        if ((c = Integer.compare(patch, other.patch)) != 0) return c;
        if ((c = Integer.compare(preOrder(), other.preOrder())) != 0) return c;
        return Integer.compare(preNum, other.preNum);
    }

    /** Returns true if remote is strictly newer than local. Falls back to string compare if parsing fails. */
    public static boolean isNewer(String remote, String local) {
        SemanticVersion r = parse(remote);
        SemanticVersion l = parse(local);
        if (r == null || l == null) return remote != null && !remote.equals(local);
        return r.compareTo(l) > 0;
    }
}
