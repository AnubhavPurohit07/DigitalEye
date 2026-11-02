package com.digitaleye.backend.dto;

public class AgentActivityRequest {

    private Long systemId;
    private String applicationName;
    private String status; // "ACTIVE" or "IDLE"

    public AgentActivityRequest() {
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
}
