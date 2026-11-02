package com.digitaleye.backend.dto;

import java.util.List;
import java.util.Map;

/** Everything the client detail page shows for one system on one day. */
public record SystemAnalyticsResponse(
        String date,
        SystemSummaryResponse system,
        Summary summary,
        Deltas deltas,
        List<HourBucket> hourly,
        List<CategorySlice> categories,
        List<AppUsage> appUsage,
        List<LogEntry> recentLogs,
        List<ScreenshotInfo> recentScreenshots) {

    /** categorySeconds: active time per category name (idle is reported separately as idleSeconds). */
    public record Summary(
            long activeSeconds,
            long idleSeconds,
            Map<String, Long> categorySeconds,
            Integer activityScore,
            int applicationsUsed,
            int sessionCount,
            int idlePeriods,
            long longestIdleSeconds,
            long alertsTriggered) {
    }

    /** Percentage change versus the previous day; any value is null when there is nothing to compare to. */
    public record Deltas(
            Double activeSeconds,
            Double idleSeconds,
            Double activityScore,
            Double applicationsUsed,
            Double alertsTriggered) {
    }

    public record CategorySlice(String category, long seconds, double percent) {
    }

    public record AppUsage(String application, String category, long seconds, double percent) {
    }

    public record LogEntry(Long id, String start, String end, String application,
                           String status, String category, long durationSeconds) {
    }
}
