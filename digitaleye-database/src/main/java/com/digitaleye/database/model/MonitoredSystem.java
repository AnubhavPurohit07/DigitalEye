package com.digitaleye.database.model;

import java.sql.Timestamp;

public class MonitoredSystem {

    private Long id;
    private Long userId;
    private String systemName;
    private String status; // "ONLINE" or "OFFLINE"
    private Timestamp lastHeartbeat;
    private Timestamp monitoringSince;

    public MonitoredSystem() {
    }

    public MonitoredSystem(Long userId, String systemName, String status) {
        this.userId = userId;
        this.systemName = systemName;
        this.status = status;
    }

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

    public Timestamp getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(Timestamp lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public Timestamp getMonitoringSince() {
        return monitoringSince;
    }

    public void setMonitoringSince(Timestamp monitoringSince) {
        this.monitoringSince = monitoringSince;
    }
}
