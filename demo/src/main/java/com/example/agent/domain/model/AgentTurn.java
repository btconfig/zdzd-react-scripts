package com.example.agent.domain.model;

import lombok.Data;

import java.time.Instant;

@Data
public class AgentTurn {
    private String turnId;
    private String userMessage;
    private AgentTurnStatus status = AgentTurnStatus.RUNNING;
    private Instant createdAt = Instant.now();
}
