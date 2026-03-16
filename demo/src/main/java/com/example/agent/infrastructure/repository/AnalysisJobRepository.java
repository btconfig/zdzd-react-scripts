package com.example.agent.infrastructure.repository;

import com.example.agent.infrastructure.mapper.AnalysisJobMapper;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

@Repository
public class AnalysisJobRepository {
    private final AnalysisJobMapper mapper;

    public AnalysisJobRepository(AnalysisJobMapper mapper) {
        this.mapper = mapper;
    }

    public void insert(String analysisJobId,
                       String queryInstanceId,
                       String sessionId,
                       String turnId,
                       String toolCallId,
                       String status,
                       String analysisPrompt,
                       String keywordType,
                       Integer topN,
                       String modelProvider,
                       String modelName) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("analysisJobId", analysisJobId);
        params.put("queryInstanceId", queryInstanceId);
        params.put("sessionId", sessionId);
        params.put("turnId", turnId);
        params.put("toolCallId", toolCallId);
        params.put("status", status);
        params.put("analysisPrompt", analysisPrompt);
        params.put("keywordType", keywordType);
        params.put("topN", topN);
        params.put("modelProvider", modelProvider);
        params.put("modelName", modelName);
        params.put("sampleCount", 0);
        params.put("errorMessage", null);
        mapper.insert(params);
    }

    public void updateStatus(String analysisJobId, String status, int sampleCount, String errorMessage) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("analysisJobId", analysisJobId);
        params.put("status", status);
        params.put("sampleCount", sampleCount);
        params.put("errorMessage", errorMessage);
        mapper.updateStatus(params);
    }

    public Map<String, Object> findById(String analysisJobId) {
        return mapper.findById(analysisJobId);
    }

    public void upsertResult(String analysisJobId,
                             String keywordsJson,
                             String topicsJson,
                             String findingsSummary,
                             String evidenceCasesJson,
                             String rawLlmOutput,
                             String structuredJson) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("analysisJobId", analysisJobId);
        params.put("keywordsJson", keywordsJson);
        params.put("topicsJson", topicsJson);
        params.put("findingsSummary", findingsSummary);
        params.put("evidenceCasesJson", evidenceCasesJson);
        params.put("rawLlmOutput", rawLlmOutput);
        params.put("structuredJson", structuredJson);
        mapper.upsertResult(params);
    }

    public Map<String, Object> findResultByJobId(String analysisJobId) {
        return mapper.findResultByJobId(analysisJobId);
    }
}
