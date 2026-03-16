package com.example.agent.application.service;

import com.example.agent.domain.model.AgentSession;
import com.example.agent.domain.model.AgentSessionStatus;
import com.example.agent.domain.model.AgentTurn;
import com.example.agent.domain.model.AgentTurnStatus;
import com.example.agent.domain.model.ExecutableDslSnapshot;
import com.example.agent.domain.model.RuntimeEventType;
import com.example.agent.domain.model.SkillDefinition;
import com.example.agent.domain.model.ToolDefinition;
import com.example.agent.domain.model.ToolExecutionResult;
import com.example.agent.domain.model.ToolValidationException;
import com.example.agent.domain.model.chat.ChatMessage;
import com.example.agent.domain.model.chat.ChatRole;
import com.example.agent.domain.model.chat.ToolCall;
import com.example.agent.domain.port.LlmClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AgentTurnRunner {
    private static final int MAX_ROUNDS = 8;

    private final SkillSelector skillSelector;
    private final ToolRegistry toolRegistry;
    private final PromptAssembler promptAssembler;
    private final LlmClient llmClient;
    private final ToolExecutionPipeline toolExecutionPipeline;
    private final AgentSessionStore sessionStore;
    private final RuntimeEventBus runtimeEventBus;
    private final DslCompilerService dslCompilerService;
    private final ObjectMapper objectMapper;
    private final TaskExecutor taskExecutor;
    private final ContextBudgetService contextBudgetService;

    public AgentTurnRunner(SkillSelector skillSelector,
                           ToolRegistry toolRegistry,
                           PromptAssembler promptAssembler,
                           LlmClient llmClient,
                           ToolExecutionPipeline toolExecutionPipeline,
                           AgentSessionStore sessionStore,
                           RuntimeEventBus runtimeEventBus,
                           DslCompilerService dslCompilerService,
                           ObjectMapper objectMapper,
                           @Qualifier("agentTaskExecutor") TaskExecutor taskExecutor,
                           ContextBudgetService contextBudgetService) {
        this.skillSelector = skillSelector;
        this.toolRegistry = toolRegistry;
        this.promptAssembler = promptAssembler;
        this.llmClient = llmClient;
        this.toolExecutionPipeline = toolExecutionPipeline;
        this.sessionStore = sessionStore;
        this.runtimeEventBus = runtimeEventBus;
        this.dslCompilerService = dslCompilerService;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
        this.contextBudgetService = contextBudgetService;
    }

    public AgentSession startSession(String userMessage, Map<String, Object> globalContext) {
        AgentSession session = createSessionRecord(userMessage, globalContext);
        return runTurn(session, userMessage, false);
    }

    public AgentSession createSessionRecord(String userMessage, Map<String, Object> globalContext) {
        AgentSession session = new AgentSession();
        session.setSessionId("session-" + UUID.randomUUID());
        session.setTitle(userMessage);
        session.setQuery(userMessage);
        session.setGlobalContext(globalContext == null ? new HashMap<String, Object>() : new HashMap<String, Object>(globalContext));
        List<SkillDefinition> skills = skillSelector.select(userMessage, session.getGlobalContext());
        session.setActiveSkillIds(extractSkillIds(skills));
        session.setTranscript(promptAssembler.buildInitialTranscript(userMessage, session.getGlobalContext(), skills, allowedTools(skills)));
        sessionStore.save(session);
        runtimeEventBus.publish(session.getSessionId(), null, RuntimeEventType.SESSION_STARTED, payload("message", userMessage));
        return session;
    }

    public void startSessionAsync(String sessionId, String userMessage) {
        taskExecutor.execute(new Runnable() {
            @Override
            public void run() {
                AgentSession session = requireSession(sessionId);
                try {
                    runTurn(session, userMessage, false);
                } catch (Exception e) {
                    session.setStatus(AgentSessionStatus.FAILED);
                    session.setLastError(e.getMessage());
                    sessionStore.save(session);
                    runtimeEventBus.publish(
                            session.getSessionId(),
                            null,
                            RuntimeEventType.TURN_FINISHED,
                            payload("status", AgentSessionStatus.FAILED.name(), "error", e.getMessage())
                    );
                }
            }
        });
    }

    public AgentSession continueSession(String sessionId, String userMessage) {
        AgentSession session = requireSession(sessionId);
        ChatMessage user = new ChatMessage();
        user.setRole(ChatRole.user);
        user.setContent(userMessage);
        session.getTranscript().add(user);
        session.setQuery(userMessage);
        session.setUpdatedAt(java.time.Instant.now());
        session.setStatus(AgentSessionStatus.RUNNING);
        sessionStore.save(session);
        return runTurn(session, userMessage, false);
    }

    public AgentSession resumeSession(String sessionId) {
        AgentSession session = requireSession(sessionId);
        session.setStatus(AgentSessionStatus.RUNNING);
        session.setWaitingExternalJobId(null);
        sessionStore.save(session);
        return runTurn(session, session.getQuery(), true);
    }

    public AgentSession appendToolObservation(String sessionId, String toolCallId, Map<String, Object> payload) {
        AgentSession session = requireSession(sessionId);
        String toolName = "async_tool";
        boolean updatedTranscript = false;
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("failed to append tool observation", e);
        }
        for (com.example.agent.domain.model.AgentExecutedStep step : session.getExecutedSteps()) {
            if (toolCallId.equals(step.getToolCallId())) {
                toolName = step.getToolName();
                String callbackStatus = payload != null && payload.get("status") != null
                        ? String.valueOf(payload.get("status"))
                        : "SUCCEEDED";
                step.setStatus("FAILED".equalsIgnoreCase(callbackStatus) ? "FAILED" : "SUCCEEDED");
                if (payload != null && payload.get("artifacts") instanceof Map) {
                    step.setArtifacts(new HashMap<String, Object>((Map<String, Object>) payload.get("artifacts")));
                }
                if (payload != null && payload.get("data") != null) {
                    step.setOutputData(payload.get("data"));
                }
                step.setResultPayload(payload == null ? new HashMap<String, Object>() : new HashMap<String, Object>(payload));
                step.setResultSummary("FAILED".equalsIgnoreCase(callbackStatus) ? "async callback failed" : "async callback completed");
            }
        }
        for (ChatMessage message : session.getTranscript()) {
            if (message.getRole() == ChatRole.tool && toolCallId.equals(message.getToolCallId())) {
                message.setName(toolName);
                message.setContent(payloadJson);
                updatedTranscript = true;
                break;
            }
        }
        if (!updatedTranscript) {
            ChatMessage tool = new ChatMessage();
            tool.setRole(ChatRole.tool);
            tool.setName(toolName);
            tool.setToolCallId(toolCallId);
            tool.setContent(payloadJson);
            session.getTranscript().add(tool);
        }
        session.setWaitingExternalJobId(null);
        session.setUpdatedAt(java.time.Instant.now());
        sessionStore.save(session);
        return session;
    }

    private AgentSession runTurn(AgentSession session, String userMessage, boolean resumed) {
        AgentTurn turn = new AgentTurn();
        turn.setTurnId("turn-" + UUID.randomUUID());
        turn.setUserMessage(userMessage);
        session.setTurnCount(session.getTurnCount() + 1);
        runtimeEventBus.publish(session.getSessionId(), turn.getTurnId(), RuntimeEventType.TURN_STARTED, payload("message", userMessage, "resumed", resumed));

        List<SkillDefinition> skills = resolveSkills(session);
        List<ToolDefinition> allowedTools = allowedTools(skills);
        ensureSystemPrompt(session, skills, allowedTools);

        for (int round = 0; round < MAX_ROUNDS; round++) {
            List<ChatMessage> messagesForLlm = contextBudgetService.prepareMessagesForLlm(session, allowedTools);
            ChatMessage reply = llmClient.chat(messagesForLlm, allowedTools);
            runtimeEventBus.publish(session.getSessionId(), turn.getTurnId(), RuntimeEventType.MODEL_DECISION, payload("content", reply.getContent(), "toolCalls", reply.getToolCalls()));
            if (reply.getToolCalls() != null && !reply.getToolCalls().isEmpty()) {
                session.getTranscript().add(reply);
                boolean recoverableToolFailure = false;
                for (ToolCall toolCall : reply.getToolCalls()) {
                    runtimeEventBus.publish(session.getSessionId(), turn.getTurnId(), RuntimeEventType.TOOL_CALL_REQUESTED, payload("tool", toolCall.getFunction().getName(), "arguments", toolCall.getFunction().getArguments()));
                    Map<String, Object> args = parseArguments(toolCall.getFunction().getArguments());
                    try {
                        ToolExecutionResult result = toolExecutionPipeline.execute(session, turn.getTurnId(), toolCall.getId(), toolCall.getFunction().getName(), args, allowedTools);
                        session.getTranscript().add(toolMessage(toolCall.getFunction().getName(), toolCall.getId(), result.getNormalizedPayload()));
                        if (result.isWaitingAsync()) {
                            turn.setStatus(AgentTurnStatus.WAITING_ASYNC_CALLBACK);
                            session.setStatus(AgentSessionStatus.WAITING_ASYNC_CALLBACK);
                            sessionStore.save(session);
                            runtimeEventBus.publish(session.getSessionId(), turn.getTurnId(), RuntimeEventType.TURN_FINISHED, payload("status", turn.getStatus().name()));
                            return session;
                        }
                    } catch (ToolValidationException e) {
                        session.getTranscript().add(toolMessage(toolCall.getFunction().getName(), toolCall.getId(), e.toPayload()));
                        recoverableToolFailure = true;
                        break;
                    }
                }
                sessionStore.save(session);
                if (recoverableToolFailure) {
                    continue;
                }
                continue;
            }

            ChatMessage assistant = new ChatMessage();
            assistant.setRole(ChatRole.assistant);
            assistant.setContent(reply.getContent());
            session.getTranscript().add(assistant);
            session.setLatestAnswer(reply.getContent());
            session.setStatus(AgentSessionStatus.SUCCEEDED);
            turn.setStatus(AgentTurnStatus.SUCCEEDED);
            runtimeEventBus.publish(session.getSessionId(), turn.getTurnId(), RuntimeEventType.ASSISTANT_MESSAGE, payload("content", reply.getContent()));
            if (!session.getExecutedSteps().isEmpty()) {
                ExecutableDslSnapshot dsl = dslCompilerService.compile(session);
                session.setLatestDslId(dsl.getDslId());
                runtimeEventBus.publish(session.getSessionId(), turn.getTurnId(), RuntimeEventType.DSL_COMPILED, payload("dslId", dsl.getDslId(), "stepCount", dsl.getSteps().size()));
            }
            sessionStore.save(session);
            runtimeEventBus.publish(session.getSessionId(), turn.getTurnId(), RuntimeEventType.TURN_FINISHED, payload("status", turn.getStatus().name(), "dslId", session.getLatestDslId()));
            return session;
        }

        session.setStatus(AgentSessionStatus.FAILED);
        session.setLastError("max rounds exceeded");
        turn.setStatus(AgentTurnStatus.FAILED);
        sessionStore.save(session);
        runtimeEventBus.publish(session.getSessionId(), turn.getTurnId(), RuntimeEventType.TURN_FINISHED, payload("status", turn.getStatus().name(), "error", session.getLastError()));
        return session;
    }

    private void ensureSystemPrompt(AgentSession session, List<SkillDefinition> skills, List<ToolDefinition> allowedTools) {
        if (session.getTranscript().isEmpty()) {
            session.setTranscript(promptAssembler.buildInitialTranscript(session.getQuery(), session.getGlobalContext(), skills, allowedTools));
            return;
        }
        ChatMessage first = session.getTranscript().get(0);
        if (first.getRole() == ChatRole.system) {
            first.setContent(promptAssembler.systemPrompt(session.getGlobalContext(), skills, allowedTools));
        }
    }

    private Map<String, Object> parseArguments(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new HashMap<String, Object>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("failed to parse tool args", e);
        }
    }

    private AgentSession requireSession(String sessionId) {
        AgentSession session = sessionStore.load(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("session not found: " + sessionId);
        }
        return session;
    }

    private List<SkillDefinition> resolveSkills(AgentSession session) {
        List<SkillDefinition> selected = new ArrayList<SkillDefinition>();
        for (String skillId : session.getActiveSkillIds()) {
            skillSelector.select(session.getQuery(), session.getGlobalContext());
            skillRegistryLoop:
            for (SkillDefinition skill : skillSelector.select(session.getQuery(), session.getGlobalContext())) {
                if (skillId.equals(skill.getSkillId())) {
                    selected.add(skill);
                    break skillRegistryLoop;
                }
            }
        }
        if (selected.isEmpty()) {
            selected = skillSelector.select(session.getQuery(), session.getGlobalContext());
        }
        return selected;
    }

    private List<String> extractSkillIds(List<SkillDefinition> skills) {
        List<String> ids = new ArrayList<String>();
        for (SkillDefinition skill : skills) {
            ids.add(skill.getSkillId());
        }
        return ids;
    }

    private List<ToolDefinition> allowedTools(List<SkillDefinition> skills) {
        List<String> toolIds = new ArrayList<String>();
        for (SkillDefinition skill : skills) {
            for (String toolId : skill.getToolIds()) {
                if (!toolIds.contains(toolId)) {
                    toolIds.add(toolId);
                }
            }
        }
        return toolRegistry.findByIds(toolIds);
    }

    private Map<String, Object> payload(Object... items) {
        Map<String, Object> payload = new HashMap<String, Object>();
        for (int i = 0; i + 1 < items.length; i += 2) {
            payload.put(String.valueOf(items[i]), items[i + 1]);
        }
        return payload;
    }

    private ChatMessage toolMessage(String toolName, String toolCallId, Object payload) {
        ChatMessage toolMessage = new ChatMessage();
        toolMessage.setRole(ChatRole.tool);
        toolMessage.setName(toolName);
        toolMessage.setToolCallId(toolCallId);
        try {
            toolMessage.setContent(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            throw new RuntimeException("failed to serialize tool result", e);
        }
        return toolMessage;
    }
}
