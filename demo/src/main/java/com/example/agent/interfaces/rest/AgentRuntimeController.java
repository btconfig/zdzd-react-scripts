package com.example.agent.interfaces.rest;

import com.example.agent.application.service.InternalAsyncCallbackService;
import com.example.agent.application.service.PlanRuntimeService;
import com.example.agent.infrastructure.repository.AsyncJobRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AgentRuntimeController {

    private final PlanRuntimeService planRuntimeService;
    private final AsyncJobRepository asyncJobRepository;
    private final InternalAsyncCallbackService internalAsyncCallbackService;

    public AgentRuntimeController(PlanRuntimeService planRuntimeService,
                                  AsyncJobRepository asyncJobRepository,
                                  InternalAsyncCallbackService internalAsyncCallbackService) {
        this.planRuntimeService = planRuntimeService;
        this.asyncJobRepository = asyncJobRepository;
        this.internalAsyncCallbackService = internalAsyncCallbackService;
    }

    @PostMapping("/agent/tool-callback")
    public ResponseEntity<Map<String, Object>> toolCallback(@RequestBody ToolCallbackRequest request) {
        Map<String, Object> job = asyncJobRepository.findByExternalJobId(request.getExternalJobId());
        if (job != null && job.get("session_id") != null) {
            internalAsyncCallbackService.dispatch(request.getExternalJobId(), request.getStatus(), request.getPayload());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Collections.<String, Object>singletonMap("error", "async job not found"));
        }
        return ResponseEntity.ok(Collections.<String, Object>singletonMap("ok", true));
    }

    @GetMapping("/agent/sessions/{sessionId}/runtime")
    public ResponseEntity<Map<String, Object>> agentRuntime(@PathVariable String sessionId) {
        return ResponseEntity.ok(planRuntimeService.agentRuntimeSnapshot(sessionId));
    }

    public static class ToolCallbackRequest {
        private String externalJobId;
        private String status;
        private Map<String, Object> payload;

        public String getExternalJobId() {
            return externalJobId;
        }

        public void setExternalJobId(String externalJobId) {
            this.externalJobId = externalJobId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Map<String, Object> getPayload() {
            return payload;
        }

        public void setPayload(Map<String, Object> payload) {
            this.payload = payload;
        }
    }
}
