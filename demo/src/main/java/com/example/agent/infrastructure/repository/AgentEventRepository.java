package com.example.agent.infrastructure.repository;

import com.example.agent.infrastructure.mapper.AgentEventMapper;
import com.example.agent.infrastructure.mapper.model.AgentEventRow;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class AgentEventRepository {
    private final AgentEventMapper mapper;

    public AgentEventRepository(AgentEventMapper mapper) {
        this.mapper = mapper;
    }

    public long insert(String sessionId, String turnId, String eventType, String payloadJson) {
        AgentEventRow row = new AgentEventRow();
        row.setSessionId(sessionId);
        row.setTurnId(turnId);
        row.setEventType(eventType);
        row.setPayloadJson(payloadJson);
        mapper.insert(row);
        return row.getId() == null ? -1L : row.getId();
    }

    public List<Map<String, Object>> findBySessionId(String sessionId) {
        return mapper.findBySessionId(sessionId);
    }
}
