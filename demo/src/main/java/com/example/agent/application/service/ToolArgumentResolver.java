package com.example.agent.application.service;

import com.example.agent.domain.model.AgentSession;
import com.example.agent.domain.model.ToolDefinition;

import java.util.Map;

public interface ToolArgumentResolver {
    String key();

    Map<String, Object> resolve(AgentSession session, ToolDefinition definition, Map<String, Object> args);
}
