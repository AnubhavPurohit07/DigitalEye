package com.digitaleye.backend.dto;

import java.util.List;

/** The small response shapes the alerts endpoints use, grouped in one file. */
public final class AlertDtos {

    private AlertDtos() {
    }

    public record AlertResponse(Long id, Long systemId, String systemName, Long ruleId, String type,
                                String severity, String message, String application,
                                boolean acknowledged, String createdAt) {
    }

    /** One page of alerts. page is 0-based. */
    public record AlertPage(List<AlertResponse> items, long total, int page, int size) {
    }

    /** Numbers for the topbar bell and the dashboard's Activity Alerts card. */
    public record AlertSummary(long unacknowledged, long high, long today) {
    }

    /** Used both as the request body when creating/editing a rule and as the response. */
    public record AlertRuleDto(Long id, String name, String ruleType, Integer thresholdMinutes,
                               String keyword, Integer startHour, Integer endHour,
                               String severity, Boolean enabled) {
    }
}
