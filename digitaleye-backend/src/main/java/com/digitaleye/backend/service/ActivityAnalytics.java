package com.digitaleye.backend.service;

import com.digitaleye.database.model.ActivityLog;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns raw activity_logs sessions into the numbers the dashboard shows: time per
 * category, an hour-by-hour timeline, per-application usage, and the Digital Activity
 * Score. Pure calculation - no database, no Spring - so it is easy to test.
 *
 * Every session is first clipped to the requested time window, so a session that
 * began yesterday or is still running right now only counts for the part inside it.
 */
public final class ActivityAnalytics {

    /** Number of columns in the hourly table: one per ActivityCategory (IDLE is the last). */
    public static final int CATEGORY_COUNT = ActivityCategory.values().length;

    private ActivityAnalytics() {
    }

    /**
     * Time spent in one application under one category. Usage is grouped by (application, category),
     * so a browser that was used for both work and entertainment shows up as two rows - one per kind
     * of use - instead of a single row with a misleading category.
     */
    public static final class AppAgg {
        public final String label;
        public final ActivityCategory category;
        public long totalMs;

        AppAgg(String label, ActivityCategory category) {
            this.label = label;
            this.category = category;
        }
    }

    public static final class Summary {
        public long activeMs;
        public long idleMs;
        /** Milliseconds per category, indexed by ActivityCategory.column(). The IDLE slot equals idleMs. */
        public final long[] categoryMs = new long[CATEGORY_COUNT];
        /** [hour of day 0-23][ActivityCategory.column()] in milliseconds. */
        public final long[][] hourlyMs = new long[24][CATEGORY_COUNT];
        /** Non-idle time per (application, category), in first-seen order. */
        public final Map<String, AppAgg> apps = new LinkedHashMap<>();
        private final Set<String> appLabels = new HashSet<>();
        /** How many separate (non-idle) sessions fall inside the window. */
        public int sessionCount;
        public int idlePeriods;
        public long longestIdleMs;

        public long millis(ActivityCategory category) {
            return categoryMs[category.column()];
        }

        public long activeSeconds() {
            return activeMs / 1000;
        }

        public long idleSeconds() {
            return idleMs / 1000;
        }

        /** Distinct applications, however many categories each one was used under. */
        public int applicationsUsed() {
            return appLabels.size();
        }

        /**
         * The Digital Activity Score (0-100) - a usage score, NOT a measure of anyone's
         * wellbeing. It blends two things:
         *   - how much of the tracked time the user was active rather than idle (30%)
         *   - how that active time was spent (70%): every category has a weight (see
         *     ActivityCategory.scoreWeight): productive 1.0, creative and troubleshooting 0.75,
         *     uncategorized 0.5, non-productive 0
         * Returns null until at least a minute has been tracked, so a brand-new
         * system doesn't show a meaningless score.
         */
        public Integer activityScore() {
            long total = activeMs + idleMs;
            if (total < 60_000) {
                return null;
            }
            double activeRatio = (double) activeMs / total;
            double quality = 0;
            if (activeMs > 0) {
                double weighted = 0;
                for (ActivityCategory category : ActivityCategory.values()) {
                    if (category.isActive()) {
                        weighted += category.scoreWeight() * millis(category);
                    }
                }
                quality = weighted / activeMs;
            }
            return (int) Math.round(100 * (0.3 * activeRatio + 0.7 * quality));
        }
    }

    public static Summary compute(List<ActivityLog> sessions, Instant windowStart, Instant windowEnd,
                                  Instant now, ZoneId zone) {
        Summary summary = new Summary();

        for (ActivityLog session : sessions) {
            if (session.getStartTime() == null) {
                continue;
            }
            Instant start = session.getStartTime().toInstant();
            Instant end = session.getEndTime() == null ? now : session.getEndTime().toInstant();

            Instant clippedStart = start.isAfter(windowStart) ? start : windowStart;
            Instant clippedEnd = end.isBefore(windowEnd) ? end : windowEnd;
            if (!clippedEnd.isAfter(clippedStart)) {
                continue;
            }

            long ms = Duration.between(clippedStart, clippedEnd).toMillis();
            boolean idle = "IDLE".equalsIgnoreCase(session.getStatus());
            ActivityCategory category;
            if (idle) {
                category = ActivityCategory.IDLE;
            } else {
                category = ActivityCategory.fromStored(session.getCategory());
                if (category == ActivityCategory.IDLE) {
                    category = ActivityCategory.OTHER; // an active session can never be "idle"
                }
            }

            summary.categoryMs[category.column()] += ms;
            if (idle) {
                summary.idleMs += ms;
                summary.idlePeriods++;
                summary.longestIdleMs = Math.max(summary.longestIdleMs, ms);
            } else {
                summary.activeMs += ms;
                summary.sessionCount++;

                String label = appLabel(session.getApplicationName());
                summary.appLabels.add(label);
                String key = label + "\0" + category.name();
                AppAgg agg = summary.apps.get(key);
                if (agg == null) {
                    agg = new AppAgg(label, category);
                    summary.apps.put(key, agg);
                }
                agg.totalMs += ms;
            }

            addToHourlyBuckets(summary.hourlyMs, clippedStart, clippedEnd, category.column(), zone);
        }
        return summary;
    }

    /** Splits [start, end) at every hour boundary so each hour's bucket gets its own share. */
    private static void addToHourlyBuckets(long[][] hourly, Instant start, Instant end, int column, ZoneId zone) {
        ZonedDateTime cursor = start.atZone(zone);
        ZonedDateTime endZoned = end.atZone(zone);

        while (cursor.isBefore(endZoned)) {
            ZonedDateTime next = cursor.truncatedTo(ChronoUnit.HOURS).plusHours(1);
            if (next.isAfter(endZoned)) {
                next = endZoned;
            }
            hourly[cursor.getHour()][column] += Duration.between(cursor, next).toMillis();
            cursor = next;
        }
    }

    /**
     * Window titles look like "Document1 - Word" or "YouTube - Google Chrome". Grouping by the
     * full title would make one row per document/tab, so usage is grouped by whatever follows
     * the last " - " (the application itself). Titles without a separator are used as they are.
     */
    public static String appLabel(String title) {
        if (title == null || title.isBlank()) {
            return "Unknown";
        }
        String trimmed = title.trim();
        int idx = Math.max(trimmed.lastIndexOf(" - "),
                Math.max(trimmed.lastIndexOf(" \u2013 "), trimmed.lastIndexOf(" \u2014 ")));
        String label = idx >= 0 ? trimmed.substring(idx + 3).trim() : trimmed;
        if (label.isEmpty()) {
            label = trimmed;
        }
        return label.length() > 60 ? label.substring(0, 57) + "..." : label;
    }

    /** Percentage change from previous to current, one decimal place; null when there is nothing to compare to. */
    public static Double percentChange(double current, double previous) {
        if (previous == 0) {
            return null;
        }
        return Math.round(((current - previous) / previous) * 1000.0) / 10.0;
    }
}
