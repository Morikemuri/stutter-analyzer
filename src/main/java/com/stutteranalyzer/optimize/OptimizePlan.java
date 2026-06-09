package com.stutteranalyzer.optimize;

import java.util.ArrayList;
import java.util.List;

public class OptimizePlan {

    public enum RiskLevel {
        LOW, MEDIUM, HIGH;

        public String label() {
            String n = name();
            return n.charAt(0) + n.substring(1).toLowerCase();
        }
    }

    public List<OptimizeMod> recommended = new ArrayList<>();
    public List<String> alreadyInstalled = new ArrayList<>();
    public RiskLevel risk = RiskLevel.MEDIUM;
    public String riskReason = "";
    public String loader = "";
    public String mcVersion = "";
    public boolean serverOnly;
    public int totalInstalledCount;
    public long createdAt = System.currentTimeMillis();

    public boolean isEmpty() {
        return recommended.isEmpty();
    }

    public boolean isExpired(long windowMs) {
        return System.currentTimeMillis() - createdAt > windowMs;
    }
}
