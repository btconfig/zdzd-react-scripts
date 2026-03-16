package com.example.agent.application.service;

import com.example.agent.domain.model.AgentExecutedStep;
import com.example.agent.domain.model.AgentSession;
import com.example.agent.domain.model.RuntimeEventType;
import com.example.agent.domain.model.ToolDefinition;
import com.example.agent.domain.model.ToolExecutionResult;
import com.example.agent.domain.model.ToolResult;
import com.example.agent.domain.model.ToolResultType;
import com.example.agent.domain.model.ToolValidationException;
import com.example.agent.domain.port.ToolExecutor;
import com.example.agent.infrastructure.repository.AsyncJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ToolExecutionPipeline {
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final RuntimeEventBus runtimeEventBus;
    private final AsyncJobRepository asyncJobRepository;
    private final ObjectMapper objectMapper;
    private final AgentTimeContextService agentTimeContextService;
    private final ToolArgumentResolverRegistry toolArgumentResolverRegistry;

    public ToolExecutionPipeline(ToolRegistry toolRegistry,
                                 ToolExecutor toolExecutor,
                                 RuntimeEventBus runtimeEventBus,
                                 AsyncJobRepository asyncJobRepository,
                                 ObjectMapper objectMapper,
                                 AgentTimeContextService agentTimeContextService,
                                 ToolArgumentResolverRegistry toolArgumentResolverRegistry) {
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.runtimeEventBus = runtimeEventBus;
        this.asyncJobRepository = asyncJobRepository;
        this.objectMapper = objectMapper;
        this.agentTimeContextService = agentTimeContextService;
        this.toolArgumentResolverRegistry = toolArgumentResolverRegistry;
    }

    public ToolExecutionResult execute(AgentSession session,
                                       String turnId,
                                       String toolCallId,
                                       String toolName,
                                       Map<String, Object> args,
                                       List<ToolDefinition> allowedTools) {
        ToolExecutionResult executionResult = new ToolExecutionResult();
        executionResult.setToolCallId(toolCallId);
        executionResult.setToolName(toolName);

        try {
            ToolDefinition definition = resolveAllowedTool(toolName, allowedTools);
            Map<String, Object> normalizedArgs = agentTimeContextService.normalizeArgs(session.getQuery(), definition, args);
            Map<String, Object> executorArgs = prepareExecutionArgs(session, definition, normalizedArgs);
            executionResult.setArgs(normalizedArgs);
            runtimeEventBus.publish(session.getSessionId(), turnId, RuntimeEventType.TOOL_EXECUTION_STARTED, payload(toolName, executorArgs, null));
            validateArgs(definition, normalizedArgs);
            com.example.agent.domain.model.ToolCommand command = new com.example.agent.domain.model.ToolCommand();
            command.setToolName(toolName);
            command.setArgs(executorArgs);
            command.setPlanInstanceId(session.getSessionId());
            command.setTurnId(turnId);
            command.setNodeId(toolCallId);
            command.setIdempotencyKey(session.getSessionId() + ":" + toolCallId);
            ToolResult toolResult = toolExecutor.execute(command);
            executionResult.setToolResult(toolResult);
            executionResult.setSuccess(true);
            executionResult.setWaitingAsync(toolResult != null && toolResult.getType() == ToolResultType.ASYNC_JOB);
            executionResult.setSummary(summarize(toolResult));
            executionResult.setNormalizedPayload(normalize(toolResult));

            AgentExecutedStep executedStep = new AgentExecutedStep();
            executedStep.setTurnId(turnId);
            executedStep.setStepId("step-" + (session.getExecutedSteps().size() + 1));
            executedStep.setToolCallId(toolCallId);
            executedStep.setToolName(toolName);
            executedStep.setArgs(normalizedArgs);
            executedStep.setResultType(toolResult == null ? ToolResultType.INLINE_DATA : toolResult.getType());
            executedStep.setAsyncJobId(toolResult == null ? null : toolResult.getAsyncJobId());
            executedStep.setStatus(executionResult.isWaitingAsync() ? "WAITING_ASYNC_CALLBACK" : "SUCCEEDED");
            executedStep.setResultSummary(executionResult.getSummary());
            executedStep.setOutputData(toolResult == null ? null : toolResult.getInlineData());
            executedStep.setResultPayload(executionResult.getNormalizedPayload());
            executedStep.setArtifacts(toolResult == null || toolResult.getArtifacts() == null ? new HashMap<String, Object>() : toolResult.getArtifacts());
            session.getExecutedSteps().add(executedStep);

            if (executionResult.isWaitingAsync()) {
                String payloadJson = objectMapper.writeValueAsString(executionResult.getNormalizedPayload());
                asyncJobRepository.insertPendingAgentJob(
                        session.getSessionId(),
                        turnId,
                        toolCallId,
                        toolResult.getAsyncJobId(),
                        toolResult.getCallbackTopic(),
                        toolResult.getCallbackUrl(),
                        payloadJson
                );
                session.setWaitingExternalJobId(toolResult.getAsyncJobId());
                runtimeEventBus.publish(session.getSessionId(), turnId, RuntimeEventType.WAITING_ASYNC_CALLBACK, payload(toolName, normalizedArgs, executionResult.getNormalizedPayload()));
            } else {
                runtimeEventBus.publish(session.getSessionId(), turnId, RuntimeEventType.TOOL_EXECUTION_FINISHED, payload(toolName, normalizedArgs, executionResult.getNormalizedPayload()));
            }
            return executionResult;
        } catch (ToolValidationException e) {
            executionResult.setSuccess(false);
            executionResult.setError(e.getMessage());
            executionResult.setNormalizedPayload(e.toPayload());
            Map<String, Object> failedArgs = executionResult.getArgs() == null ? new LinkedHashMap<String, Object>() : executionResult.getArgs();
            recordFailedStep(session, turnId, toolCallId, toolName, failedArgs, e.getMessage(), e.toPayload());
            runtimeEventBus.publish(session.getSessionId(), turnId, RuntimeEventType.TOOL_EXECUTION_FAILED, payload(toolName, failedArgs, e.toPayload()));
            throw e;
        } catch (Exception e) {
            executionResult.setSuccess(false);
            executionResult.setError(e.getMessage());
            Map<String, Object> failurePayload = executionFailurePayload(
                    "TOOL_EXECUTION_FAILED",
                    e.getMessage(),
                    "Check tool args and upstream tool availability before retrying.",
                    false
            );
            Map<String, Object> failedArgs = executionResult.getArgs() == null ? new LinkedHashMap<String, Object>() : executionResult.getArgs();
            recordFailedStep(session, turnId, toolCallId, toolName, failedArgs, e.getMessage(), failurePayload);
            runtimeEventBus.publish(session.getSessionId(), turnId, RuntimeEventType.TOOL_EXECUTION_FAILED, payload(toolName, failedArgs, failurePayload));
            throw new RuntimeException("tool execution failed for " + toolName + ": " + e.getMessage(), e);
        }
    }

    private ToolDefinition resolveAllowedTool(String toolName, List<ToolDefinition> allowedTools) {
        for (ToolDefinition definition : allowedTools) {
            if (definition != null && toolName.equals(definition.getToolId())) {
                return definition;
            }
        }
        throw new IllegalArgumentException("tool not allowed: " + toolName);
    }

    private void validateArgs(ToolDefinition definition, Map<String, Object> args) {
        Object required = definition.getInputSchema().get("required");
        if (!(required instanceof String[])) {
            return;
        }
        for (String key : (String[]) required) {
            if (!args.containsKey(key) || args.get(key) == null) {
                throw validation(
                        "MISSING_REQUIRED_ARG",
                        "Missing required arg '" + key + "' for tool '" + definition.getToolId() + "'.",
                        "Rebuild the tool call with all required arguments before retrying."
                );
            }
        }
    }

    private Map<String, Object> normalize(ToolResult toolResult) {
        Map<String, Object> payload = new HashMap<String, Object>();
        if (toolResult == null) {
            return payload;
        }
        payload.put("type", toolResult.getType());
        payload.put("data", toolResult.getInlineData());
        payload.put("inlineData", toolResult.getInlineData());
        payload.put("handleId", toolResult.getHandleId());
        payload.put("asyncJobId", toolResult.getAsyncJobId());
        payload.put("artifacts", toolResult.getArtifacts());
        return payload;
    }

    private String summarize(ToolResult toolResult) {
        if (toolResult == null) {
            return "null tool result";
        }
        if (toolResult.getType() == ToolResultType.ASYNC_JOB) {
            return "async job created: " + toolResult.getAsyncJobId();
        }
        if (toolResult.getType() == ToolResultType.HANDLE) {
            return "handle created: " + toolResult.getHandleId();
        }
        Object inlineData = toolResult.getInlineData();
        if (inlineData instanceof List) {
            return "returned rows: " + ((List<?>) inlineData).size();
        }
        return String.valueOf(inlineData);
    }

    private Map<String, Object> payload(String toolName, Map<String, Object> args, Object result) {
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("tool", toolName);
        payload.put("args", args);
        payload.put("result", result);
        return payload;
    }

    private Map<String, Object> prepareExecutionArgs(AgentSession session, ToolDefinition definition, Map<String, Object> args) {
        ToolArgumentResolver resolver = toolArgumentResolverRegistry.get(definition.getRequestPreprocessor());
        return resolver.resolve(session, definition, args);
    }

    private ToolValidationException validation(String code, String message, String hint) {
        return new ToolValidationException(code, message, hint);
    }

    private Map<String, Object> executionFailurePayload(String code, String message, String hint, boolean retryable) {
        return new ToolValidationException(code, message, hint, retryable, new LinkedHashMap<String, Object>()).toPayload();
    }

    private void recordFailedStep(AgentSession session,
                                  String turnId,
                                  String toolCallId,
                                  String toolName,
                                  Map<String, Object> args,
                                  String summary,
                                  Map<String, Object> payload) {
        AgentExecutedStep executedStep = new AgentExecutedStep();
        executedStep.setTurnId(turnId);
        executedStep.setStepId("step-" + (session.getExecutedSteps().size() + 1));
        executedStep.setToolCallId(toolCallId);
        executedStep.setToolName(toolName);
        executedStep.setArgs(args);
        executedStep.setResultType(ToolResultType.INLINE_DATA);
        executedStep.setStatus("FAILED");
        executedStep.setResultSummary(summary);
        executedStep.setResultPayload(payload);
        executedStep.setArtifacts(new HashMap<String, Object>());
        session.getExecutedSteps().add(executedStep);
    }

}
