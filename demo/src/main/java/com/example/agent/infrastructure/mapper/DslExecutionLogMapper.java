package com.example.agent.infrastructure.mapper;

import java.util.Map;

public interface DslExecutionLogMapper {
    int insert(Map<String, Object> params);
}
