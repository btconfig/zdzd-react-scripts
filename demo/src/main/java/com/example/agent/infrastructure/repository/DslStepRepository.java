package com.example.agent.infrastructure.repository;

import com.example.agent.infrastructure.mapper.DslStepMapper;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class DslStepRepository {
    private final DslStepMapper mapper;

    public DslStepRepository(DslStepMapper mapper) {
        this.mapper = mapper;
    }

    public void upsert(String dslId,
                       String stepId,
                       String toolName,
                       String argsTemplateJson,
                       String dependsOnJson,
                       String inputBindingsJson,
                       String outputSchemaJson) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("dslId", dslId);
        params.put("stepId", stepId);
        params.put("toolName", toolName);
        params.put("argsTemplateJson", argsTemplateJson);
        params.put("dependsOnJson", dependsOnJson);
        params.put("inputBindingsJson", inputBindingsJson);
        params.put("outputSchemaJson", outputSchemaJson);
        int updated = mapper.updateByDslStepId(params);
        if (updated == 0) {
            mapper.insert(params);
        }
    }

    public List<Map<String, Object>> findByDslId(String dslId) {
        return mapper.findByDslId(dslId);
    }
}
