package com.example.agent.application.service;

import com.example.agent.domain.model.SkillDefinition;
import com.example.agent.domain.model.ToolDefinition;
import com.example.agent.infrastructure.repository.SkillDefinitionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SkillAdminService {
    private final SkillRegistry skillRegistry;
    private final ToolRegistry toolRegistry;
    private final SkillDefinitionRepository skillDefinitionRepository;
    private final ObjectMapper objectMapper;

    public SkillAdminService(SkillRegistry skillRegistry,
                             ToolRegistry toolRegistry,
                             SkillDefinitionRepository skillDefinitionRepository,
                             ObjectMapper objectMapper) {
        this.skillRegistry = skillRegistry;
        this.toolRegistry = toolRegistry;
        this.skillDefinitionRepository = skillDefinitionRepository;
        this.objectMapper = objectMapper;
    }

    public List<SkillDefinition> listSkills() {
        return skillRegistry.all();
    }

    public List<ToolDefinition> listTools() {
        return toolRegistry.allDefinitions();
    }

    public SkillDefinition saveSkill(String skillId,
                                     String name,
                                     String description,
                                     boolean enabled,
                                     List<String> toolIds,
                                     String promptMarkdown) {
        validateSkill(skillId, name, toolIds, promptMarkdown);
        String toolIdsJson;
        try {
            toolIdsJson = objectMapper.writeValueAsString(toolIds);
        } catch (Exception e) {
            throw new RuntimeException("failed to serialize tool ids", e);
        }
        skillDefinitionRepository.upsert(skillId, name, description, enabled, toolIdsJson, promptMarkdown);
        return skillRegistry.findById(skillId)
                .orElseThrow(() -> new IllegalStateException("saved skill not found: " + skillId));
    }

    private void validateSkill(String skillId,
                               String name,
                               List<String> toolIds,
                               String promptMarkdown) {
        if (skillId == null || skillId.trim().isEmpty()) {
            throw new IllegalArgumentException("skillId is required");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("name is required");
        }
        if (promptMarkdown == null || promptMarkdown.trim().isEmpty()) {
            throw new IllegalArgumentException("promptMarkdown is required");
        }
        if (toolIds == null) {
            throw new IllegalArgumentException("toolIds is required");
        }
        for (String toolId : toolIds) {
            if (!toolRegistry.findById(toolId).isPresent()) {
                throw new IllegalArgumentException("unknown toolId: " + toolId);
            }
        }
    }
}
