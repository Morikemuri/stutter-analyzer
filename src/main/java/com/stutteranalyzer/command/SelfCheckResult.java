package com.stutteranalyzer.command;

import java.util.ArrayList;
import java.util.List;

public class SelfCheckResult {

    public enum ItemStatus { OK, WARN, ERROR, UNAVAILABLE }

    public static final class CheckItem {
        public final String name;
        public final ItemStatus status;
        public final String note;
        CheckItem(String name, ItemStatus status, String note) {
            this.name = name; this.status = status; this.note = note;
        }
    }

    private final List<CheckItem> items = new ArrayList<>();

    public void ok(String name) { items.add(new CheckItem(name, ItemStatus.OK, "")); }
    public void warn(String name, String note) { items.add(new CheckItem(name, ItemStatus.WARN, note)); }
    public void error(String name, String note) { items.add(new CheckItem(name, ItemStatus.ERROR, note)); }
    public void unavailable(String name, String note) { items.add(new CheckItem(name, ItemStatus.UNAVAILABLE, note)); }

    public List<CheckItem> items() { return items; }

    public boolean isHealthy() {
        return items.stream().noneMatch(i -> i.status == ItemStatus.ERROR);
    }

    public String overall() {
        if (items.stream().anyMatch(i -> i.status == ItemStatus.ERROR)) return "degraded";
        if (items.stream().anyMatch(i -> i.status == ItemStatus.WARN)) return "warning";
        return "working";
    }
}
