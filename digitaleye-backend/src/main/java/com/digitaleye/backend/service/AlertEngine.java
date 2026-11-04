package com.digitaleye.backend.service;

import com.digitaleye.database.dao.ActivityLogDao;
import com.digitaleye.database.dao.AlertDao;
import com.digitaleye.database.dao.AlertRuleDao;
import com.digitaleye.database.dao.MonitoredSystemDao;
import com.digitaleye.database.model.ActivityLog;
import com.digitaleye.database.model.Alert;
import com.digitaleye.database.model.AlertRule;
import com.digitaleye.database.model.MonitoredSystem;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides when an alert should be raised.
 *
 *  - onNewSession(): called by the agent endpoint the moment a new activity session starts.
 *    Checks the "unauthorized application" and "outside work hours" rules.
 *  - checkIdleSessions(): called on a timer. Idle has no "event" to react to (nothing happens
 *    while the user is idle), so the timer looks at every still-open idle session and raises
 *    an alert once it has lasted longer than a rule's threshold.
 *  - checkOfflineSystems(): called on a timer. An agent that stops sending heartbeats gets its
 *    system marked OFFLINE, its open session closed, and an "agent went offline" alert raised.
 *
 * To avoid flooding the admin: app alerts have a 10-minute cooldown per rule+system, the
 * off-hours rule fires at most once a day per system, and an idle alert fires once per idle session.
 */
@Service
public class AlertEngine {

    private static final int APP_ALERT_COOLDOWN_MINUTES = 10;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final AlertRuleDao ruleDao = new AlertRuleDao();
    private final AlertDao alertDao = new AlertDao();
    private final MonitoredSystemDao systemDao = new MonitoredSystemDao();
    private final ActivityLogDao activityLogDao = new ActivityLogDao();

    public void onNewSession(MonitoredSystem system, ActivityLog session) throws SQLException {
        if (session.getStatus() == null || "IDLE".equalsIgnoreCase(session.getStatus())) {
            return;
        }

        Instant now = Instant.now();
        for (AlertRule rule : ruleDao.findEnabled()) {
            if (AlertRule.UNAUTHORIZED_APP.equals(rule.getRuleType())) {
                if (RuleMatcher.appMatches(rule, session.getApplicationName())) {
                    String message = rule.getName() + ": " + TimeUtil.abbreviate(session.getApplicationName(), 120);
                    Instant since = now.minus(APP_ALERT_COOLDOWN_MINUTES, ChronoUnit.MINUTES);
                    raiseWithCooldown(rule, system, session, since, message);
                }
            } else if (AlertRule.OFF_HOURS.equals(rule.getRuleType())) {
                int hour = ZonedDateTime.ofInstant(now, TimeUtil.ZONE).getHour();
                if (RuleMatcher.isOutsideWorkHours(rule, hour)) {
                    String message = rule.getName() + " (allowed " + pad(rule.getStartHour()) + ":00-"
                            + pad(rule.getEndHour()) + ":00)";
                    Instant startOfToday = TimeUtil.dayStart(java.time.LocalDate.now(TimeUtil.ZONE));
                    raiseWithCooldown(rule, system, session, startOfToday, message);
                }
            }
        }
    }

    public void checkIdleSessions() throws SQLException {
        List<AlertRule> idleRules = new ArrayList<>();
        for (AlertRule rule : ruleDao.findEnabled()) {
            if (AlertRule.IDLE_THRESHOLD.equals(rule.getRuleType()) && rule.getThresholdMinutes() != null) {
                idleRules.add(rule);
            }
        }
        if (idleRules.isEmpty()) {
            return;
        }

        long nowMs = System.currentTimeMillis();
        for (ActivityLog open : activityLogDao.findOpenSessions()) {
            if (!"IDLE".equalsIgnoreCase(open.getStatus()) || open.getStartTime() == null) {
                continue;
            }
            long idleMs = nowMs - open.getStartTime().getTime();

            for (AlertRule rule : idleRules) {
                if (!RuleMatcher.idleExceeded(rule, idleMs)) {
                    continue;
                }
                if (alertDao.existsForRuleAndLog(rule.getId(), open.getId())) {
                    continue;
                }
                String message = rule.getName() + " (" + rule.getThresholdMinutes() + " min)";
                alertDao.insert(new Alert(open.getSystemId(), rule.getId(), open.getId(),
                        rule.getRuleType(), rule.getSeverity(), message, null));
            }
        }
    }

    /** Marks systems whose agent has gone quiet as OFFLINE. Returns how many were flipped. */
    public int checkOfflineSystems(int offlineAfterSeconds) throws SQLException {
        Timestamp cutoff = Timestamp.from(Instant.now().minusSeconds(offlineAfterSeconds));
        List<MonitoredSystem> stale = systemDao.findStaleOnline(cutoff);

        for (MonitoredSystem system : stale) {
            markOffline(system, system.getLastHeartbeat() == null ? null : system.getLastHeartbeat().toInstant());

            String lastSeen = system.getLastHeartbeat() == null
                    ? "no heartbeat received"
                    : "last seen " + CLOCK.format(system.getLastHeartbeat().toInstant().atZone(TimeUtil.ZONE));
            alertDao.insert(new Alert(system.getId(), null, null, "AGENT_OFFLINE", "MEDIUM",
                    "Agent went offline (" + lastSeen + ")", null));
        }
        return stale.size();
    }

    /**
     * Sets the system OFFLINE and closes whatever session was still open, ending it at
     * endAt (or now). Also used by the agent's "goodbye" call when it shuts down cleanly.
     */
    public void markOffline(MonitoredSystem system, Instant endAt) throws SQLException {
        systemDao.setStatusOnly(system.getId(), "OFFLINE");

        ActivityLog open = activityLogDao.findOpenSession(system.getId());
        if (open != null) {
            Instant end = endAt == null ? Instant.now() : endAt;
            if (open.getStartTime() != null && end.isBefore(open.getStartTime().toInstant())) {
                end = open.getStartTime().toInstant();
            }
            activityLogDao.closeSession(open.getId(), Timestamp.from(end));
        }
    }

    private void raiseWithCooldown(AlertRule rule, MonitoredSystem system, ActivityLog session,
                                   Instant since, String message) throws SQLException {
        if (alertDao.existsForRuleSystemSince(rule.getId(), system.getId(), Timestamp.from(since))) {
            return;
        }
        alertDao.insert(new Alert(system.getId(), rule.getId(), session.getId(), rule.getRuleType(),
                rule.getSeverity(), TimeUtil.abbreviate(message, 255),
                TimeUtil.abbreviate(session.getApplicationName(), 255)));
    }

    private String pad(Integer hour) {
        return hour == null ? "--" : String.format("%02d", hour);
    }
}
