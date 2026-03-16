package com.example.agent.application.service;

import com.example.agent.domain.model.RuntimeEvent;
import com.example.agent.domain.model.RuntimeEventType;
import com.example.agent.infrastructure.repository.AgentEventRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class RuntimeEventBus {
    private final AgentEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>>();

    public RuntimeEventBus(AgentEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public RuntimeEvent publish(String sessionId, String turnId, RuntimeEventType eventType, Map<String, Object> payload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            long id = repository.insert(sessionId, turnId, eventType.name(), payloadJson);
            RuntimeEvent event = new RuntimeEvent();
            event.setId(id);
            event.setSessionId(sessionId);
            event.setTurnId(turnId);
            event.setEventType(eventType);
            event.setPayload(payload);
            broadcast(sessionId, event);
            return event;
        } catch (Exception e) {
            throw new RuntimeException("failed to publish runtime event", e);
        }
    }

    public List<RuntimeEvent> history(String sessionId) {
        List<Map<String, Object>> rows = repository.findBySessionId(sessionId);
        List<RuntimeEvent> events = new ArrayList<RuntimeEvent>();
        for (Map<String, Object> row : rows) {
            RuntimeEvent event = new RuntimeEvent();
            event.setId(((Number) row.get("id")).longValue());
            event.setSessionId((String) row.get("session_id"));
            event.setTurnId((String) row.get("turn_id"));
            event.setEventType(RuntimeEventType.valueOf(String.valueOf(row.get("event_type"))));
            event.setCreatedAt(toInstant(row.get("created_at")));
            try {
                event.setPayload(objectMapper.readValue(String.valueOf(row.get("payload_json")), new TypeReference<Map<String, Object>>() {}));
            } catch (Exception e) {
                throw new RuntimeException("failed to parse event payload", e);
            }
            events.add(event);
        }
        return events;
    }

    public SseEmitter subscribe(String sessionId) {
        SseEmitter emitter = new SseEmitter(0L);
        subscribers.computeIfAbsent(sessionId, ignored -> new CopyOnWriteArrayList<SseEmitter>()).add(emitter);
        emitter.onCompletion(() -> remove(sessionId, emitter));
        emitter.onTimeout(() -> remove(sessionId, emitter));
        return emitter;
    }

    private void broadcast(String sessionId, RuntimeEvent event) {
        List<SseEmitter> emitters = subscribers.get(sessionId);
        if (emitters == null) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(event.getEventType().name())
                        .id(String.valueOf(event.getId()))
                        .data(event));
            } catch (IOException e) {
                remove(sessionId, emitter);
            }
        }
    }

    private void remove(String sessionId, SseEmitter emitter) {
        List<SseEmitter> emitters = subscribers.get(sessionId);
        if (emitters != null) {
            emitters.remove(emitter);
        }
    }

    private Instant toInstant(Object value) {
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toInstant();
        }
        return Instant.now();
    }
}
