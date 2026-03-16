package com.example.agent.application.service;

import com.example.agent.domain.model.ToolDefinition;
import com.example.agent.domain.model.ToolResult;
import com.example.agent.domain.model.ToolResultType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class StandardToolResponseNormalizer implements ToolResponseNormalizer {
    @Override
    public String key() {
        return "STANDARD";
    }

    @Override
    public ToolResult normalize(ToolDefinition definition, Map<String, Object> body) {
        Map<String, Object> artifacts = body.get("artifacts") instanceof Map
                ? new HashMap<String, Object>((Map<String, Object>) body.get("artifacts"))
                : new HashMap<String, Object>();
        ToolResult result = new ToolResult();
        result.setArtifacts(artifacts);
        result.setType(definition.getResultType());
        result.setInlineData(body.get("data"));
        if (definition.getResultType() == ToolResultType.HANDLE) {
            result.setHandleId(stringValue(artifacts.get(definition.getHandleArtifactKey())));
        }
        if (definition.getResultType() == ToolResultType.ASYNC_JOB) {
            result.setAsyncJobId(stringValue(artifacts.get(definition.getAsyncJobArtifactKey())));
            result.setCallbackTopic("agent.tool.analysis.completed");
        }
        return result;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
