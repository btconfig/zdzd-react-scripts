package com.example.agent.application.service;

import com.example.agent.infrastructure.repository.AnalysisJobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AnalysisJobService {
    private final QueryHandleService queryHandleService;
    private final AnalysisJobRepository analysisJobRepository;
    private final AnalysisWorkerService analysisWorkerService;
    private final String modelProvider;
    private final String modelName;
    private final long replayWaitTimeoutMs;

    public AnalysisJobService(QueryHandleService queryHandleService,
                              AnalysisJobRepository analysisJobRepository,
                              AnalysisWorkerService analysisWorkerService,
                              @Value("${llm.provider:deepseek}") String modelProvider,
                              @Value("${llm.model:${llm.deepseek.model:deepseek-chat}}") String modelName,
                              @Value("${agent.analysis.replay-wait-timeout-ms:20000}") long replayWaitTimeoutMs) {
        this.queryHandleService = queryHandleService;
        this.analysisJobRepository = analysisJobRepository;
        this.analysisWorkerService = analysisWorkerService;
        this.modelProvider = modelProvider;
        this.modelName = modelName;
        this.replayWaitTimeoutMs = replayWaitTimeoutMs;
    }

    public Map<String, Object> createJob(Map<String, Object> request, String sessionId, String turnId, String toolCallId) {
        String queryInstanceId = stringValue(request.get("query_instance_id"));
        Map<String, Object> queryInstance = queryHandleService.findQueryInstance(queryInstanceId);
        if (queryInstance == null) {
            throw new IllegalArgumentException("query_instance_id not found: " + queryInstanceId);
        }
        String analysisJobId = "job-" + UUID.randomUUID();
        Integer topN = request.get("top_n") instanceof Number ? ((Number) request.get("top_n")).intValue() : 10;
        analysisJobRepository.insert(
                analysisJobId,
                queryInstanceId,
                sessionId,
                turnId,
                toolCallId,
                "PENDING",
                stringValue(request.get("analysis_prompt")),
                stringValue(request.get("keyword_type")),
                topN,
                modelProvider,
                modelName
        );
        analysisWorkerService.start(analysisJobId);

        Map<String, Object> artifacts = new LinkedHashMap<String, Object>();
        artifacts.put("analysis_job_id", analysisJobId);
        artifacts.put("query_instance_id", queryInstanceId);
        artifacts.put("query_total", queryInstance.get("total_count"));
        return artifacts;
    }

    public Map<String, Object> awaitPayload(String analysisJobId) {
        return analysisWorkerService.awaitPayload(analysisJobId, replayWaitTimeoutMs);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
