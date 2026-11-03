package com.digitaleye.backend.dto;

/** What the agent is told when it asks "anything for me?" - currently just "take a screenshot now". */
public record AgentCommands(boolean screenshot) {
}
