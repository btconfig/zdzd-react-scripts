package com.example.agent.infrastructure.repository;

import com.example.agent.infrastructure.mapper.AgentSessionMapper;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

@Repository
public class AgentSessionRepository {
    private final AgentSessionMapper mapper;

    public AgentSessionRepository(AgentSessionMapper mapper) {
        this.mapper = mapper;
    }

    public void upsert(String sessionId,
                       String status,
                       String title,
                       String query,
                       String transcriptJson,
                       String globalContextJson,
                       String activeSkillIdsJson,
                       String executedStepsJson,
                       String latestAnswer,
                       String latestDslId,
                       String waitingExternalJobId,
                       Integer turnCount,
                       String lastError) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("sessionId", sessionId);
        params.put("status", status);
        params.put("title", title);
        params.put("query", query);
        params.put("transcriptJson", transcriptJson);
        params.put("globalContextJson", globalContextJson);
        params.put("activeSkillIdsJson", activeSkillIdsJson);
        params.put("executedStepsJson", executedStepsJson);
        params.put("latestAnswer", latestAnswer);
        params.put("latestDslId", latestDslId);
        params.put("waitingExternalJobId", waitingExternalJobId);
        params.put("turnCount", turnCount);
        params.put("lastError", lastError);
        int updated = mapper.updateBySessionId(params);
        if (updated == 0) {
            mapper.insert(params);
        }
    }

    public Map<String, Object> findById(String sessionId) {
        return mapper.findById(sessionId);
    }
}
