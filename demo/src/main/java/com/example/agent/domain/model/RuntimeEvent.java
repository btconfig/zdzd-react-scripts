package com.example.agent.domain.model;

import lombok.Data;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Data
public class RuntimeEvent {
    private Long id;
    private String sessionId;
    private String turnId;
    private RuntimeEventType eventType;
    private Map<String, Object> payload = new HashMap<String, Object>();
    private Instant createdAt = Instant.now();
}
