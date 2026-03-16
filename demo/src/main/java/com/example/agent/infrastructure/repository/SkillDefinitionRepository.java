package com.example.agent.infrastructure.repository;

import com.example.agent.infrastructure.mapper.SkillDefinitionMapper;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class SkillDefinitionRepository {
    private final SkillDefinitionMapper mapper;

    public SkillDefinitionRepository(SkillDefinitionMapper mapper) {
        this.mapper = mapper;
    }

    public List<Map<String, Object>> findAll() {
        return mapper.findAll();
    }

    public Map<String, Object> findById(String skillId) {
        return mapper.findById(skillId);
    }

    public void upsert(String skillId,
                       String name,
                       String description,
                       boolean enabled,
                       String toolIdsJson,
                       String promptMarkdown) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("skillId", skillId);
        params.put("name", name);
        params.put("description", description);
        params.put("enabled", enabled);
        params.put("toolIdsJson", toolIdsJson);
        params.put("promptMarkdown", promptMarkdown);
        int updated = mapper.updateBySkillId(params);
        if (updated == 0) {
            mapper.insert(params);
        }
    }
}
