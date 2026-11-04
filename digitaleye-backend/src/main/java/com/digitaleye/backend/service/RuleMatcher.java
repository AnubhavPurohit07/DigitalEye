package com.digitaleye.backend.service;

import com.digitaleye.database.model.AlertRule;

/** The small yes/no questions the alert engine asks about a rule. Pure logic, easy to test. */
public final class RuleMatcher {

    private RuleMatcher() {
    }

    /** UNAUTHORIZED_APP: does the window title contain the rule's keyword (case-insensitive)? */
    public static boolean appMatches(AlertRule rule, String applicationName) {
        if (rule.getKeyword() == null || rule.getKeyword().isBlank() || applicationName == null) {
            return false;
        }
        return applicationName.toLowerCase().contains(rule.getKeyword().trim().toLowerCase());
    }

    /** OFF_HOURS: the rule's hours are the ALLOWED window [start, end), so anything outside it matches. */
    public static boolean isOutsideWorkHours(AlertRule rule, int hourOfDay) {
        if (rule.getStartHour() == null || rule.getEndHour() == null) {
            return false;
        }
        return hourOfDay < rule.getStartHour() || hourOfDay >= rule.getEndHour();
    }

    /** IDLE_THRESHOLD: has the current idle stretch lasted at least the rule's minutes? */
    public static boolean idleExceeded(AlertRule rule, long idleMillis) {
        if (rule.getThresholdMinutes() == null) {
            return false;
        }
        return idleMillis >= rule.getThresholdMinutes() * 60_000L;
    }
}
