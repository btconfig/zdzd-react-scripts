package com.example.agent.application.service;

import com.example.agent.domain.model.AgentSession;
import com.example.agent.domain.model.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class DefaultToolArgumentResolver implements ToolArgumentResolver {
    @Override
    public String key() {
        return "NONE";
    }

    @Override
    public Map<String, Object> resolve(AgentSession session, ToolDefinition definition, Map<String, Object> args) {
        return new LinkedHashMap<String, Object>(args);
    }
}
