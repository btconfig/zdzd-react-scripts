package com.example.agent.infrastructure.mapper;

import com.example.agent.infrastructure.mapper.model.AgentEventRow;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface AgentEventMapper {
    int insert(AgentEventRow row);

    List<Map<String, Object>> findBySessionId(@Param("sessionId") String sessionId);
}
