package com.digitaleye.backend.service;

import java.util.Locale;

/**
 * The categories an activity can be filed under. The order matters: the ordinal is also the
 * column index used by ActivityAnalytics.hourlyMs and the order the dashboard lists them in.
 *
 * scoreWeight is how much one active minute in the category counts towards the Digital
 * Activity Score (1.0 = fully productive, 0 = not at all). IDLE is not "active" time at all
 * and is handled separately by the score.
 */
public enum ActivityCategory {

    PRODUCTIVE("Productive", 1.0),
    NON_PRODUCTIVE("Non-Productive", 0.0),
    TROUBLESHOOTING("Troubleshooting", 0.75),
    CREATIVE("Creative", 0.75),
    OTHER("Uncategorized", 0.5),
    IDLE("Idle", 0.0);

    private final String label;
    private final double scoreWeight;

    ActivityCategory(String label, double scoreWeight) {
        this.label = label;
        this.scoreWeight = scoreWeight;
    }

    public String label() {
        return label;
    }

    public double scoreWeight() {
        return scoreWeight;
    }

    /** Index into ActivityAnalytics.hourlyMs[hour][...]. */
    public int column() {
        return ordinal();
    }

    /** True for everything except IDLE. */
    public boolean isActive() {
        return this != IDLE;
    }

    /**
     * Reads a category name as stored in activity_logs.category (or sent by the dashboard).
     * Old rows written before this feature say ENTERTAINMENT, which is now NON_PRODUCTIVE.
     * Anything unknown or missing counts as OTHER, never as an error.
     */
    public static ActivityCategory fromStored(String value) {
        if (value == null) {
            return OTHER;
        }
        String key = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        switch (key) {
            case "PRODUCTIVE":
                return PRODUCTIVE;
            case "NON_PRODUCTIVE":
            case "NONPRODUCTIVE":
            case "UNPRODUCTIVE":
            case "ENTERTAINMENT":
                return NON_PRODUCTIVE;
            case "TROUBLESHOOTING":
            case "TROUBLE_SHOOTING":
                return TROUBLESHOOTING;
            case "CREATIVE":
                return CREATIVE;
            case "IDLE":
                return IDLE;
            default:
                return OTHER;
        }
    }

    /** Like fromStored but returns null for blank / unrecognised input (used to validate API parameters). */
    public static ActivityCategory parseOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String key = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (ActivityCategory c : values()) {
            if (c.name().equals(key)) {
                return c;
            }
        }
        return "ENTERTAINMENT".equals(key) ? NON_PRODUCTIVE : null;
    }
}
