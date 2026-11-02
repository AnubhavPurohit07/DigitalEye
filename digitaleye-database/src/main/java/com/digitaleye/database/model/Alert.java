package com.digitaleye.database.model;

import java.sql.Timestamp;

/** One triggered alert. systemName is filled in by queries that join the systems table. */
public class Alert {

    private Long id;
    private Long systemId;
    private String systemName;
    private Long ruleId;
    private Long activityLogId;
    private String alertType;
    private String severity;
    private String message;
    private String applicationName;
    private boolean acknowledged;
    private Timestamp createdAt;

    public Alert() {
    }

    public Alert(Long systemId, Long ruleId, Long activityLogId, String alertType,
                 String severity, String message, String applicationName) {
        this.systemId = systemId;
        this.ruleId = ruleId;
        this.activityLogId = activityLogId;
        this.alertType = alertType;
        this.severity = severity;
        this.message = message;
        this.applicationName = applicationName;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSystemId() { return systemId; }
    public void setSystemId(Long systemId) { this.systemId = systemId; }

    public String getSystemName() { return systemName; }
    public void setSystemName(String systemName) { this.systemName = systemName; }

    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }

    public Long getActivityLogId() { return activityLogId; }
    public void setActivityLogId(Long activityLogId) { this.activityLogId = activityLogId; }

    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getApplicationName() { return applicationName; }
    public void setApplicationName(String applicationName) { this.applicationName = applicationName; }

    public boolean isAcknowledged() { return acknowledged; }
    public void setAcknowledged(boolean acknowledged) { this.acknowledged = acknowledged; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
