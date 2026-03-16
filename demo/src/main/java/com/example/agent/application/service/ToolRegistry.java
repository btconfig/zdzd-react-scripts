package com.example.agent.application.service;

import com.example.agent.domain.model.RiskLevel;
import com.example.agent.domain.model.TimeNormalizationMode;
import com.example.agent.domain.model.ToolDefinition;
import com.example.agent.domain.model.ToolResultType;
import com.example.agent.infrastructure.repository.ToolDefinitionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ToolRegistry {
    private final ToolDefinitionRepository toolDefinitionRepository;
    private final ObjectMapper objectMapper;

    public ToolRegistry(ToolDefinitionRepository toolDefinitionRepository, ObjectMapper objectMapper) {
        this.toolDefinitionRepository = toolDefinitionRepository;
        this.objectMapper = objectMapper;
    }

    public Optional<ToolDefinition> findById(String toolId) {
        for (ToolDefinition definition : all()) {
            if (definition != null && toolId != null && toolId.equals(definition.getToolId())) {
                return Optional.of(definition);
            }
        }
        return Optional.empty();
    }

    public List<ToolDefinition> findByIds(List<String> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<ToolDefinition> all = all();
        List<ToolDefinition> result = new ArrayList<ToolDefinition>();
        for (String toolId : toolIds) {
            for (ToolDefinition toolDefinition : all) {
                if (toolDefinition != null && toolId.equals(toolDefinition.getToolId())) {
                    result.add(toolDefinition);
                    break;
                }
            }
        }
        return result;
    }

    public List<ToolDefinition> all() {
        List<Map<String, Object>> rows = toolDefinitionRepository.findAllEnabled();
        List<ToolDefinition> result = new ArrayList<ToolDefinition>();
        for (Map<String, Object> row : rows) {
            result.add(fromRow(row));
        }
        return result;
    }

    public List<ToolDefinition> allDefinitions() {
        List<Map<String, Object>> rows = toolDefinitionRepository.findAll();
        List<ToolDefinition> result = new ArrayList<ToolDefinition>();
        for (Map<String, Object> row : rows) {
            result.add(fromRow(row));
        }
        return result;
    }

    private ToolDefinition fromRow(Map<String, Object> row) {
        ToolDefinition toolDefinition = new ToolDefinition();
        toolDefinition.setToolId(stringValue(row.get("tool_id")));
        toolDefinition.setName(stringValue(row.get("name")));
        toolDefinition.setDescription(stringValue(row.get("description")));
        toolDefinition.setEndpoint(stringValue(row.get("endpoint")));
        toolDefinition.setInputSchema(parseSchema(stringValue(row.get("input_schema_json"))));
        toolDefinition.setResultType(ToolResultType.valueOf(stringValue(row.get("result_type"))));
        toolDefinition.setAsync(Boolean.TRUE.equals(row.get("async_flag")) || "1".equals(String.valueOf(row.get("async_flag"))) || "true".equalsIgnoreCase(String.valueOf(row.get("async_flag"))));
        if (row.get("timeout_seconds") instanceof Number) {
            toolDefinition.setTimeoutSeconds(((Number) row.get("timeout_seconds")).intValue());
        }
        String riskLevel = stringValue(row.get("risk_level"));
        toolDefinition.setRiskLevel(riskLevel == null ? RiskLevel.LOW : RiskLevel.valueOf(riskLevel));
        String timeNormalizationMode = stringValue(row.get("time_normalization_mode"));
        if (timeNormalizationMode != null && !timeNormalizationMode.trim().isEmpty()) {
            toolDefinition.setTimeNormalizationMode(TimeNormalizationMode.valueOf(timeNormalizationMode));
        }
        String requestPreprocessor = stringValue(row.get("request_preprocessor"));
        if (requestPreprocessor != null && !requestPreprocessor.trim().isEmpty()) {
            toolDefinition.setRequestPreprocessor(requestPreprocessor);
        }
        String responseNormalizer = stringValue(row.get("response_normalizer"));
        if (responseNormalizer != null && !responseNormalizer.trim().isEmpty()) {
            toolDefinition.setResponseNormalizer(responseNormalizer);
        }
        toolDefinition.setHandleArtifactKey(stringValue(row.get("handle_artifact_key")));
        toolDefinition.setAsyncJobArtifactKey(stringValue(row.get("async_job_artifact_key")));
        return toolDefinition;
    }

    private Map<String, Object> parseSchema(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("failed to parse input_schema_json for tool", e);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
