package com.example.agent.application.service;

import com.example.agent.domain.model.AgentExecutedStep;
import com.example.agent.domain.model.ExecutableDslInputBinding;
import com.example.agent.domain.model.AgentSession;
import com.example.agent.domain.model.ExecutableDslSnapshot;
import com.example.agent.domain.model.ExecutableDslStep;
import com.example.agent.infrastructure.repository.DslInstanceRepository;
import com.example.agent.infrastructure.repository.DslStepRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DslCompilerService {
    private final DslInstanceRepository dslInstanceRepository;
    private final DslStepRepository dslStepRepository;
    private final ObjectMapper objectMapper;

    public DslCompilerService(DslInstanceRepository dslInstanceRepository,
                              DslStepRepository dslStepRepository,
                              ObjectMapper objectMapper) {
        this.dslInstanceRepository = dslInstanceRepository;
        this.dslStepRepository = dslStepRepository;
        this.objectMapper = objectMapper;
    }

    public ExecutableDslSnapshot compile(AgentSession session) {
        ExecutableDslSnapshot snapshot = new ExecutableDslSnapshot();
        snapshot.setDslId("dsl-" + UUID.randomUUID());
        snapshot.setSessionId(session.getSessionId());
        snapshot.setTitle(session.getTitle());
        snapshot.setStatus("READY");
        snapshot.setSummary(session.getLatestAnswer());
        snapshot.setOutputJson(session.getLatestAnswer());

        List<CompiledStepContext> priorSteps = new ArrayList<CompiledStepContext>();
        int index = 1;
        for (AgentExecutedStep executedStep : session.getExecutedSteps()) {
            if (executedStep == null || !"SUCCEEDED".equalsIgnoreCase(executedStep.getStatus())) {
                continue;
            }
            ExecutableDslStep step = new ExecutableDslStep();
            step.setStepId("STEP_" + index++);
            step.setToolName(executedStep.getToolName());
            step.setArgsTemplate(buildArgsTemplate(executedStep, priorSteps, step));
            if (step.getInputBindings().isEmpty()) {
                if (!priorSteps.isEmpty()) {
                    step.getDependsOn().add(priorSteps.get(priorSteps.size() - 1).stepId);
                }
            } else {
                Set<String> dependsOn = new LinkedHashSet<String>();
                for (ExecutableDslInputBinding binding : step.getInputBindings()) {
                    dependsOn.add(binding.getFromStep());
                }
                step.setDependsOn(new ArrayList<String>(dependsOn));
            }
            step.setOutputSchema(new ArrayList<String>(executedStep.getArtifacts().keySet()));
            snapshot.getSteps().add(step);
            priorSteps.add(new CompiledStepContext(step.getStepId(), executedStep.getArtifacts()));
        }
        appendFinalResponseStep(session, snapshot);

        persist(snapshot);
        return snapshot;
    }

    private void appendFinalResponseStep(AgentSession session, ExecutableDslSnapshot snapshot) {
        if (session.getLatestAnswer() == null || session.getLatestAnswer().trim().isEmpty() || snapshot.getSteps().isEmpty()) {
            return;
        }
        ExecutableDslStep finalStep = new ExecutableDslStep();
        finalStep.setStepId("STEP_" + (snapshot.getSteps().size() + 1));
        finalStep.setToolName("final_response");

        Map<String, Object> argsTemplate = new HashMap<String, Object>();
        argsTemplate.put("user_query", session.getQuery());
        argsTemplate.put("final_answer_previous", session.getLatestAnswer());

        int reportStepIndex = findLastStepIndex(snapshot, "build_keyword_report");
        if (reportStepIndex >= 0) {
            ExecutableDslStep reportStep = snapshot.getSteps().get(reportStepIndex);
            argsTemplate.put("mode", "REPORT_RENDER");
            finalStep.getDependsOn().add(reportStep.getStepId());

            ExecutableDslInputBinding markdownBinding = new ExecutableDslInputBinding();
            markdownBinding.setTargetArg("report_markdown");
            markdownBinding.setFromStep(reportStep.getStepId());
            markdownBinding.setSourcePath("artifacts.report_markdown");
            finalStep.getInputBindings().add(markdownBinding);

            ExecutableDslInputBinding summaryBinding = new ExecutableDslInputBinding();
            summaryBinding.setTargetArg("report_summary");
            summaryBinding.setFromStep(reportStep.getStepId());
            summaryBinding.setSourcePath("artifacts.report_summary");
            finalStep.getInputBindings().add(summaryBinding);
        } else {
            ExecutableDslStep lastStep = snapshot.getSteps().get(snapshot.getSteps().size() - 1);
            argsTemplate.put("mode", "ANSWER_SYNTHESIS");
            finalStep.getDependsOn().add(lastStep.getStepId());

            ExecutableDslInputBinding resultBinding = new ExecutableDslInputBinding();
            resultBinding.setTargetArg("tool_result");
            resultBinding.setFromStep(lastStep.getStepId());
            resultBinding.setSourcePath("data");
            finalStep.getInputBindings().add(resultBinding);

            ExecutableDslInputBinding artifactsBinding = new ExecutableDslInputBinding();
            artifactsBinding.setTargetArg("tool_artifacts");
            artifactsBinding.setFromStep(lastStep.getStepId());
            artifactsBinding.setSourcePath("artifacts");
            finalStep.getInputBindings().add(artifactsBinding);
        }

        finalStep.setArgsTemplate(argsTemplate);
        finalStep.setOutputSchema(java.util.Collections.singletonList("final_answer"));
        snapshot.getSteps().add(finalStep);
    }

    private int findLastStepIndex(ExecutableDslSnapshot snapshot, String toolName) {
        for (int i = snapshot.getSteps().size() - 1; i >= 0; i--) {
            if (toolName.equals(snapshot.getSteps().get(i).getToolName())) {
                return i;
            }
        }
        return -1;
    }

    private void persist(ExecutableDslSnapshot snapshot) {
        try {
            dslInstanceRepository.upsert(
                    snapshot.getDslId(),
                    snapshot.getSessionId(),
                    snapshot.getTitle(),
                    snapshot.getStatus(),
                    snapshot.getSummary(),
                    snapshot.getOutputJson()
            );
            for (ExecutableDslStep step : snapshot.getSteps()) {
                dslStepRepository.upsert(
                    snapshot.getDslId(),
                    step.getStepId(),
                    step.getToolName(),
                        objectMapper.writeValueAsString(step.getArgsTemplate()),
                        objectMapper.writeValueAsString(step.getDependsOn()),
                        objectMapper.writeValueAsString(step.getInputBindings()),
                        objectMapper.writeValueAsString(step.getOutputSchema())
                );
            }
        } catch (Exception e) {
            throw new RuntimeException("failed to persist executable dsl", e);
        }
    }

    private Map<String, Object> buildArgsTemplate(AgentExecutedStep executedStep,
                                                  List<CompiledStepContext> priorSteps,
                                                  ExecutableDslStep step) {
        if ("build_keyword_report".equals(executedStep.getToolName())) {
            return buildReportArgsTemplate(executedStep, priorSteps, step);
        }
        Map<String, Object> template = new HashMap<String, Object>();
        for (Map.Entry<String, Object> arg : executedStep.getArgs().entrySet()) {
            ExecutableDslInputBinding binding = inferBinding(arg.getKey(), arg.getValue(), priorSteps);
            if (binding != null) {
                step.getInputBindings().add(binding);
                continue;
            }
            template.put(arg.getKey(), arg.getValue());
        }
        return template;
    }

    private Map<String, Object> buildReportArgsTemplate(AgentExecutedStep executedStep,
                                                        List<CompiledStepContext> priorSteps,
                                                        ExecutableDslStep step) {
        Map<String, Object> template = new HashMap<String, Object>();
        template.put("report_title", executedStep.getArgs().get("report_title"));
        template.put("report_blueprint", sanitizeBlueprint(executedStep.getArgs().get("report_blueprint")));
        Object rawReportInputs = executedStep.getArgs().get("report_inputs");
        if (rawReportInputs instanceof Map) {
            template.put("report_inputs", buildReportInputTemplate("report_inputs", rawReportInputs, priorSteps, step));
        } else {
            template.put("report_inputs", new HashMap<String, Object>());
        }
        return template;
    }

    private Object sanitizeBlueprint(Object rawBlueprint) {
        if (!(rawBlueprint instanceof Map)) {
            return rawBlueprint;
        }
        Map<String, Object> raw = objectMapper.convertValue(rawBlueprint, Map.class);
        Map<String, Object> sanitized = new HashMap<String, Object>();
        copyIfPresent(raw, sanitized, "goal");
        copyIfPresent(raw, sanitized, "summary_style");
        copyIfPresent(raw, sanitized, "summaryStyle");
        copyIfPresent(raw, sanitized, "conclusion_rules");
        copyIfPresent(raw, sanitized, "conclusionRules");
        Object sections = raw.get("sections");
        if (sections instanceof List) {
            List<Map<String, Object>> sanitizedSections = new ArrayList<Map<String, Object>>();
            for (Object item : (List<?>) sections) {
                if (!(item instanceof Map)) {
                    continue;
                }
                Map<String, Object> rawSection = objectMapper.convertValue(item, Map.class);
                Map<String, Object> sanitizedSection = new HashMap<String, Object>();
                copyIfPresent(rawSection, sanitizedSection, "section_id");
                copyIfPresent(rawSection, sanitizedSection, "sectionId");
                copyIfPresent(rawSection, sanitizedSection, "title");
                copyIfPresent(rawSection, sanitizedSection, "purpose");
                Object blocks = rawSection.get("blocks");
                if (blocks instanceof List) {
                    List<Map<String, Object>> sanitizedBlocks = new ArrayList<Map<String, Object>>();
                    for (Object blockItem : (List<?>) blocks) {
                        if (!(blockItem instanceof Map)) {
                            continue;
                        }
                        Map<String, Object> rawBlock = objectMapper.convertValue(blockItem, Map.class);
                        Map<String, Object> sanitizedBlock = new HashMap<String, Object>();
                        Object blockType = rawBlock.containsKey("block_type") ? rawBlock.get("block_type") : rawBlock.get("type");
                        if (blockType != null) {
                            sanitizedBlock.put("block_type", blockType);
                        }
                        if (rawBlock.get("instruction") != null) {
                            sanitizedBlock.put("instruction", rawBlock.get("instruction"));
                        }
                        if (rawBlock.get("source_refs") instanceof List) {
                            sanitizedBlock.put("source_refs", rawBlock.get("source_refs"));
                        }
                        sanitizedBlocks.add(sanitizedBlock);
                    }
                    sanitizedSection.put("blocks", sanitizedBlocks);
                }
                sanitizedSections.add(sanitizedSection);
            }
            sanitized.put("sections", sanitizedSections);
        }
        return sanitized;
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }

    private Object buildReportInputTemplate(String targetPath,
                                            Object rawValue,
                                            List<CompiledStepContext> priorSteps,
                                            ExecutableDslStep step) {
        ExecutableDslInputBinding directBinding = inferBinding(targetPath, rawValue, priorSteps);
        if (directBinding != null) {
            step.getInputBindings().add(directBinding);
            return null;
        }
        if (rawValue instanceof Map) {
            Map<String, Object> source = objectMapper.convertValue(rawValue, Map.class);
            if (isSourceDescriptor(source)) {
                return source;
            }
            Map<String, Object> nestedTemplate = new HashMap<String, Object>();
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                Object nestedValue = buildReportInputTemplate(targetPath + "." + entry.getKey(), entry.getValue(), priorSteps, step);
                if (nestedValue != null) {
                    nestedTemplate.put(entry.getKey(), nestedValue);
                }
            }
            return nestedTemplate;
        }
        return rawValue;
    }

    private boolean isSourceDescriptor(Map<String, Object> raw) {
        return raw.containsKey("tool") && raw.containsKey("field");
    }

    private ExecutableDslInputBinding inferBinding(String argKey, Object argValue, List<CompiledStepContext> priorSteps) {
        if (argValue == null || priorSteps.isEmpty()) {
            return null;
        }
        for (int i = priorSteps.size() - 1; i >= 0; i--) {
            CompiledStepContext priorStep = priorSteps.get(i);
            for (Map.Entry<String, Object> artifact : priorStep.artifacts.entrySet()) {
                if (artifact.getValue() == null) {
                    continue;
                }
                if (sameValue(argValue, artifact.getValue())) {
                    ExecutableDslInputBinding binding = new ExecutableDslInputBinding();
                    binding.setTargetArg(argKey);
                    binding.setFromStep(priorStep.stepId);
                    binding.setSourcePath("artifacts." + artifact.getKey());
                    return binding;
                }
            }
        }
        return null;
    }

    private boolean sameValue(Object left, Object right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (left instanceof Map || left instanceof List || right instanceof Map || right instanceof List) {
            try {
                return objectMapper.writeValueAsString(left).equals(objectMapper.writeValueAsString(right));
            } catch (Exception e) {
                return false;
            }
        }
        return String.valueOf(left).equals(String.valueOf(right));
    }

    private static final class CompiledStepContext {
        private final String stepId;
        private final Map<String, Object> artifacts;

        private CompiledStepContext(String stepId, Map<String, Object> artifacts) {
            this.stepId = stepId;
            this.artifacts = artifacts == null ? new HashMap<String, Object>() : artifacts;
        }
    }
}
