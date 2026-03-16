package com.example.agent.interfaces.rest;

import com.example.agent.application.service.AgentSessionStore;
import com.example.agent.application.service.AgentTurnRunner;
import com.example.agent.application.service.DslRuntimeService;
import com.example.agent.application.service.RuntimeEventBus;
import com.example.agent.domain.model.AgentSession;
import com.example.agent.domain.model.ExecutableDslSnapshot;
import com.example.agent.domain.model.RuntimeEvent;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent")
public class AgentSessionController {
    private final AgentTurnRunner agentTurnRunner;
    private final AgentSessionStore agentSessionStore;
    private final RuntimeEventBus runtimeEventBus;
    private final DslRuntimeService dslRuntimeService;

    public AgentSessionController(AgentTurnRunner agentTurnRunner,
                                  AgentSessionStore agentSessionStore,
                                  RuntimeEventBus runtimeEventBus,
                                  DslRuntimeService dslRuntimeService) {
        this.agentTurnRunner = agentTurnRunner;
        this.agentSessionStore = agentSessionStore;
        this.runtimeEventBus = runtimeEventBus;
        this.dslRuntimeService = dslRuntimeService;
    }

    @PostMapping("/sessions")
    public ResponseEntity<AgentSession> createSession(@RequestBody SessionRequest request) {
        AgentSession session = agentTurnRunner.createSessionRecord(request.getMessage(), request.getGlobalContext());
        agentTurnRunner.startSessionAsync(session.getSessionId(), request.getMessage());
        return ResponseEntity.ok(session);
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<AgentSession> continueSession(@PathVariable String sessionId, @RequestBody MessageRequest request) {
        return ResponseEntity.ok(agentTurnRunner.continueSession(sessionId, request.getMessage()));
    }

    @PostMapping("/sessions/{sessionId}/resume")
    public ResponseEntity<AgentSession> resume(@PathVariable String sessionId) {
        return ResponseEntity.ok(agentTurnRunner.resumeSession(sessionId));
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<AgentSession> getSession(@PathVariable String sessionId) {
        AgentSession session = agentSessionStore.load(sessionId);
        return session == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(session);
    }

    @GetMapping(value = "/sessions/{sessionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String sessionId) {
        SseEmitter emitter = runtimeEventBus.subscribe(sessionId);
        List<RuntimeEvent> history = runtimeEventBus.history(sessionId);
        for (RuntimeEvent event : history) {
            try {
                emitter.send(SseEmitter.event().name(event.getEventType().name()).id(String.valueOf(event.getId())).data(event));
            } catch (Exception ignore) {
                break;
            }
        }
        return emitter;
    }

    @GetMapping("/dsl/{dslId}")
    public ResponseEntity<ExecutableDslSnapshot> getDsl(@PathVariable String dslId) {
        ExecutableDslSnapshot snapshot = dslRuntimeService.get(dslId);
        return snapshot == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(snapshot);
    }

    @PostMapping("/dsl/{dslId}/execute")
    public ResponseEntity<Map<String, Object>> executeDsl(@PathVariable String dslId, @RequestBody(required = false) Map<String, Object> overrides) {
        return ResponseEntity.ok(dslRuntimeService.execute(dslId, overrides == null ? Collections.<String, Object>emptyMap() : overrides));
    }

    public static class SessionRequest {
        private String message;
        private Map<String, Object> globalContext;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Map<String, Object> getGlobalContext() {
            return globalContext;
        }

        public void setGlobalContext(Map<String, Object> globalContext) {
            this.globalContext = globalContext;
        }
    }

    public static class MessageRequest {
        private String message;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
