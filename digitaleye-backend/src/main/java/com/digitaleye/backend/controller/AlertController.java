package com.digitaleye.backend.controller;

import com.digitaleye.backend.dto.AlertDtos.AlertPage;
import com.digitaleye.backend.dto.AlertDtos.AlertResponse;
import com.digitaleye.backend.dto.AlertDtos.AlertRuleDto;
import com.digitaleye.backend.dto.AlertDtos.AlertSummary;
import com.digitaleye.backend.service.TimeUtil;
import com.digitaleye.database.dao.AlertDao;
import com.digitaleye.database.dao.AlertRuleDao;
import com.digitaleye.database.model.Alert;
import com.digitaleye.database.model.AlertRule;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Triggered alerts (list, acknowledge) and the admin-editable alert rules (create, edit, delete). */
@RestController
@RequestMapping("/api/admin")
public class AlertController {

    private static final Set<String> RULE_TYPES = Set.of(
            AlertRule.IDLE_THRESHOLD, AlertRule.UNAUTHORIZED_APP, AlertRule.OFF_HOURS);
    private static final Set<String> SEVERITIES = Set.of("HIGH", "MEDIUM", "LOW");
    private static final int MAX_PAGE_SIZE = 100;

    private final AlertDao alertDao = new AlertDao();
    private final AlertRuleDao ruleDao = new AlertRuleDao();

    // ---------------------------------------------------------------- alerts

    @GetMapping("/alerts")
    public AlertPage listAlerts(
            @RequestParam(name = "systemId", required = false) Long systemId,
            @RequestParam(name = "severity", required = false) String severity,
            @RequestParam(name = "acknowledged", required = false) Boolean acknowledged,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        try {
            int safePage = Math.max(0, page);
            int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));

