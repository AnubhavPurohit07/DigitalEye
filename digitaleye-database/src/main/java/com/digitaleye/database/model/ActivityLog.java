package com.digitaleye.database.model;

import java.sql.Timestamp;

public class ActivityLog {

    private Long id;
    private Long systemId;
    private String applicationName;
    private String status;   // "ACTIVE" or "IDLE"
    private String category; // "PRODUCTIVE", "ENTERTAINMENT", "NON_PRODUCTIVE", "IDLE", "OTHER"
    private Timestamp startTime;
    private Timestamp endTime;

    public ActivityLog() {
    }

    public ActivityLog(Long systemId, String applicationName, String status, String category, Timestamp startTime) {
        this.systemId = systemId;
        this.applicationName = applicationName;
        this.status = status;
        this.category = category;
        this.startTime = startTime;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Timestamp getStartTime() {
        return startTime;
    }

    public void setStartTime(Timestamp startTime) {
        this.startTime = startTime;
    }

    public Timestamp getEndTime() {
        return endTime;
    }

    public void setEndTime(Timestamp endTime) {
        this.endTime = endTime;
    }
}
