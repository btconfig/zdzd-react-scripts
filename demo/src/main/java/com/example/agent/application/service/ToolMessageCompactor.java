package com.example.agent.application.service;

import com.example.agent.domain.model.chat.ChatMessage;
import com.example.agent.domain.model.chat.ChatRole;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ToolMessageCompactor {
    private final ObjectMapper objectMapper;
    private final int maxToolMessageChars;

    public ToolMessageCompactor(ObjectMapper objectMapper,
                                @Value("${llm.context.max-tool-message-chars:4000}") int maxToolMessageChars) {
        this.objectMapper = objectMapper;
        this.maxToolMessageChars = maxToolMessageChars;
    }

    public ChatMessage compact(ChatMessage message) {
        if (message == null || message.getRole() != ChatRole.tool || message.getContent() == null) {
            return message;
        }
        if (message.getContent().length() <= maxToolMessageChars) {
            return message;
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(message.getContent(), new TypeReference<Map<String, Object>>() {});
            Map<String, Object> compact = new LinkedHashMap<String, Object>();
            copyIfPresent(payload, compact, "status");
            copyIfPresent(payload, compact, "type");
            copyIfPresent(payload, compact, "handleId");
            copyIfPresent(payload, compact, "asyncJobId");
            if (payload.get("error") instanceof Map) {
                compact.put("error", summarizeMap((Map<String, Object>) payload.get("error")));
            }
            if (payload.get("artifacts") instanceof Map) {
                compact.put("artifacts", summarizeMap((Map<String, Object>) payload.get("artifacts")));
            }
            if (payload.get("data") != null) {
                compact.put("data_summary", summarizeValue(payload.get("data")));
            } else if (payload.get("inlineData") != null) {
                compact.put("data_summary", summarizeValue(payload.get("inlineData")));
            }
            ChatMessage copy = shallowCopy(message);
            copy.setContent(objectMapper.writeValueAsString(compact));
            return copy;
        } catch (Exception ignore) {
            ChatMessage copy = shallowCopy(message);
            copy.setContent(message.getContent().substring(0, Math.min(maxToolMessageChars, message.getContent().length())) + "...[truncated]");
            return copy;
        }
    }

    private Map<String, Object> summarizeMap(Map<String, Object> map) {
        Map<String, Object> summarized = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            summarized.put(entry.getKey(), summarizeValue(entry.getValue()));
        }
        return summarized;
    }

    private Object summarizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            String text = (String) value;
            return text.length() <= 300 ? text : text.substring(0, 300) + "...";
        }
        if (value instanceof List) {
            List<?> source = (List<?>) value;
            List<Object> summarized = new ArrayList<Object>();
            int limit = Math.min(source.size(), 3);
            for (int i = 0; i < limit; i++) {
                summarized.add(summarizeValue(source.get(i)));
            }
            if (source.size() > limit) {
                summarized.add("...+" + (source.size() - limit) + " more");
            }
            return summarized;
        }
        if (value instanceof Map) {
            return summarizeMap((Map<String, Object>) value);
        }
        return value;
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }

    private ChatMessage shallowCopy(ChatMessage message) {
        ChatMessage copy = new ChatMessage();
        copy.setRole(message.getRole());
        copy.setContent(message.getContent());
        copy.setName(message.getName());
        copy.setToolCallId(message.getToolCallId());
        copy.setToolCalls(message.getToolCalls());
        return copy;
    }
}