            long total = alertDao.count(systemId, severity, acknowledged);
            List<AlertResponse> items = new ArrayList<>();
            for (Alert a : alertDao.search(systemId, severity, acknowledged, safeSize, safePage * safeSize)) {
                items.add(new AlertResponse(a.getId(), a.getSystemId(), a.getSystemName(), a.getRuleId(),
                        a.getAlertType(), a.getSeverity(), a.getMessage(), a.getApplicationName(),
                        a.isAcknowledged(), TimeUtil.iso(a.getCreatedAt())));
            }
            return new AlertPage(items, total, safePage, safeSize);
        } catch (SQLException e) {
            throw new RuntimeException("Database error while listing alerts", e);
        }
    }

    /** Small numbers for the topbar bell and the dashboard's alerts card. */
    @GetMapping("/alerts/summary")
    public AlertSummary alertSummary() {
        try {
            long startOfToday = TimeUtil.dayStart(LocalDate.now(TimeUtil.ZONE)).toEpochMilli();
            return new AlertSummary(
                    alertDao.count(null, null, false),
                    alertDao.count(null, "HIGH", false),
                    alertDao.countSince(new java.sql.Timestamp(startOfToday)));
        } catch (SQLException e) {
            throw new RuntimeException("Database error while counting alerts", e);
        }
    }

    @PostMapping("/alerts/{id}/acknowledge")
    public Map<String, Boolean> acknowledge(@PathVariable("id") Long id) {
        try {
            if (!alertDao.acknowledge(id)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such alert");
            }
            return Map.of("acknowledged", true);
        } catch (SQLException e) {
            throw new RuntimeException("Database error while acknowledging alert", e);
        }
    }

    @PostMapping("/alerts/acknowledge-all")
    public Map<String, Integer> acknowledgeAll() {
        try {
            return Map.of("acknowledged", alertDao.acknowledgeAll());
        } catch (SQLException e) {
            throw new RuntimeException("Database error while acknowledging alerts", e);
        }
    }

    // ----------------------------------------------------------------- rules

    @GetMapping("/alert-rules")
    public List<AlertRuleDto> listRules() {
        try {
            List<AlertRuleDto> result = new ArrayList<>();
            for (AlertRule rule : ruleDao.findAll()) {
                result.add(toDto(rule));
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Database error while listing alert rules", e);
        }
    }

    @PostMapping("/alert-rules")
    public ResponseEntity<?> createRule(@RequestBody AlertRuleDto body) {
        try {
            String problem = validate(body);
            if (problem != null) {
                return invalid(problem);
            }
            AlertRule rule = toRule(body);
            rule.setId(ruleDao.insert(rule));
            return ResponseEntity.ok(toDto(rule));
        } catch (SQLException e) {
            throw new RuntimeException("Database error while creating alert rule", e);
        }
    }

    @PutMapping("/alert-rules/{id}")
    public ResponseEntity<?> updateRule(@PathVariable("id") Long id, @RequestBody AlertRuleDto body) {
        try {
            String problem = validate(body);
            if (problem != null) {
                return invalid(problem);
            }
            AlertRule rule = toRule(body);
            rule.setId(id);
            if (!ruleDao.update(rule)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such rule");
            }
            return ResponseEntity.ok(toDto(rule));
        } catch (SQLException e) {
            throw new RuntimeException("Database error while updating alert rule", e);
        }
    }

    @DeleteMapping("/alert-rules/{id}")
    public Map<String, Boolean> deleteRule(@PathVariable("id") Long id) {
        try {
            if (!ruleDao.delete(id)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such rule");
            }
            return Map.of("deleted", true);
        } catch (SQLException e) {
            throw new RuntimeException("Database error while deleting alert rule", e);
        }
    }

    // --------------------------------------------------------------- helpers

    /** A 400 whose JSON body carries the reason, so the dashboard can show it next to the form. */
    private ResponseEntity<Map<String, String>> invalid(String problem) {
        return ResponseEntity.badRequest().body(Map.of("message", problem));
    }

    private String validate(AlertRuleDto d) {
        if (d == null) {
            return "Missing rule";
        }
        if (d.name() == null || d.name().isBlank()) {
            return "Rule name is required";
        }
        if (d.name().length() > 100) {
            return "Rule name must be 100 characters or fewer";
        }
        if (d.ruleType() == null || !RULE_TYPES.contains(d.ruleType())) {
            return "Rule type must be IDLE_THRESHOLD, UNAUTHORIZED_APP or OFF_HOURS";
        }
        if (d.severity() == null || !SEVERITIES.contains(d.severity())) {
            return "Severity must be HIGH, MEDIUM or LOW";
        }

        if (AlertRule.IDLE_THRESHOLD.equals(d.ruleType())) {
            if (d.thresholdMinutes() == null || d.thresholdMinutes() < 1 || d.thresholdMinutes() > 1440) {
                return "Idle threshold must be between 1 and 1440 minutes";
            }
        } else if (AlertRule.UNAUTHORIZED_APP.equals(d.ruleType())) {
            if (d.keyword() == null || d.keyword().isBlank()) {
                return "A keyword to look for in the application name is required";
            }
            if (d.keyword().length() > 100) {
                return "Keyword must be 100 characters or fewer";
            }
        } else if (AlertRule.OFF_HOURS.equals(d.ruleType())) {
            if (d.startHour() == null || d.endHour() == null
                    || d.startHour() < 0 || d.startHour() > 23 || d.endHour() < 1 || d.endHour() > 24) {
                return "Start hour must be 0-23 and end hour 1-24";
            }
            if (d.startHour() >= d.endHour()) {
                return "Start hour must be before end hour";
            }
        }
        return null;
    }

    /** Keeps only the fields that matter for the rule's type, so unused columns stay NULL. */
    private AlertRule toRule(AlertRuleDto d) {
        AlertRule rule = new AlertRule();
        rule.setName(d.name().trim());
        rule.setRuleType(d.ruleType());
        rule.setSeverity(d.severity());
        rule.setEnabled(d.enabled() == null || d.enabled());

        if (AlertRule.IDLE_THRESHOLD.equals(d.ruleType())) {
            rule.setThresholdMinutes(d.thresholdMinutes());
        } else if (AlertRule.UNAUTHORIZED_APP.equals(d.ruleType())) {
            rule.setKeyword(d.keyword().trim());
        } else if (AlertRule.OFF_HOURS.equals(d.ruleType())) {
            rule.setStartHour(d.startHour());
            rule.setEndHour(d.endHour());
        }
        return rule;
    }

    private AlertRuleDto toDto(AlertRule rule) {
        return new AlertRuleDto(rule.getId(), rule.getName(), rule.getRuleType(), rule.getThresholdMinutes(),
                rule.getKeyword(), rule.getStartHour(), rule.getEndHour(), rule.getSeverity(), rule.isEnabled());
    }
}
