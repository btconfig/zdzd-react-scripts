package com.example.agent.infrastructure.repository;

import com.example.agent.infrastructure.mapper.DslExecutionLogMapper;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

@Repository
public class DslExecutionLogRepository {
    private final DslExecutionLogMapper mapper;

    public DslExecutionLogRepository(DslExecutionLogMapper mapper) {
        this.mapper = mapper;
    }

    public void insert(String dslId, String executionId, String status, String outputJson) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("dslId", dslId);
        params.put("executionId", executionId);
        params.put("status", status);
        params.put("outputJson", outputJson);
        mapper.insert(params);
    }
}
