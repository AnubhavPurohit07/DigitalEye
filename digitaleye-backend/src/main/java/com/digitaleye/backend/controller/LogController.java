package com.digitaleye.backend.controller;

import com.digitaleye.backend.dto.LogPage;
import com.digitaleye.backend.service.TimeUtil;
import com.digitaleye.database.dao.ActivityLogDao;
import com.digitaleye.database.model.ActivityLogFilter;
import com.digitaleye.database.model.ActivityLogView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** The searchable, filterable, paged Activity Logs list. */
@RestController
@RequestMapping("/api/admin")
public class LogController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ActivityLogDao activityLogDao = new ActivityLogDao();

    @GetMapping("/logs")
    public LogPage logs(
            @RequestParam(name = "systemId", required = false) Long systemId,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "25") int size) {
        try {
            int safePage = Math.max(0, page);
            int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));

            ActivityLogFilter filter = new ActivityLogFilter();
            filter.setSystemId(systemId);
            filter.setCategory(category);
            filter.setStatus(status);
            filter.setApplicationQuery(query);
            if (from != null && !from.isBlank()) {
                filter.setFrom(TimeUtil.ts(TimeUtil.dayStart(TimeUtil.parseDateOrToday(from))));
            }
            if (to != null && !to.isBlank()) {
                // "to" is an inclusive calendar day, so the exclusive limit is the start of the next day.
                LocalDate lastDay = TimeUtil.parseDateOrToday(to);
                filter.setTo(TimeUtil.ts(TimeUtil.dayEnd(lastDay)));
            }

            long total = activityLogDao.countSearch(filter);
            List<ActivityLogView> rows = activityLogDao.search(filter, safeSize, safePage * safeSize);

            Instant now = Instant.now();
            List<LogPage.LogRow> items = new ArrayList<>();
            for (ActivityLogView row : rows) {
                Instant start = row.getStartTime().toInstant();
                Instant end = row.getEndTime() == null ? now : row.getEndTime().toInstant();
                items.add(new LogPage.LogRow(
                        row.getId(), row.getSystemId(), row.getSystemName(), row.getApplicationName(),
                        row.getStatus(), AnalyticsController.displayCategory(row.getStatus(), row.getCategory()),
                        TimeUtil.iso(row.getStartTime()),
                        TimeUtil.iso(row.getEndTime()), Math.max(0, Duration.between(start, end).getSeconds()),
                        row.getEndTime() == null));
            }
            return new LogPage(items, total, safePage, safeSize);
        } catch (SQLException e) {
            throw new RuntimeException("Database error while searching activity logs", e);
        }
    }
}
