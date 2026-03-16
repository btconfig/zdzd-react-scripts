package com.example.agent.application.service;

import com.example.agent.domain.model.AgentSession;
import com.example.agent.domain.model.AgentSessionStatus;
import com.example.agent.domain.model.AgentExecutedStep;
import com.example.agent.domain.model.chat.ChatMessage;
import com.example.agent.infrastructure.repository.AgentSessionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class AgentSessionStore {
    private final AgentSessionRepository repository;
    private final ObjectMapper objectMapper;

    public AgentSessionStore(AgentSessionRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void save(AgentSession session) {
        try {
            repository.upsert(
                    session.getSessionId(),
                    session.getStatus().name(),
                    session.getTitle(),
                    session.getQuery(),
                    objectMapper.writeValueAsString(session.getTranscript()),
                    objectMapper.writeValueAsString(session.getGlobalContext()),
                    objectMapper.writeValueAsString(session.getActiveSkillIds()),
                    objectMapper.writeValueAsString(session.getExecutedSteps()),
                    session.getLatestAnswer(),
                    session.getLatestDslId(),
                    session.getWaitingExternalJobId(),
                    session.getTurnCount(),
                    session.getLastError()
            );
        } catch (Exception e) {
            throw new RuntimeException("failed to persist agent session", e);
        }
    }

    public AgentSession load(String sessionId) {
        Map<String, Object> row = repository.findById(sessionId);
        if (row == null) {
            return null;
        }
        try {
            AgentSession session = new AgentSession();
            session.setSessionId(sessionId);
            session.setStatus(AgentSessionStatus.valueOf(String.valueOf(row.get("status"))));
            session.setTitle((String) row.get("title"));
            session.setQuery((String) row.get("query_text"));
            session.setTranscript(readList(String.valueOf(row.get("transcript_json")), new TypeReference<List<ChatMessage>>() {}));
            session.setGlobalContext(readMap((String) row.get("global_context_json")));
            session.setActiveSkillIds(readList(String.valueOf(row.get("active_skill_ids_json")), new TypeReference<List<String>>() {}));
            session.setExecutedSteps(readList(String.valueOf(row.get("executed_steps_json")), new TypeReference<List<AgentExecutedStep>>() {}));
            session.setLatestAnswer((String) row.get("latest_answer"));
            session.setLatestDslId((String) row.get("latest_dsl_id"));
            session.setWaitingExternalJobId((String) row.get("waiting_external_job_id"));
            session.setTurnCount(row.get("turn_count") == null ? 0 : ((Number) row.get("turn_count")).intValue());
            session.setLastError((String) row.get("last_error"));
            session.setCreatedAt(toInstant(row.get("created_at")));
            session.setUpdatedAt(toInstant(row.get("updated_at")));
            return session;
        } catch (Exception e) {
            throw new RuntimeException("failed to load agent session", e);
        }
    }

    private Map<String, Object> readMap(String json) throws Exception {
        if (json == null || json.trim().isEmpty() || "null".equals(json)) {
            return Collections.emptyMap();
        }
        return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    private <T> T readList(String json, TypeReference<T> typeReference) throws Exception {
        if (json == null || json.trim().isEmpty() || "null".equals(json)) {
            if (typeReference.getType().getTypeName().contains("List")) {
                return objectMapper.convertValue(Collections.emptyList(), typeReference);
            }
            return null;
        }
        return objectMapper.readValue(json, typeReference);
    }

    private Instant toInstant(Object value) {
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toInstant();
        }
        return Instant.now();
    }
}
