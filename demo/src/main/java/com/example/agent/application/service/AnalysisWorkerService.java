package com.example.agent.application.service;

import com.example.agent.domain.model.chat.ChatMessage;
import com.example.agent.domain.model.chat.ChatRole;
import com.example.agent.domain.port.LlmClient;
import com.example.agent.infrastructure.repository.AnalysisJobRepository;
import com.example.agent.infrastructure.repository.QueryInstanceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalysisWorkerService {
    private final AnalysisJobRepository analysisJobRepository;
    private final QueryInstanceRepository queryInstanceRepository;
    private final AnalysisResultAssembler analysisResultAssembler;
    private final InternalAsyncCallbackService internalAsyncCallbackService;
    private final LlmClient llmClient;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final TaskExecutor taskExecutor;
    private final int maxLlmRecords;

    public AnalysisWorkerService(AnalysisJobRepository analysisJobRepository,
                                 QueryInstanceRepository queryInstanceRepository,
                                 AnalysisResultAssembler analysisResultAssembler,
                                 InternalAsyncCallbackService internalAsyncCallbackService,
                                 LlmClient llmClient,
                                 com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                                 @Qualifier("agentTaskExecutor") TaskExecutor taskExecutor,
                                 @Value("${agent.analysis.max-llm-records:200}") int maxLlmRecords) {
        this.analysisJobRepository = analysisJobRepository;
        this.queryInstanceRepository = queryInstanceRepository;
        this.analysisResultAssembler = analysisResultAssembler;
        this.internalAsyncCallbackService = internalAsyncCallbackService;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
        this.maxLlmRecords = maxLlmRecords;
    }

    public void start(String analysisJobId) {
        taskExecutor.execute(() -> run(analysisJobId));
    }

    public Map<String, Object> awaitPayload(String analysisJobId, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> job = analysisJobRepository.findById(analysisJobId);
            if (job != null) {
                String status = String.valueOf(job.get("status"));
                if ("SUCCEEDED".equalsIgnoreCase(status)) {
                    return buildSuccessPayload(analysisJobId);
                }
                if ("FAILED".equalsIgnoreCase(status)) {
                    return buildFailurePayload(analysisJobId, String.valueOf(job.get("error_message")));
                }
            }
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("interrupted while waiting for analysis job", e);
            }
        }
        throw new IllegalStateException("analysis job timeout: " + analysisJobId);
    }

    private void run(String analysisJobId) {
        Map<String, Object> job = analysisJobRepository.findById(analysisJobId);
        if (job == null) {
            return;
        }
        try {
            Map<String, Object> queryInstance = queryInstanceRepository.findById(String.valueOf(job.get("query_instance_id")));
            List<Map<String, Object>> records = queryInstanceRepository.findResultItems(String.valueOf(job.get("query_instance_id")), maxLlmRecords);
            Map<String, Object> structured = analyze(job, queryInstance, records);
            String structuredJson = objectMapper.writeValueAsString(structured);
            analysisJobRepository.upsertResult(
                    analysisJobId,
                    objectMapper.writeValueAsString(structured.get("keywords")),
                    objectMapper.writeValueAsString(structured.get("topics")),
                    String.valueOf(structured.get("findings_summary")),
                    objectMapper.writeValueAsString(structured.get("evidence_cases")),
                    String.valueOf(structured.get("raw_llm_output")),
                    structuredJson
            );
            analysisJobRepository.updateStatus(analysisJobId, "SUCCEEDED", records.size(), null);
            internalAsyncCallbackService.dispatch(analysisJobId, "SUCCEEDED", buildSuccessPayload(analysisJobId));
        } catch (Exception e) {
            analysisJobRepository.updateStatus(analysisJobId, "FAILED", 0, e.getMessage());
            internalAsyncCallbackService.dispatch(analysisJobId, "FAILED", buildFailurePayload(analysisJobId, e.getMessage()));
        }
    }

    private Map<String, Object> analyze(Map<String, Object> job,
                                        Map<String, Object> queryInstance,
                                        List<Map<String, Object>> records) throws Exception {
        String analysisPrompt = String.valueOf(job.get("analysis_prompt"));
        Integer topN = job.get("top_n") instanceof Number ? ((Number) job.get("top_n")).intValue() : 10;

        ChatMessage system = new ChatMessage();
        system.setRole(ChatRole.system);
        system.setContent("""
                You are an analysis engine for customer-support conversations.
                Return valid JSON only.
                JSON fields:
                - keywords: string[]
                - topics: [{ "topic": string, "evidence": string }]
                - findings_summary: string
                - evidence_cases: [{ "session_code": string, "user_message": string, "agent_reply": string, "reason": string }]
                Ground findings only in the provided session data.
                """);
        ChatMessage user = new ChatMessage();
        user.setRole(ChatRole.user);
        user.setContent(buildAnalysisInput(job, queryInstance, records, analysisPrompt, topN));
        ChatMessage reply = llmClient.chat(java.util.Arrays.asList(system, user), false);

        Map<String, Object> structured = analysisResultAssembler.assemble(reply.getContent(), topN);
        structured.put("raw_llm_output", reply.getContent());
        structured.put("sample_count", records.size());
        return structured;
    }

    private String buildAnalysisInput(Map<String, Object> job,
                                      Map<String, Object> queryInstance,
                                      List<Map<String, Object>> records,
                                      String analysisPrompt,
                                      int topN) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("analysis_prompt", analysisPrompt);
        payload.put("keyword_type", job.get("keyword_type"));
        payload.put("top_n", topN);
        payload.put("query_meta", queryInstance);
        payload.put("records", records);
        return objectMapper.writeValueAsString(payload);
    }

    private Map<String, Object> buildSuccessPayload(String analysisJobId) {
        Map<String, Object> result = analysisJobRepository.findResultByJobId(analysisJobId);
        Map<String, Object> artifacts = new LinkedHashMap<String, Object>();
        artifacts.put("analysis_job_id", analysisJobId);
        artifacts.put("keywords", readStringList(result == null ? null : result.get("keywords_json")));
        artifacts.put("topics", readMapList(result == null ? null : result.get("topics_json")));
        artifacts.put("findings_summary", result == null ? null : result.get("findings_summary"));
        artifacts.put("evidence_cases", readMapList(result == null ? null : result.get("evidence_cases_json")));

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("status", "SUCCEEDED");
        payload.put("artifacts", artifacts);
        return payload;
    }

    private Map<String, Object> buildFailurePayload(String analysisJobId, String errorMessage) {
        Map<String, Object> artifacts = new LinkedHashMap<String, Object>();
        artifacts.put("analysis_job_id", analysisJobId);

        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("type", "ANALYSIS");
        error.put("code", "ANALYSIS_JOB_FAILED");
        error.put("message", errorMessage);
        error.put("hint", "Adjust the analysis prompt or query scope, then retry analyze_keywords_async.");
        error.put("retryable", true);

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("status", "FAILED");
        payload.put("artifacts", artifacts);
        payload.put("error", error);
        return payload;
    }

    private List<Map<String, Object>> readMapList(Object raw) {
        if (raw == null || String.valueOf(raw).trim().isEmpty()) {
            return new ArrayList<Map<String, Object>>();
        }
        try {
            return objectMapper.readValue(String.valueOf(raw), new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return new ArrayList<Map<String, Object>>();
        }
    }

    private List<String> readStringList(Object raw) {
        if (raw == null || String.valueOf(raw).trim().isEmpty()) {
            return new ArrayList<String>();
        }
        try {
            return objectMapper.readValue(String.valueOf(raw), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<String>();
        }
    }
}
