package com.example.agent.infrastructure.tool;

import com.example.agent.application.service.ToolResponseNormalizerRegistry;
import com.example.agent.application.service.ToolRegistry;
import com.example.agent.domain.model.ToolDefinition;
import com.example.agent.domain.model.ToolCommand;
import com.example.agent.domain.model.ToolResult;
import com.example.agent.domain.port.ToolExecutor;
import com.example.agent.interfaces.rest.ToolsController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ToolExecutor implementation that calls local ToolsController APIs over HTTP.
 */
@Component
@Primary
public class ToolsApiToolExecutor implements ToolExecutor {
    private static final Logger log = LoggerFactory.getLogger(ToolsApiToolExecutor.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ToolRegistry toolRegistry;
    private final ToolResponseNormalizerRegistry toolResponseNormalizerRegistry;
    private final ToolsController toolsController;
    private final String toolsBaseUrl;
    private final boolean autoCallbackEnabled;
    private final long autoCallbackDelayMs;

    public ToolsApiToolExecutor(ToolRegistry toolRegistry,
                                ToolResponseNormalizerRegistry toolResponseNormalizerRegistry,
                                ToolsController toolsController,
                                @Value("${tools.base-url:http://localhost:8080}") String toolsBaseUrl,
                                @Value("${tools.mock.auto-callback-enabled:true}") boolean autoCallbackEnabled,
                                @Value("${tools.mock.auto-callback-delay-ms:300}") long autoCallbackDelayMs) {
        this.toolRegistry = toolRegistry;
        this.toolResponseNormalizerRegistry = toolResponseNormalizerRegistry;
        this.toolsController = toolsController;
        this.toolsBaseUrl = toolsBaseUrl;
        this.autoCallbackEnabled = autoCallbackEnabled;
        this.autoCallbackDelayMs = autoCallbackDelayMs;
    }

    @Override
    public ToolResult execute(ToolCommand command) {
        String toolName = command.getToolName();
        ToolDefinition definition = toolRegistry.findById(toolName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tool mapping: " + toolName));
        String endpoint = definition.getEndpoint();
        log.info("[tools-api] invoking toolName={}, endpoint={}, planInstanceId={}, nodeId={}",
                toolName, endpoint, command.getPlanInstanceId(), command.getNodeId());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> requestArgs = requestArgs(command);
        ResponseEntity<Map> response;
        try {
            if (isLocalMode()) {
                response = invokeLocal(endpoint, requestArgs);
            } else {
                HttpEntity<Map<String, Object>> request = new HttpEntity<Map<String, Object>>(requestArgs, headers);
                response = restTemplate.postForEntity(toolsBaseUrl + endpoint, request, Map.class);
            }
        } catch (HttpStatusCodeException e) {
            throw toValidationException(e);
        }
        Map<String, Object> body = response.getBody() == null ? new HashMap<String, Object>() : response.getBody();
        Map<String, Object> artifacts = getArtifacts(body);
        log.info("[tools-api] tool response received toolName={}, status={}, artifactKeys={}",
                toolName, body.get("status"), artifacts.keySet());
        return toolResponseNormalizerRegistry.resolve(definition.getResponseNormalizer()).normalize(definition, body);
    }

    private Map<String, Object> getArtifacts(Map<String, Object> body) {
        if (body.get("artifacts") instanceof Map) {
            return (Map<String, Object>) body.get("artifacts");
        }
        return new HashMap<String, Object>();
    }

    @SuppressWarnings("unchecked")
    private RuntimeException toValidationException(HttpStatusCodeException e) {
        if (e.getStatusCode().is4xxClientError()) {
            try {
                Map<String, Object> body = restTemplate.getMessageConverters().stream()
                        .findFirst()
                        .map(converter -> new LinkedHashMap<String, Object>())
                        .orElse(new LinkedHashMap<String, Object>());
                body = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                        e.getResponseBodyAsString(),
                        LinkedHashMap.class
                );
                if (body.get("error") instanceof Map) {
                    Map<String, Object> error = (Map<String, Object>) body.get("error");
                    return new com.example.agent.domain.model.ToolValidationException(
                            stringValue(error.get("code")),
                            stringValue(error.get("message")),
                            stringValue(error.get("hint")),
                            !Boolean.FALSE.equals(error.get("retryable")),
                            error.get("details") instanceof Map ? (Map<String, Object>) error.get("details") : new LinkedHashMap<String, Object>()
                    );
                }
            } catch (Exception ignore) {
                // Fall through to generic runtime error if body isn't our validation payload.
            }
        }
        return new RuntimeException(e.getMessage(), e);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean isLocalMode() {
        return "local://tools".equalsIgnoreCase(toolsBaseUrl);
    }

    private Map<String, Object> requestArgs(ToolCommand command) {
        Map<String, Object> requestArgs = new HashMap<String, Object>(command.getArgs());
        requestArgs.put("__session_id", command.getPlanInstanceId());
        requestArgs.put("__turn_id", command.getTurnId());
        requestArgs.put("__tool_call_id", command.getNodeId());
        return requestArgs;
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> invokeLocal(String endpoint, Map<String, Object> args) {
        if ("/tools/sample-sessions-by-keywords".equals(endpoint)) {
            return (ResponseEntity<Map>) (ResponseEntity<?>) toolsController.sampleSessions(args);
        }
        if ("/tools/query-customer-sessions-mysql".equals(endpoint)) {
            return (ResponseEntity<Map>) (ResponseEntity<?>) toolsController.queryCustomerSessionsMysql(args);
        }
        if ("/tools/query-sessions-handle".equals(endpoint)) {
            return (ResponseEntity<Map>) (ResponseEntity<?>) toolsController.querySessionsHandle(args);
        }
        if ("/tools/analyze-keywords-async".equals(endpoint)) {
            return (ResponseEntity<Map>) (ResponseEntity<?>) toolsController.analyzeKeywordsAsync(args);
        }
        if ("/tools/build-keyword-report".equals(endpoint)) {
            return (ResponseEntity<Map>) (ResponseEntity<?>) toolsController.buildKeywordReport(args);
        }
        throw new IllegalArgumentException("Unsupported local endpoint: " + endpoint);
    }
}
