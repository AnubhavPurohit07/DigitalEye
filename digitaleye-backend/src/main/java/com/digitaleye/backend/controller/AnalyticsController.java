package com.digitaleye.backend.controller;

import com.digitaleye.backend.dto.HourBucket;
import com.digitaleye.backend.dto.OverviewResponse;
import com.digitaleye.backend.dto.ScreenshotInfo;
import com.digitaleye.backend.dto.SystemAnalyticsResponse;
import com.digitaleye.backend.service.ActivityAnalytics;
import com.digitaleye.backend.service.ActivityCategory;
import com.digitaleye.backend.service.SystemSummaryService;
import com.digitaleye.backend.service.TimeUtil;
import com.digitaleye.database.dao.ActivityLogDao;
import com.digitaleye.database.dao.AlertDao;
import com.digitaleye.database.dao.MonitoredSystemDao;
import com.digitaleye.database.dao.ScreenshotDao;
import com.digitaleye.database.model.ActivityLog;
import com.digitaleye.database.model.MonitoredSystem;
import com.digitaleye.database.model.Screenshot;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The numbers behind the dashboard's charts and cards. Everything is computed on demand
 * from activity_logs (see ActivityAnalytics) - nothing is pre-aggregated or stored.
 */
@RestController
@RequestMapping("/api/admin")
public class AnalyticsController {

    private static final int RECENT_LOGS = 15;
    private static final int RECENT_SHOTS = 6;
    private static final int TOP_APPS = 10;

    private final MonitoredSystemDao systemDao = new MonitoredSystemDao();
    private final ActivityLogDao activityLogDao = new ActivityLogDao();
    private final AlertDao alertDao = new AlertDao();
    private final ScreenshotDao screenshotDao = new ScreenshotDao();

    private final SystemSummaryService summaryService;

    public AnalyticsController(SystemSummaryService summaryService) {
        this.summaryService = summaryService;
    }

    /** Dashboard home: cards + the "Overall Activity" hourly chart, across every system. */
    @GetMapping("/overview")
    public OverviewResponse overview(@RequestParam(name = "date", required = false) String date) {
        try {
            LocalDate day = TimeUtil.parseDateOrToday(date);
            Instant start = TimeUtil.dayStart(day);
            Instant end = TimeUtil.dayEnd(day);
            Instant now = Instant.now();

            List<MonitoredSystem> systems = systemDao.findAll();
            int online = 0;
            for (MonitoredSystem s : systems) {
                if ("ONLINE".equals(s.getStatus())) {
                    online++;
                }
            }

            Map<Long, List<ActivityLog>> bySystem = new LinkedHashMap<>();
            for (ActivityLog log : activityLogDao.findOverlappingAll(TimeUtil.ts(start), TimeUtil.ts(end))) {
                bySystem.computeIfAbsent(log.getSystemId(), k -> new ArrayList<>()).add(log);
            }

            long[][] totalHourly = new long[24][ActivityAnalytics.CATEGORY_COUNT];
            int scoreSum = 0;
            int scoreCount = 0;
            for (List<ActivityLog> logs : bySystem.values()) {
                ActivityAnalytics.Summary summary = ActivityAnalytics.compute(logs, start, end, now, TimeUtil.ZONE);
                for (int h = 0; h < 24; h++) {
                    for (int c = 0; c < ActivityAnalytics.CATEGORY_COUNT; c++) {
                        totalHourly[h][c] += summary.hourlyMs[h][c];
                    }
                }
                Integer score = summary.activityScore();
                if (score != null) {
                    scoreSum += score;
                    scoreCount++;
                }
            }

            Integer avgScore = scoreCount == 0 ? null : (int) Math.round((double) scoreSum / scoreCount);

            return new OverviewResponse(
                    day.toString(),
                    systems.size(),
                    online,
                    avgScore,
                    activityLogDao.countStartedBetween(TimeUtil.ts(start), TimeUtil.ts(end)),
                    alertDao.count(null, null, false),
                    alertDao.count(null, "HIGH", false),
                    HourBucket.fromMillis(totalHourly));
        } catch (SQLException e) {
            throw new RuntimeException("Database error while building the overview", e);
        }
    }

    /** Client detail page: one system, one day (default today). */
    @GetMapping("/systems/{id}/analytics")
    public SystemAnalyticsResponse systemAnalytics(@PathVariable("id") Long id,
                                                   @RequestParam(name = "date", required = false) String date) {
        try {
            MonitoredSystem system = systemDao.findById(id);
            if (system == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such system");
            }

            LocalDate day = TimeUtil.parseDateOrToday(date);
            Instant start = TimeUtil.dayStart(day);
            Instant end = TimeUtil.dayEnd(day);
            Instant prevStart = TimeUtil.dayStart(day.minusDays(1));
            Instant now = Instant.now();

            List<ActivityLog> logs = activityLogDao.findOverlapping(id, TimeUtil.ts(start), TimeUtil.ts(end));
            List<ActivityLog> prevLogs = activityLogDao.findOverlapping(id, TimeUtil.ts(prevStart), TimeUtil.ts(start));

            ActivityAnalytics.Summary cur = ActivityAnalytics.compute(logs, start, end, now, TimeUtil.ZONE);
            ActivityAnalytics.Summary prev = ActivityAnalytics.compute(prevLogs, prevStart, start, now, TimeUtil.ZONE);

            long alertsNow = alertDao.countBySystemBetween(id, TimeUtil.ts(start), TimeUtil.ts(end));
            long alertsPrev = alertDao.countBySystemBetween(id, TimeUtil.ts(prevStart), TimeUtil.ts(start));

            SystemAnalyticsResponse.Summary summary = new SystemAnalyticsResponse.Summary(
                    cur.activeSeconds(), cur.idleSeconds(),
                    activeCategorySeconds(cur),
                    cur.activityScore(), cur.applicationsUsed(), cur.sessionCount, cur.idlePeriods,
                    cur.longestIdleMs / 1000, alertsNow);

            Integer curScore = cur.activityScore();
            Integer prevScore = prev.activityScore();
            SystemAnalyticsResponse.Deltas deltas = new SystemAnalyticsResponse.Deltas(
                    ActivityAnalytics.percentChange(cur.activeMs, prev.activeMs),
                    ActivityAnalytics.percentChange(cur.idleMs, prev.idleMs),
                    curScore == null || prevScore == null ? null : ActivityAnalytics.percentChange(curScore, prevScore),
                    ActivityAnalytics.percentChange(cur.applicationsUsed(), prev.applicationsUsed()),
                    ActivityAnalytics.percentChange(alertsNow, alertsPrev));

            return new SystemAnalyticsResponse(
                    day.toString(),
                    summaryService.summarize(system),
                    summary,
                    deltas,
                    HourBucket.fromMillis(cur.hourlyMs),
                    buildCategories(cur),
                    buildAppUsage(cur),
                    buildRecentLogs(logs, now),
                    buildRecentScreenshots(id, start, end));
        } catch (SQLException e) {
            throw new RuntimeException("Database error while building system analytics", e);
        }
    }

