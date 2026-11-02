package com.digitaleye.backend.service;

import com.digitaleye.backend.dto.SystemSummaryResponse;
import com.digitaleye.database.dao.ActivityLogDao;
import com.digitaleye.database.dao.MonitoredSystemDao;
import com.digitaleye.database.model.ActivityLog;
import com.digitaleye.database.model.MonitoredSystem;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the per-system card the dashboard shows: the system's own fields, what it is
 * doing right now, and today's Activity Score. Used by both the Client Systems list and
 * the client detail page, so the two never disagree.
 */
@Service
public class SystemSummaryService {

    private final MonitoredSystemDao systemDao = new MonitoredSystemDao();
    private final ActivityLogDao activityLogDao = new ActivityLogDao();

    public List<SystemSummaryResponse> summarizeAll() throws SQLException {
        List<SystemSummaryResponse> result = new ArrayList<>();
        for (MonitoredSystem system : systemDao.findAll()) {
            result.add(summarize(system));
        }
        return result;
    }

    public SystemSummaryResponse summarize(MonitoredSystem system) throws SQLException {
        SystemSummaryResponse dto = new SystemSummaryResponse();
        dto.setId(system.getId());
        dto.setUserId(system.getUserId());
        dto.setSystemName(system.getSystemName());
        dto.setStatus(system.getStatus());
        dto.setLastHeartbeat(TimeUtil.iso(system.getLastHeartbeat()));
        dto.setMonitoringSince(TimeUtil.iso(system.getMonitoringSince()));

        // Only an ONLINE system has a meaningful "current" application.
        if ("ONLINE".equals(system.getStatus())) {
            ActivityLog open = activityLogDao.findOpenSession(system.getId());
            if (open != null) {
                dto.setCurrentApplication(open.getApplicationName());
                dto.setActivityStatus(open.getStatus());
                dto.setSessionStartTime(TimeUtil.iso(open.getStartTime()));
            }
        }

        LocalDate today = LocalDate.now(TimeUtil.ZONE);
        Instant start = TimeUtil.dayStart(today);
        Instant end = TimeUtil.dayEnd(today);
        List<ActivityLog> sessions = activityLogDao.findOverlapping(
                system.getId(), TimeUtil.ts(start), TimeUtil.ts(end));
        dto.setActivityScore(ActivityAnalytics.compute(sessions, start, end, Instant.now(), TimeUtil.ZONE)
                .activityScore());

        return dto;
    }
}
