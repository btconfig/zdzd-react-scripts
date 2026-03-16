package com.example.agent.infrastructure.llm;

import com.example.agent.domain.model.ToolDefinition;
import com.example.agent.domain.model.chat.ChatMessage;
import com.example.agent.domain.model.chat.ChatRole;
import com.example.agent.domain.model.chat.ToolCall;
import com.example.agent.domain.model.chat.ToolFunctionCall;
import com.example.agent.domain.port.LlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DummyLlmClient now always performs a real HTTP call to the configured
 * OpenAI-compatible chat completions API. It forwards the full
 * conversation (including tool messages) and supports function tool calls
 * when enabled.
 */
@Component
public class DummyLlmClient implements LlmClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String provider;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public DummyLlmClient(
            @Value("${llm.provider:deepseek}") String provider,
            @Value("${llm.api-key:${llm.deepseek.api-key:${DEEPSEEK_API_KEY:}}}") String apiKey,
            @Value("${llm.base-url:${llm.deepseek.base-url:https://api.deepseek.com/v1/chat/completions}}") String baseUrl,
            @Value("${llm.model:${llm.deepseek.model:deepseek-chat}}") String model) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    @Override
    public ChatMessage chat(List<ChatMessage> messages, boolean useTools) {
        return chat(messages, new ArrayList<ToolDefinition>());
    }

    @Override
    public ChatMessage chat(List<ChatMessage> messages, List<ToolDefinition> tools) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("LLM API key is required for provider " + provider + ". Set llm.api-key or the matching environment variable.");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", toApiMessages(messages));
        body.put("temperature", 0.3);
        if (tools != null && !tools.isEmpty()) {
            body.put("tool_choice", "auto");
            body.put("tools", toolDefinitions(tools));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(baseUrl, entity, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException(provider + " API error: " + response.getStatusCode() + " " + response.getBody());
        }
        return parseResponse(response.getBody());
    }

    private List<Map<String, Object>> toApiMessages(List<ChatMessage> messages) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ChatMessage msg : messages) {
            Map<String, Object> m = new HashMap<>();
            m.put("role", msg.getRole().name());
            if (msg.getContent() != null) {
                m.put("content", msg.getContent());
            }
            if (msg.getRole() == ChatRole.tool) {
                m.put("tool_call_id", msg.getToolCallId());
                m.put("name", msg.getName());
            }
            if (msg.getRole() == ChatRole.assistant && msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                List<Map<String, Object>> toolCalls = new ArrayList<>();
                for (ToolCall tc : msg.getToolCalls()) {
                    Map<String, Object> tcMap = new HashMap<>();
                    tcMap.put("id", tc.getId());
                    tcMap.put("type", "function");
                    Map<String, Object> func = new HashMap<>();
                    func.put("name", tc.getFunction().getName());
                    func.put("arguments", tc.getFunction().getArguments());
                    tcMap.put("function", func);
                    toolCalls.add(tcMap);
                }
                m.put("tool_calls", toolCalls);
            }
            list.add(m);
        }
        return list;
    }

    private ChatMessage parseResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode messageNode = root.path("choices").get(0).path("message");
            ChatMessage reply = new ChatMessage();
            reply.setRole(ChatRole.assistant);
            reply.setContent(messageNode.path("content").isMissingNode() ? null : messageNode.path("content").asText());
            JsonNode toolCallsNode = messageNode.path("tool_calls");
            if (toolCallsNode.isArray() && toolCallsNode.size() > 0) {
                List<ToolCall> toolCalls = new ArrayList<>();
                for (JsonNode tcNode : toolCallsNode) {
                    ToolCall tc = new ToolCall();
                    tc.setId(tcNode.path("id").asText());
                    JsonNode funcNode = tcNode.path("function");
                    ToolFunctionCall func = new ToolFunctionCall();
                    func.setName(funcNode.path("name").asText());
                    func.setArguments(funcNode.path("arguments").asText());
                    tc.setFunction(func);
                    toolCalls.add(tc);
                }
                reply.setToolCalls(toolCalls);
            }
            return reply;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse " + provider + " response", e);
        }
    }

    private List<Map<String, Object>> toolDefinitions(List<ToolDefinition> definitions) {
        List<Map<String, Object>> toolMaps = new ArrayList<>();
        for (ToolDefinition definition : definitions) {
            Map<String, Object> toolMap = new HashMap<>();
            toolMap.put("type", "function");
            Map<String, Object> function = new HashMap<>();
            function.put("name", definition.getToolId());
            function.put("description", definition.getDescription());
            function.put("parameters", definition.getInputSchema());
            toolMap.put("function", function);
            toolMaps.add(toolMap);
        }
        return toolMaps;
    }
}
