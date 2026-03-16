package com.example.agent.application.service;

import com.example.agent.domain.model.RuntimeEventType;
import com.example.agent.infrastructure.repository.AsyncJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class InternalAsyncCallbackService {
    private final AsyncJobRepository asyncJobRepository;
    private final AgentTurnRunner agentTurnRunner;
    private final RuntimeEventBus runtimeEventBus;
    private final ObjectMapper objectMapper;

    public InternalAsyncCallbackService(AsyncJobRepository asyncJobRepository,
                                        @Lazy AgentTurnRunner agentTurnRunner,
                                        RuntimeEventBus runtimeEventBus,
                                        ObjectMapper objectMapper) {
        this.asyncJobRepository = asyncJobRepository;
        this.agentTurnRunner = agentTurnRunner;
        this.runtimeEventBus = runtimeEventBus;
        this.objectMapper = objectMapper;
    }

    public void dispatch(String externalJobId, String status, Map<String, Object> payload) {
        Map<String, Object> job = findAsyncJobWithRetry(externalJobId);
        if (job == null) {
            return;
        }
        String payloadJson = writeJson(payload);
        asyncJobRepository.updateStatusByExternalJobId(externalJobId, status, payloadJson);
        Object sessionIdValue = job.get("session_id");
        if (sessionIdValue == null) {
            return;
        }
        String sessionId = String.valueOf(sessionIdValue);
        String turnId = stringValue(job.get("turn_id"));
        String toolCallId = stringValue(job.get("tool_call_id"));
        runtimeEventBus.publish(sessionId, turnId, RuntimeEventType.TOOL_CALLBACK_RECEIVED, callbackPayload(externalJobId, status, payload));
        agentTurnRunner.appendToolObservation(sessionId, toolCallId, payload);
        runtimeEventBus.publish(sessionId, turnId, RuntimeEventType.TOOL_EXECUTION_FINISHED, payload);
        if (!sessionId.startsWith("dsl-session-")) {
            agentTurnRunner.resumeSession(sessionId);
        }
    }

    private Map<String, Object> callbackPayload(String externalJobId, String status, Map<String, Object> payload) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("externalJobId", externalJobId);
        result.put("status", status);
        result.put("payload", payload);
        return result;
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("failed to serialize async callback payload", e);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Map<String, Object> findAsyncJobWithRetry(String externalJobId) {
        for (int i = 0; i < 20; i++) {
            Map<String, Object> job = asyncJobRepository.findByExternalJobId(externalJobId);
            if (job != null) {
                return job;
            }
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }
}
