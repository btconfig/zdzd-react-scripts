package com.example.agent.application.service;

import com.example.agent.domain.model.ExecutableDslSnapshot;
import com.example.agent.domain.model.ExecutableDslInputBinding;
import com.example.agent.domain.model.ExecutableDslStep;
import com.example.agent.domain.model.ToolExecutionResult;
import com.example.agent.domain.model.ToolResult;
import com.example.agent.domain.model.ToolResultType;
import com.example.agent.domain.model.chat.ChatMessage;
import com.example.agent.domain.model.chat.ChatRole;
import com.example.agent.domain.port.LlmClient;
import com.example.agent.infrastructure.repository.DslExecutionLogRepository;
import com.example.agent.infrastructure.repository.DslInstanceRepository;
import com.example.agent.infrastructure.repository.DslStepRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DslRuntimeService {
    private final DslInstanceRepository dslInstanceRepository;
    private final DslStepRepository dslStepRepository;
    private final DslExecutionLogRepository executionLogRepository;
    private final ToolRegistry toolRegistry;
    private final ToolExecutionPipeline toolExecutionPipeline;
    private final AgentSessionStore sessionStore;
    private final AnalysisJobService analysisJobService;
    private final ObjectMapper objectMapper;
    private final LlmClient llmClient;

    public DslRuntimeService(DslInstanceRepository dslInstanceRepository,
                             DslStepRepository dslStepRepository,
                             DslExecutionLogRepository executionLogRepository,
                             ToolRegistry toolRegistry,
                             ToolExecutionPipeline toolExecutionPipeline,
                             AgentSessionStore sessionStore,
                             AnalysisJobService analysisJobService,
                             ObjectMapper objectMapper,
                             LlmClient llmClient) {
        this.dslInstanceRepository = dslInstanceRepository;
        this.dslStepRepository = dslStepRepository;
        this.executionLogRepository = executionLogRepository;
        this.toolRegistry = toolRegistry;
        this.toolExecutionPipeline = toolExecutionPipeline;
        this.sessionStore = sessionStore;
        this.analysisJobService = analysisJobService;
        this.objectMapper = objectMapper;
        this.llmClient = llmClient;
    }

    public ExecutableDslSnapshot get(String dslId) {
        Map<String, Object> row = dslInstanceRepository.findById(dslId);
        if (row == null) {
            return null;
        }
        try {
            ExecutableDslSnapshot snapshot = new ExecutableDslSnapshot();
            snapshot.setDslId(dslId);
            snapshot.setSessionId((String) row.get("session_id"));
            snapshot.setTitle((String) row.get("title"));
            snapshot.setStatus((String) row.get("status"));
            snapshot.setSummary((String) row.get("summary"));
            snapshot.setOutputJson((String) row.get("output_json"));
            for (Map<String, Object> stepRow : dslStepRepository.findByDslId(dslId)) {
                ExecutableDslStep step = new ExecutableDslStep();
                step.setStepId((String) stepRow.get("step_id"));
                step.setToolName((String) stepRow.get("tool_name"));
                step.setArgsTemplate(readJsonMap(stepRow.get("args_template_json")));
                step.setDependsOn(readJsonList(stepRow.get("depends_on_json")));
                step.setInputBindings(readJsonBindings(stepRow.get("input_bindings_json")));
                step.setOutputSchema(readJsonList(stepRow.get("output_schema_json")));
                snapshot.getSteps().add(step);
            }
            return snapshot;
        } catch (Exception e) {
            throw new RuntimeException("failed to load dsl snapshot", e);
        }
    }

    public Map<String, Object> execute(String dslId, Map<String, Object> overrides) {
        ExecutableDslSnapshot snapshot = get(dslId);
        if (snapshot == null) {
            throw new IllegalArgumentException("dsl not found: " + dslId);
        }
        String executionId = "dsl-run-" + UUID.randomUUID();
        Map<String, Object> output = new HashMap<String, Object>();
        List<Map<String, Object>> stepOutputs = new ArrayList<Map<String, Object>>();
        com.example.agent.domain.model.AgentSession replaySession = new com.example.agent.domain.model.AgentSession();
        replaySession.setSessionId("dsl-session-" + UUID.randomUUID());
        replaySession.setTitle("DSL replay for " + dslId);
        replaySession.setQuery(snapshot.getTitle());
        replaySession.setGlobalContext(overrides == null ? new HashMap<String, Object>() : overrides);
        sessionStore.save(replaySession);
        Map<String, Map<String, Object>> stepContext = new HashMap<String, Map<String, Object>>();
        for (ExecutableDslStep step : snapshot.getSteps()) {
            Map<String, Object> mergedArgs = new HashMap<String, Object>(step.getArgsTemplate());
            if (overrides != null) {
                mergedArgs.putAll(overrides);
            }
            applyBindings(step, stepContext, mergedArgs);
            if ("final_response".equals(step.getToolName())) {
                Map<String, Object> finalPayload = executeFinalResponseStep(mergedArgs);
                Map<String, Object> stepOutput = new HashMap<String, Object>();
                stepOutput.put("stepId", step.getStepId());
                stepOutput.put("tool", step.getToolName());
                stepOutput.put("summary", "final answer generated");
                stepOutput.put("payload", finalPayload);
                stepOutputs.add(stepOutput);
                stepContext.put(step.getStepId(), finalPayload);
                output.put("finalAnswer", finalPayload.get("final_answer"));
                continue;
            }
            ToolExecutionResult result = toolExecutionPipeline.execute(
                    replaySession,
                    executionId,
                    step.getStepId(),
                    step.getToolName(),
                    mergedArgs,
                    toolRegistry.findByIds(java.util.Collections.singletonList(step.getToolName()))
            );
            Map<String, Object> normalizedPayload = result.getNormalizedPayload();
            if (result.isWaitingAsync() && "analyze_keywords_async".equals(step.getToolName())) {
                String analysisJobId = result.getToolResult() == null ? null : result.getToolResult().getAsyncJobId();
                normalizedPayload = analysisJobService.awaitPayload(analysisJobId);
                if ("FAILED".equalsIgnoreCase(String.valueOf(normalizedPayload.get("status")))) {
                    throw new IllegalStateException("dsl async analysis failed: " + normalizedPayload);
                }
                updateReplayExecutedStep(replaySession, step, normalizedPayload);
            }
            ToolResult toolResult = result.getToolResult();
            Map<String, Object> stepOutput = new HashMap<String, Object>();
            stepOutput.put("stepId", step.getStepId());
            stepOutput.put("tool", step.getToolName());
            stepOutput.put("summary", result.getSummary());
            stepOutput.put("payload", normalizedPayload);
            stepOutputs.add(stepOutput);
            stepContext.put(step.getStepId(), normalizedPayload);
            if (toolResult != null && toolResult.getType() == ToolResultType.HANDLE) {
                output.put(step.getToolName(), toolResult.getHandleId());
            } else if (toolResult != null) {
                output.put(step.getToolName(), toolResult.getInlineData());
            }
        }
        output.put("steps", stepOutputs);
        try {
            executionLogRepository.insert(dslId, executionId, "SUCCEEDED", objectMapper.writeValueAsString(output));
        } catch (Exception e) {
            throw new RuntimeException("failed to persist dsl execution", e);
        }
        return output;
    }

    private Map<String, Object> executeFinalResponseStep(Map<String, Object> args) {
        Map<String, Object> payload = new HashMap<String, Object>();
        String mode = args.get("mode") == null ? null : String.valueOf(args.get("mode"));
        if ("REPORT_RENDER".equals(mode)) {
            Object reportMarkdown = args.get("report_markdown");
            Object reportSummary = args.get("report_summary");
            payload.put("mode", mode);
            payload.put("final_answer", reportMarkdown != null ? reportMarkdown : reportSummary);
            return payload;
        }

        List<ChatMessage> messages = new ArrayList<ChatMessage>();
        ChatMessage system = new ChatMessage();
        system.setRole(ChatRole.system);
        system.setContent("You are a grounded summarization assistant. Produce a concise final answer using only the provided tool outputs. Do not invent facts.");
        messages.add(system);

        ChatMessage user = new ChatMessage();
        user.setRole(ChatRole.user);
        Map<String, Object> synthesisPrompt = new HashMap<String, Object>();
        synthesisPrompt.put("user_query", args.get("user_query"));
        synthesisPrompt.put("tool_result", args.get("tool_result"));
        synthesisPrompt.put("tool_artifacts", args.get("tool_artifacts"));
        synthesisPrompt.put("previous_answer_style_reference", args.get("final_answer_previous"));
        try {
            user.setContent(objectMapper.writeValueAsString(synthesisPrompt));
        } catch (Exception e) {
            throw new RuntimeException("failed to serialize final response synthesis prompt", e);
        }
        messages.add(user);
        ChatMessage reply = llmClient.chat(messages, false);
        payload.put("mode", "ANSWER_SYNTHESIS");
        payload.put("final_answer", reply == null ? null : reply.getContent());
        return payload;
    }

    private void updateReplayExecutedStep(com.example.agent.domain.model.AgentSession replaySession,
                                          ExecutableDslStep step,
                                          Map<String, Object> normalizedPayload) {
        if (replaySession.getExecutedSteps().isEmpty()) {
            return;
        }
        com.example.agent.domain.model.AgentExecutedStep executedStep =
                replaySession.getExecutedSteps().get(replaySession.getExecutedSteps().size() - 1);
        if (!step.getToolName().equals(executedStep.getToolName())) {
            return;
        }
        executedStep.setStatus("SUCCEEDED");
        executedStep.setResultSummary("async callback completed");
        executedStep.setResultPayload(normalizedPayload);
        Object data = normalizedPayload.get("data");
        if (data == null) {
            data = normalizedPayload.get("inlineData");
        }
        executedStep.setOutputData(data);
        Object artifacts = normalizedPayload.get("artifacts");
        if (artifacts instanceof Map) {
            executedStep.setArtifacts(new HashMap<String, Object>((Map<String, Object>) artifacts));
        }
    }

    private void applyBindings(ExecutableDslStep step,
                               Map<String, Map<String, Object>> stepContext,
                               Map<String, Object> mergedArgs) {
        for (ExecutableDslInputBinding binding : step.getInputBindings()) {
            Map<String, Object> sourceStep = stepContext.get(binding.getFromStep());
            if (sourceStep == null) {
                throw new IllegalStateException("missing source step context: " + binding.getFromStep());
            }
            Object resolved = resolvePath(sourceStep, binding.getSourcePath());
            if (resolved == null) {
                throw new IllegalStateException("missing binding source: " + binding.getSourcePath());
            }
            setNestedValue(mergedArgs, binding.getTargetArg(), resolved);
        }
    }

    private void setNestedValue(Map<String, Object> target, String path, Object value) {
        if (path == null || path.trim().isEmpty()) {
            return;
        }
        String[] parts = path.split("\\.");
        Map<String, Object> current = target;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (!(next instanceof Map)) {
                next = new HashMap<String, Object>();
                current.put(parts[i], next);
            }
            current = (Map<String, Object>) next;
        }
        current.put(parts[parts.length - 1], value);
    }

    private Object resolvePath(Map<String, Object> source, String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        Object current = source;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<?, ?>) current).get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private Map<String, Object> readJsonMap(Object raw) throws Exception {
        if (raw == null || String.valueOf(raw).trim().isEmpty() || "null".equals(String.valueOf(raw))) {
            return new HashMap<String, Object>();
        }
        return objectMapper.readValue(String.valueOf(raw), new TypeReference<Map<String, Object>>() {});
    }

    private List<String> readJsonList(Object raw) throws Exception {
        if (raw == null || String.valueOf(raw).trim().isEmpty() || "null".equals(String.valueOf(raw))) {
            return new ArrayList<String>();
        }
        return objectMapper.readValue(String.valueOf(raw), new TypeReference<List<String>>() {});
    }

    private List<ExecutableDslInputBinding> readJsonBindings(Object raw) throws Exception {
        if (raw == null || String.valueOf(raw).trim().isEmpty() || "null".equals(String.valueOf(raw))) {
            return new ArrayList<ExecutableDslInputBinding>();
        }
        return objectMapper.readValue(String.valueOf(raw), new TypeReference<List<ExecutableDslInputBinding>>() {});
    }
}
