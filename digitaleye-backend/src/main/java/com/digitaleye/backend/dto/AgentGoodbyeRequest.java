package com.digitaleye.backend.dto;

/** Sent by the agent when it shuts down cleanly, so the dashboard shows OFFLINE straight away. */
public record AgentGoodbyeRequest(Long systemId) {
}
