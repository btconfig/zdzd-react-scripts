package com.example.agent.infrastructure.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.Map;

public interface AgentSessionMapper {
    int updateBySessionId(Map<String, Object> params);

    int insert(Map<String, Object> params);

    Map<String, Object> findById(@Param("sessionId") String sessionId);
}