    /** Seconds of active time per category name, in display order. IDLE is not part of this - it is reported on its own. */
    private Map<String, Long> activeCategorySeconds(ActivityAnalytics.Summary s) {
        Map<String, Long> seconds = new LinkedHashMap<>();
        for (ActivityCategory category : ActivityCategory.values()) {
            if (category.isActive()) {
                seconds.put(category.name(), s.millis(category) / 1000);
            }
        }
        return seconds;
    }

    /** Every category (idle included) as a share of all tracked time - the numbers behind the donut chart. */
    private List<SystemAnalyticsResponse.CategorySlice> buildCategories(ActivityAnalytics.Summary s) {
        long total = s.activeMs + s.idleMs;
        List<SystemAnalyticsResponse.CategorySlice> slices = new ArrayList<>();
        for (ActivityCategory category : ActivityCategory.values()) {
            slices.add(slice(category.name(), s.millis(category), total));
        }
        return slices;
    }

    private SystemAnalyticsResponse.CategorySlice slice(String category, long ms, long totalMs) {
        double percent = totalMs == 0 ? 0 : Math.round(ms * 1000.0 / totalMs) / 10.0;
        return new SystemAnalyticsResponse.CategorySlice(category, ms / 1000, percent);
    }

    /** Top applications by time; one row per (application, category) so mixed use of a browser stays visible. */
    private List<SystemAnalyticsResponse.AppUsage> buildAppUsage(ActivityAnalytics.Summary s) {
        List<ActivityAnalytics.AppAgg> entries = new ArrayList<>(s.apps.values());
        entries.sort(Comparator.comparingLong((ActivityAnalytics.AppAgg e) -> e.totalMs).reversed());

        List<SystemAnalyticsResponse.AppUsage> usage = new ArrayList<>();
        for (ActivityAnalytics.AppAgg entry : entries) {
            if (usage.size() >= TOP_APPS) {
                break;
            }
            double percent = s.activeMs == 0 ? 0 : Math.round(entry.totalMs * 1000.0 / s.activeMs) / 10.0;
            usage.add(new SystemAnalyticsResponse.AppUsage(
                    entry.label, entry.category.name(), entry.totalMs / 1000, percent));
        }
        return usage;
    }

    /** Newest sessions first, limited to the selected day. */
    private List<SystemAnalyticsResponse.LogEntry> buildRecentLogs(List<ActivityLog> dayLogs, Instant now) {
        List<SystemAnalyticsResponse.LogEntry> entries = new ArrayList<>();
        for (int i = dayLogs.size() - 1; i >= 0 && entries.size() < RECENT_LOGS; i--) {
            ActivityLog log = dayLogs.get(i);
            Instant logStart = log.getStartTime().toInstant();
            Instant logEnd = log.getEndTime() == null ? now : log.getEndTime().toInstant();
            entries.add(new SystemAnalyticsResponse.LogEntry(
                    log.getId(), TimeUtil.iso(log.getStartTime()), TimeUtil.iso(log.getEndTime()),
                    log.getApplicationName(), log.getStatus(), displayCategory(log.getStatus(), log.getCategory()),
                    Math.max(0, Duration.between(logStart, logEnd).getSeconds())));
        }
        return entries;
    }

    /** The category name shown to the dashboard: old ENTERTAINMENT rows read as NON_PRODUCTIVE, idle rows as IDLE. */
    static String displayCategory(String status, String storedCategory) {
        if ("IDLE".equalsIgnoreCase(status)) {
            return ActivityCategory.IDLE.name();
        }
        ActivityCategory category = ActivityCategory.fromStored(storedCategory);
        return (category == ActivityCategory.IDLE ? ActivityCategory.OTHER : category).name();
    }

    private List<ScreenshotInfo> buildRecentScreenshots(Long systemId, Instant start, Instant end) throws SQLException {
        List<ScreenshotInfo> infos = new ArrayList<>();
        for (Screenshot shot : screenshotDao.findBySystemBetween(
                systemId, TimeUtil.ts(start), TimeUtil.ts(end), RECENT_SHOTS, false)) {
            infos.add(new ScreenshotInfo(shot.getId(), shot.getSystemId(),
                    TimeUtil.iso(shot.getTakenAt()), shot.getApplicationName()));
        }
        return infos;
    }
}
