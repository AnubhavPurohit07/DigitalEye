package com.digitaleye.backend.dto;

/**
 * What the dashboard's "Client Systems" table actually needs: the system's own
 * fields (from the systems table) PLUS whatever its current open activity_logs
 * session says it's doing right now (if anything) PLUS today's Activity Score.
 * One combined response means the dashboard needs no extra round-trip per system.
 */
public class SystemSummaryResponse {

    private Long id;
    private Long userId;
    private String systemName;
    private String status; // ONLINE / OFFLINE
    private String lastHeartbeat;    // ISO-8601 string, or null
    private String monitoringSince;  // ISO-8601 string, or null

    // Null when the system is OFFLINE or hasn't reported anything yet.
    private String currentApplication;
    private String activityStatus; // ACTIVE / IDLE
    private String sessionStartTime; // ISO-8601 string - used to compute "running time" client-side

    // Today's Digital Activity Score (0-100); null until about a minute of activity has been tracked.
    private Integer activityScore;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(String lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public String getMonitoringSince() {
        return monitoringSince;
    }

    public void setMonitoringSince(String monitoringSince) {
        this.monitoringSince = monitoringSince;
    }

    public String getCurrentApplication() {
        return currentApplication;
    }

    public void setCurrentApplication(String currentApplication) {
        this.currentApplication = currentApplication;
    }

    public String getActivityStatus() {
        return activityStatus;
    }

    public void setActivityStatus(String activityStatus) {
        this.activityStatus = activityStatus;
    }

    public String getSessionStartTime() {
        return sessionStartTime;
    }

    public void setSessionStartTime(String sessionStartTime) {
        this.sessionStartTime = sessionStartTime;
    }

    public Integer getActivityScore() {
        return activityScore;
    }

    public void setActivityScore(Integer activityScore) {
        this.activityScore = activityScore;
    }
}
