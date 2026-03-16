package com.example.agent.infrastructure.repository;

import com.example.agent.infrastructure.mapper.ToolDefinitionMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class ToolDefinitionRepository {
    private final ToolDefinitionMapper mapper;

    public ToolDefinitionRepository(ToolDefinitionMapper mapper) {
        this.mapper = mapper;
    }

    public List<Map<String, Object>> findAllEnabled() {
        return mapper.findAllEnabled();
    }

    public List<Map<String, Object>> findAll() {
        return mapper.findAll();
    }

    public Map<String, Object> findById(String toolId) {
        return mapper.findById(toolId);
    }
}
