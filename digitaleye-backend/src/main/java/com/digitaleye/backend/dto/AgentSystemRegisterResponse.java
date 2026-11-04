package com.digitaleye.backend.dto;

public class AgentSystemRegisterResponse {

    private Long systemId;
    private String status;

    public AgentSystemRegisterResponse(Long systemId, String status) {
        this.systemId = systemId;
        this.status = status;
    }

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
