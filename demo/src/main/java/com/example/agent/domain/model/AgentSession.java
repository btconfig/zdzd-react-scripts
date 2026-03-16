package com.example.agent.domain.model;

import com.example.agent.domain.model.chat.ChatMessage;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class AgentSession {
    private String sessionId;
    private String title;
    private String query;
    private AgentSessionStatus status = AgentSessionStatus.RUNNING;
    private List<ChatMessage> transcript = new ArrayList<ChatMessage>();
    private Map<String, Object> globalContext = new HashMap<String, Object>();
    private List<String> activeSkillIds = new ArrayList<String>();
    private List<AgentExecutedStep> executedSteps = new ArrayList<AgentExecutedStep>();
    private String latestAnswer;
    private String latestDslId;
    private String waitingExternalJobId;
    private Integer turnCount = 0;
    private String lastError;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}
