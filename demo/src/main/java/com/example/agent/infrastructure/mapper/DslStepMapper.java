package com.example.agent.infrastructure.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface DslStepMapper {
    int updateByDslStepId(Map<String, Object> params);

    int insert(Map<String, Object> params);

    List<Map<String, Object>> findByDslId(@Param("dslId") String dslId);
}
