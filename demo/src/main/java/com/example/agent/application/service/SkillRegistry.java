package com.example.agent.application.service;

import com.example.agent.domain.model.SkillDefinition;
import com.example.agent.infrastructure.repository.SkillDefinitionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SkillRegistry {
    private final SkillDefinitionRepository skillDefinitionRepository;
    private final ObjectMapper objectMapper;

    public SkillRegistry(SkillDefinitionRepository skillDefinitionRepository, ObjectMapper objectMapper) {
        this.skillDefinitionRepository = skillDefinitionRepository;
        this.objectMapper = objectMapper;
    }

    public List<SkillDefinition> allEnabled() {
        List<SkillDefinition> skills = all();
        List<SkillDefinition> enabled = new ArrayList<SkillDefinition>();
        for (SkillDefinition skill : skills) {
            if (skill != null && skill.isEnabled()) {
                enabled.add(skill);
            }
        }
        return enabled;
    }

    public Optional<SkillDefinition> findById(String skillId) {
        for (SkillDefinition skill : all()) {
            if (skill != null && skillId != null && skillId.equals(skill.getSkillId())) {
                return Optional.of(skill);
            }
        }
        return Optional.empty();
    }

    public List<SkillDefinition> all() {
        List<Map<String, Object>> rows = skillDefinitionRepository.findAll();
        List<SkillDefinition> result = new ArrayList<SkillDefinition>();
        for (Map<String, Object> row : rows) {
            result.add(fromRow(row));
        }
        return result;
    }

    private SkillDefinition fromRow(Map<String, Object> row) {
        SkillDefinition skill = new SkillDefinition();
        skill.setSkillId(stringValue(row.get("skill_id")));
        skill.setName(stringValue(row.get("name")));
        skill.setDescription(stringValue(row.get("description")));
        skill.setEnabled(Boolean.TRUE.equals(row.get("enabled")) || "1".equals(String.valueOf(row.get("enabled"))) || "true".equalsIgnoreCase(String.valueOf(row.get("enabled"))));
        skill.setPromptMarkdown(stringValue(row.get("prompt_markdown")));
        skill.setToolIds(parseToolIds(stringValue(row.get("tool_ids_json"))));
        return skill;
    }

    private List<String> parseToolIds(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<String>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            throw new RuntimeException("failed to parse tool_ids_json for skill", e);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
