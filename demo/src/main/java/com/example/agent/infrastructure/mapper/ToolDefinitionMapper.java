package com.example.agent.infrastructure.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface ToolDefinitionMapper {
    List<Map<String, Object>> findAllEnabled();

    List<Map<String, Object>> findAll();

    Map<String, Object> findById(@Param("toolId") String toolId);
}
