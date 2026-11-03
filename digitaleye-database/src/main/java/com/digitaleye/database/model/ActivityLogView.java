package com.digitaleye.database.model;

import java.sql.Timestamp;

/** An activity_logs row joined with its system's name - what the Activity Logs page lists. */
public class ActivityLogView {

    private Long id;
    private Long systemId;
    private String systemName;
    private String applicationName;
    private String status;
    private String category;
    private Timestamp startTime;
    private Timestamp endTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSystemId() { return systemId; }
    public void setSystemId(Long systemId) { this.systemId = systemId; }

    public String getSystemName() { return systemName; }
    public void setSystemName(String systemName) { this.systemName = systemName; }

    public String getApplicationName() { return applicationName; }
    public void setApplicationName(String applicationName) { this.applicationName = applicationName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Timestamp getStartTime() { return startTime; }
    public void setStartTime(Timestamp startTime) { this.startTime = startTime; }

    public Timestamp getEndTime() { return endTime; }
    public void setEndTime(Timestamp endTime) { this.endTime = endTime; }
}
