package com.example.agent.application.service;

import com.example.agent.infrastructure.repository.AgentEventRepository;
import com.example.agent.infrastructure.repository.AgentSessionRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class PlanRuntimeService {
    private final AgentSessionRepository agentSessionRepository;
    private final AgentEventRepository agentEventRepository;

    public PlanRuntimeService(AgentSessionRepository agentSessionRepository,
                              AgentEventRepository agentEventRepository) {
        this.agentSessionRepository = agentSessionRepository;
        this.agentEventRepository = agentEventRepository;
    }

    public Map<String, Object> agentRuntimeSnapshot(String sessionId) {
        Map<String, Object> snapshot = new HashMap<String, Object>();
        snapshot.put("session", agentSessionRepository.findById(sessionId));
        snapshot.put("events", agentEventRepository.findBySessionId(sessionId));
        return snapshot;
    }
}
