package com.example.agent.infrastructure.repository;

import com.example.agent.infrastructure.mapper.DslInstanceMapper;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

@Repository
public class DslInstanceRepository {
    private final DslInstanceMapper mapper;

    public DslInstanceRepository(DslInstanceMapper mapper) {
        this.mapper = mapper;
    }

    public void upsert(String dslId, String sessionId, String title, String status, String summary, String outputJson) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("dslId", dslId);
        params.put("sessionId", sessionId);
        params.put("title", title);
        params.put("status", status);
        params.put("summary", summary);
        params.put("outputJson", outputJson);
        int updated = mapper.updateByDslId(params);
        if (updated == 0) {
            mapper.insert(params);
        }
    }

    public Map<String, Object> findById(String dslId) {
        return mapper.findById(dslId);
    }
}
