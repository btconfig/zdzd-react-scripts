package com.example.agent.infrastructure.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.Map;

public interface DslInstanceMapper {
    int updateByDslId(Map<String, Object> params);

    int insert(Map<String, Object> params);

    Map<String, Object> findById(@Param("dslId") String dslId);
}
