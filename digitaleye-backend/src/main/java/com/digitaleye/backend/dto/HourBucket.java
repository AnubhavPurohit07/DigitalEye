package com.digitaleye.backend.dto;

import com.digitaleye.backend.service.ActivityCategory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seconds spent in each category during one hour of the day - one bar of the timeline chart.
 * seconds is keyed by category name (PRODUCTIVE, NON_PRODUCTIVE, TROUBLESHOOTING, CREATIVE, OTHER, IDLE).
 */
public record HourBucket(int hour, Map<String, Long> seconds) {

    /** Converts the [24][categories] millisecond table from ActivityAnalytics into 24 buckets. */
    public static List<HourBucket> fromMillis(long[][] hourlyMs) {
        List<HourBucket> buckets = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            Map<String, Long> seconds = new LinkedHashMap<>();
            for (ActivityCategory category : ActivityCategory.values()) {
                seconds.put(category.name(), hourlyMs[hour][category.column()] / 1000);
            }
            buckets.add(new HourBucket(hour, seconds));
        }
        return buckets;
    }
}
