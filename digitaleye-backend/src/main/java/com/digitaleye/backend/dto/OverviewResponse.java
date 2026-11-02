package com.digitaleye.backend.dto;

import java.util.List;

/** Everything the dashboard home page needs besides the systems list and the recent alerts. */
public record OverviewResponse(
        String date,
        int totalSystems,
        int onlineSystems,
        Integer avgActivityScore,
        long logsToday,
        long alertsUnacknowledged,
        long alertsHigh,
        List<HourBucket> hourly) {
}
