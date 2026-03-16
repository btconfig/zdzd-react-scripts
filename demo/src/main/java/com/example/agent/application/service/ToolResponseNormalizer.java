package com.example.agent.application.service;

import com.example.agent.domain.model.ToolDefinition;
import com.example.agent.domain.model.ToolResult;

import java.util.Map;

public interface ToolResponseNormalizer {
    String key();

    ToolResult normalize(ToolDefinition definition, Map<String, Object> body);
}
